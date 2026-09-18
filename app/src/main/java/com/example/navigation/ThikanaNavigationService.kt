package com.example.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Robust Background Navigation & Real-Time Location Sharing Foreground Service.
 *
 * Guarantees that turn-by-turn guidance and group location sharing (trip presence)
 * remain 100% active, reliable, and responsive when:
 * 1. The screen is turned OFF (locked in pocket or on a bike/car mount)
 * 2. The user switches to another app
 * 3. Android Doze / CPU idle triggers (via PARTIAL_WAKE_LOCK & FOREGROUND_SERVICE_LOCATION)
 */
class ThikanaNavigationService : Service() {

    companion object {
        private const val TAG = "ThikanaNavService"
        const val CHANNEL_ID = "thikana_navigation_channel"
        const val NOTIFICATION_ID = 2027

        const val ACTION_START_NAV = "com.example.navigation.ACTION_START_NAV"
        const val ACTION_STOP_NAV = "com.example.navigation.ACTION_STOP_NAV"
        const val ACTION_UPDATE_GUIDANCE = "com.example.navigation.ACTION_UPDATE_GUIDANCE"
        const val ACTION_TOGGLE_MUTE = "com.example.navigation.ACTION_TOGGLE_MUTE"

        const val EXTRA_DEST_NAME = "extra_dest_name"
        const val EXTRA_DEST_LAT = "extra_dest_lat"
        const val EXTRA_DEST_LNG = "extra_dest_lng"
        const val EXTRA_TRIP_PLAN_ID = "extra_trip_plan_id"
        const val EXTRA_STEPS_JSON = "extra_steps_json"
        const val EXTRA_CURRENT_INSTR = "extra_current_instr"
        const val EXTRA_DISTANCE = "extra_distance"
        const val EXTRA_NEXT_INSTR = "extra_next_instr"

        // Global state observable by MainActivity and UI
        @Volatile
        var isNavigating = false
            private set

        @Volatile
        var activeDestinationName = ""
            private set

        @Volatile
        var activeTripPlanId = ""
            private set

        @Volatile
        var lastKnownLatitude = 0.0
            private set

        @Volatile
        var lastKnownLongitude = 0.0
            private set

        @Volatile
        var isMuted = false
            private set

        // Dispatches navigation events (e.g. user pressed stop on notification) back to MainActivity
        var navigationEventListener: ((action: String, data: Map<String, Any>) -> Unit)? = null

        fun startNavigation(
            context: Context,
            destinationName: String,
            destLat: Double,
            destLng: Double,
            tripPlanId: String = "",
            stepsJson: String = ""
        ) {
            val intent = Intent(context, ThikanaNavigationService::class.java).apply {
                action = ACTION_START_NAV
                putExtra(EXTRA_DEST_NAME, destinationName)
                putExtra(EXTRA_DEST_LAT, destLat)
                putExtra(EXTRA_DEST_LNG, destLng)
                putExtra(EXTRA_TRIP_PLAN_ID, tripPlanId)
                putExtra(EXTRA_STEPS_JSON, stepsJson)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateGuidance(
            context: Context,
            instruction: String,
            distance: String,
            nextInstruction: String = ""
        ) {
            if (!isNavigating) return
            val intent = Intent(context, ThikanaNavigationService::class.java).apply {
                action = ACTION_UPDATE_GUIDANCE
                putExtra(EXTRA_CURRENT_INSTR, instruction)
                putExtra(EXTRA_DISTANCE, distance)
                putExtra(EXTRA_NEXT_INSTR, nextInstruction)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun stopNavigation(context: Context) {
            val intent = Intent(context, ThikanaNavigationService::class.java).apply {
                action = ACTION_STOP_NAV
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    data class NavStep(
        val maneuverLat: Double,
        val maneuverLng: Double,
        val instruction: String,
        val distanceMeters: Double
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var wakeLock: PowerManager.WakeLock? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private var destinationLat = 0.0
    private var destinationLng = 0.0
    private var currentInstruction = "Continue on route"
    private var currentDistance = ""
    private var nextInstruction = ""

    private val parsedSteps = mutableListOf<NavStep>()
    private var currentStepIdx = 0
    private var lastSpokenInstruction = ""
    private var lastPresencePingTimestamp = 0L

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleLocationUpdate(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        acquireWakeLock()
        initTts()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "thikana:navigation_lock")?.apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // 4 hours maximum safety timeout
            }
            Log.i(TAG, "Navigation Partial WakeLock acquired for screen-off execution")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "Navigation Partial WakeLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        }
    }

    private fun initTts() {
        try {
            textToSpeech = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val tts = textToSpeech ?: return@TextToSpeech
                    val indianEnglish = Locale("en", "IN")
                    val hindi = Locale("hi", "IN")

                    val result = tts.setLanguage(indianEnglish)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        val hiResult = tts.setLanguage(hindi)
                        if (hiResult == TextToSpeech.LANG_MISSING_DATA || hiResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts.language = Locale.getDefault()
                        }
                    }
                    tts.setSpeechRate(0.98f)
                    tts.setPitch(1.0f)
                    isTtsReady = true
                    Log.i(TAG, "Background Navigation TTS initialized successfully")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS init error: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_NAV

        when (action) {
            ACTION_START_NAV -> {
                val dest = intent?.getStringExtra(EXTRA_DEST_NAME)?.takeIf { it.isNotBlank() } ?: "Destination"
                destinationLat = intent?.getDoubleExtra(EXTRA_DEST_LAT, 0.0) ?: 0.0
                destinationLng = intent?.getDoubleExtra(EXTRA_DEST_LNG, 0.0) ?: 0.0
                val tripPlanId = intent?.getStringExtra(EXTRA_TRIP_PLAN_ID) ?: ""
                val stepsJson = intent?.getStringExtra(EXTRA_STEPS_JSON) ?: ""

                activeDestinationName = dest
                activeTripPlanId = tripPlanId
                isNavigating = true

                parseSteps(stepsJson)

                // Start Foreground with Location Type
                val initialNotification = buildNotification(
                    title = "Navigating to $dest",
                    content = if (currentDistance.isNotBlank()) "$currentDistance • $currentInstruction" else currentInstruction
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        initialNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, initialNotification)
                }

                startLocationUpdates()
                speak("Starting navigation to $dest")
            }

            ACTION_UPDATE_GUIDANCE -> {
                val instr = intent?.getStringExtra(EXTRA_CURRENT_INSTR)
                val dist = intent?.getStringExtra(EXTRA_DISTANCE)
                val next = intent?.getStringExtra(EXTRA_NEXT_INSTR)

                if (!instr.isNullOrBlank()) currentInstruction = instr
                if (!dist.isNullOrBlank()) currentDistance = dist
                if (!next.isNullOrBlank()) nextInstruction = next

                updateNotification()
            }

            ACTION_TOGGLE_MUTE -> {
                isMuted = !isMuted
                if (!isMuted) {
                    speak("Voice guidance unmuted")
                }
                updateNotification()
            }

            ACTION_STOP_NAV -> {
                stopSelfAndCleanup()
            }
        }

        return START_STICKY
    }

    private fun parseSteps(stepsJson: String) {
        parsedSteps.clear()
        currentStepIdx = 0
        if (stepsJson.isBlank()) return

        try {
            val array = JSONArray(stepsJson)
            for (i in 0 until array.length()) {
                val stepObj = array.optJSONObject(i) ?: continue
                val dist = stepObj.optDouble("distance", 0.0)
                val maneuver = stepObj.optJSONObject("maneuver")
                val locArr = maneuver?.optJSONArray("location")
                val instr = stepObj.optString("instruction", maneuver?.optString("instruction", "") ?: "")

                if (locArr != null && locArr.length() >= 2) {
                    val lng = locArr.optDouble(0)
                    val lat = locArr.optDouble(1)
                    parsedSteps.add(
                        NavStep(
                            maneuverLat = lat,
                            maneuverLng = lng,
                            instruction = instr.ifBlank { "Turn" },
                            distanceMeters = dist
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing navigation steps: ${e.message}")
        }
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(1.0f)
                .setWaitForAccurateLocation(false)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.i(TAG, "High-accuracy background GPS updates started for screen-off tracking")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates", e)
        }
    }

    private fun handleLocationUpdate(loc: Location) {
        lastKnownLatitude = loc.latitude
        lastKnownLongitude = loc.longitude

        // 1. Send live coordinates to web application so the map stays in sync
        MainActivity.currentInstance?.onBackgroundLocationReceived(loc)

        // 2. Continuous Location Sharing (Trip Presence) in background
        if (activeTripPlanId.isNotBlank()) {
            val now = System.currentTimeMillis()
            if (now - lastPresencePingTimestamp >= 3500L) {
                lastPresencePingTimestamp = now
                sendBackgroundTripPresence(activeTripPlanId, loc.latitude, loc.longitude)
            }
        }

        // 3. Evaluate Turn-by-Turn guidance when screen is off
        evaluateTurnGuidance(loc)
    }

    private fun evaluateTurnGuidance(loc: Location) {
        if (destinationLat != 0.0 && destinationLng != 0.0) {
            val distToDestMeters = haversineMeters(loc.latitude, loc.longitude, destinationLat, destinationLng)
            if (distToDestMeters <= 35.0) {
                val arrivalText = "Aap destination pahunch gaye! Welcome to $activeDestinationName."
                if (lastSpokenInstruction != arrivalText) {
                    lastSpokenInstruction = arrivalText
                    speak(arrivalText)
                    vibrateTurn()
                    currentInstruction = "You have arrived!"
                    currentDistance = "0 m"
                    updateNotification()
                }
                return
            }
        }

        // Check maneuver steps
        if (parsedSteps.isNotEmpty() && currentStepIdx < parsedSteps.size) {
            val step = parsedSteps[currentStepIdx]
            val distToManeuver = haversineMeters(loc.latitude, loc.longitude, step.maneuverLat, step.maneuverLng)

            if (distToManeuver <= 40.0) {
                // Advance step
                currentStepIdx++
                if (currentStepIdx < parsedSteps.size) {
                    val nextStep = parsedSteps[currentStepIdx]
                    val spoken = nextStep.instruction
                    if (spoken.isNotBlank() && spoken != lastSpokenInstruction) {
                        lastSpokenInstruction = spoken
                        speak(spoken)
                        vibrateTurn()
                        currentInstruction = spoken
                        currentDistance = "${distToManeuver.toInt()} m"
                        updateNotification()
                    }
                }
            } else {
                currentDistance = if (distToManeuver < 1000) {
                    "${distToManeuver.toInt()} m"
                } else {
                    String.format(Locale.US, "%.1f km", distToManeuver / 1000.0)
                }
                updateNotification()
            }
        }
    }

    private fun sendBackgroundTripPresence(tripPlanId: String, lat: Double, lng: Double) {
        serviceScope.launch {
            try {
                val url = "${MainActivity.APP_URL}/api/trip-presence"
                val json = JSONObject().apply {
                    put("trip_plan_id", tripPlanId)
                    put("lat", lat)
                    put("lng", lng)
                }
                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val cookies = try {
                    CookieManager.getInstance().getCookie(MainActivity.APP_URL) ?: ""
                } catch (_: Exception) {
                    ""
                }

                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("User-Agent", "MeraThikaana-Android/1.0 (ScreenOffSafe)")
                    .header("Accept", "application/json")

                if (cookies.isNotBlank()) {
                    reqBuilder.header("Cookie", cookies)
                }

                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.d(TAG, "Trip presence background ping returned ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Trip presence background ping error: ${e.message}")
            }
        }
    }

    private fun speak(text: String) {
        if (isMuted || !isTtsReady) return
        try {
            textToSpeech?.stop()
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_turn_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak error: ${e.message}")
        }
    }

    private fun vibrateTurn() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 180, 100, 180),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 180, 100, 180), -1)
            }
        } catch (_: Exception) {}
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ThikanaNavigationService::class.java).apply {
            action = ACTION_STOP_NAV
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, ThikanaNavigationService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePendingIntent = PendingIntent.getService(
            this,
            2,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteActionTitle = if (isMuted) "Unmute Voice" else "Mute Voice"
        val muteActionIcon = if (isMuted) R.drawable.ic_nav_unmute else R.drawable.ic_nav_mute

        val subText = if (activeTripPlanId.isNotBlank()) {
            "Screen Off Safe • Live Sharing Active"
        } else {
            "Screen Off Safe • Turn Guidance Active"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_turn)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(subText)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_nav_stop, "Stop", stopPendingIntent)
            .addAction(muteActionIcon, muteActionTitle, mutePendingIntent)
            .build()
    }

    private fun updateNotification() {
        val notification = buildNotification(
            title = "Navigating to $activeDestinationName",
            content = if (currentDistance.isNotBlank()) "$currentDistance • $currentInstruction" else currentInstruction
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, notification)
    }

    private fun stopSelfAndCleanup() {
        isNavigating = false
        activeDestinationName = ""
        val tripIdToDelete = activeTripPlanId
        activeTripPlanId = ""

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}

        releaseWakeLock()

        // Clean up presence server-side if trip was active
        if (tripIdToDelete.isNotBlank()) {
            serviceScope.launch {
                try {
                    val url = "${MainActivity.APP_URL}/api/trip-presence?trip_plan_id=$tripIdToDelete"
                    val cookies = try {
                        CookieManager.getInstance().getCookie(MainActivity.APP_URL) ?: ""
                    } catch (_: Exception) { "" }

                    val reqBuilder = Request.Builder().url(url).delete()
                    if (cookies.isNotBlank()) reqBuilder.header("Cookie", cookies)
                    httpClient.newCall(reqBuilder.build()).execute().close()
                } catch (_: Exception) {}
            }
        }

        // Notify MainActivity to close web navigation modal if open
        navigationEventListener?.invoke("stop_navigation", emptyMap())

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Turn-by-Turn Navigation",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ongoing turn directions and live location sharing alerts while navigating with screen off"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 80, 150)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isNavigating = false
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        releaseWakeLock()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        serviceScope.cancel()
    }
}
