package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class NavSensorData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f, // GPS course over ground
    val compassHeading: Float = 0f, // Hardware sensor orientation (0-360 deg)
    val hasLocation: Boolean = false
)

class NavigationSensorBridge(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "NavSensorBridge"
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _sensorData = MutableStateFlow(NavSensorData())
    val sensorData: StateFlow<NavSensorData> = _sensorData.asStateFlow()

    private var isLocationTracking = false
    private var isSensorTracking = false
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var smoothedHeading = 0f

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            updateFromLocation(location)
        }
    }

    private fun updateFromLocation(location: android.location.Location) {
        val current = _sensorData.value
        _sensorData.value = current.copy(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracy,
            speed = location.speed,
            bearing = location.bearing,
            hasLocation = true
        )
    }

    fun startTracking() {
        startLocationUpdates()
        startSensorUpdates()
    }

    fun startLocationUpdates() {
        if (isLocationTracking) return

        // Fetch last known location immediately so map / near-me features don't wait for GPS cold start
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && !_sensorData.value.hasLocation) {
                    updateFromLocation(location)
                    Log.d(TAG, "Initialized with last known location: ${location.latitude}, ${location.longitude}")
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted for initial lastLocation query")
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch lastLocation", e)
        }

        // High-frequency fused GPS (up to 500ms when moving)
        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                500L
            ).setMinUpdateIntervalMillis(250L)
             .setMinUpdateDistanceMeters(0.5f)
             .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isLocationTracking = true
            Log.d(TAG, "Fused location updates started")
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted yet for high-frequency GPS")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates", e)
        }
    }

    fun startSensorUpdates() {
        if (isSensorTracking) return
        rotationVectorSensor?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI // ~60Hz
            )
            isSensorTracking = true
            Log.d(TAG, "Rotation vector sensor registered")
        }
    }

    fun stopTracking() {
        if (isLocationTracking) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing location updates", e)
            }
            isLocationTracking = false
        }
        if (isSensorTracking) {
            sensorManager.unregisterListener(this)
            isSensorTracking = false
        }
        Log.d(TAG, "Tracking stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            // azimuth in radians -> degrees (-180 to +180 -> 0 to 360)
            val azimuthDeg = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360) % 360).toFloat()
            
            // Low-pass exponential smoothing for steady, non-jittery compass
            val diff = (azimuthDeg - smoothedHeading + 540) % 360 - 180
            smoothedHeading = (smoothedHeading + diff * 0.22f + 360) % 360

            val roundedHeading = (smoothedHeading * 10).roundToInt() / 10f
            val current = _sensorData.value
            if (kotlin.math.abs(current.compassHeading - roundedHeading) >= 0.05f) {
                _sensorData.value = current.copy(compassHeading = roundedHeading)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
