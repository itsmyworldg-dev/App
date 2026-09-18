package com.example.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.example.sensor.NavigationSensorBridge
import com.example.voice.NavigationVoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ThikanaNativeBridge(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sensorBridge: NavigationSensorBridge,
    private val navVoiceManager: NavigationVoiceManager,
    private val onTabChangedFromWeb: (String) -> Unit,
    private val onUploadChooserStateChanged: ((Boolean) -> Unit)? = null,
    private val onLocationActionRequested: ((String) -> Unit)? = null,
    private val onOpenCameraRequested: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "ThikanaNativeBridge"
    }

    /**
     * Launch native Instagram-style camera and story/post image editor
     */
    @JavascriptInterface
    fun openNativeCamera(mode: String = "story") {
        scope.launch(Dispatchers.Main) {
            onOpenCameraRequested?.invoke(mode)
        }
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Provide native sensor and GPS state to the web application at high frequency.
     * Returns JSON string with lat, lng, speed, bearing, compassHeading, accuracy.
     */
    @JavascriptInterface
    fun getSmoothSensorData(): String {
        val data = sensorBridge.sensorData.value
        return """{"lat":${data.latitude},"lng":${data.longitude},"speed":${data.speed},"bearing":${data.bearing},"heading":${data.compassHeading},"accuracy":${data.accuracy},"hasLocation":${data.hasLocation}}"""
    }

    /**
     * Haptic feedback when tapping posts, likes, pins, or tabs
     */
    @JavascriptInterface
    fun performHapticFeedback(type: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
                val effect = when (type.lowercase()) {
                    "light", "tab", "tap" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    "medium", "like" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    "heavy" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                }
                vibrator.vibrate(effect)
            }
        } catch (_: Exception) {}
    }

    /**
     * One-tap launch into Google Maps turn-by-turn navigation (e.g. driving/walking)
     */
    @JavascriptInterface
    fun launchGoogleMapsNavigation(lat: Double, lng: Double, label: String = "") {
        scope.launch(Dispatchers.Main) {
            try {
                // Uri: google.navigation:q=latitude,longitude
                val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                if (mapIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(mapIntent)
                } else {
                    // Fallback to web browser maps
                    val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
                    context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open Google Maps: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Called by web JavaScript when upload chooser overlay is opened or closed
     */
    @JavascriptInterface
    fun onUploadChooserVisibilityChanged(isOpen: Boolean) {
        scope.launch(Dispatchers.Main) {
            onUploadChooserStateChanged?.invoke(isOpen)
        }
    }

    /**
     * Called by web JavaScript when a page view change occurs (discover, feed, messages, profile)
     * Keeps native Compose bottom bar in sync!
     */
    @JavascriptInterface
    fun onWebViewChanged(viewName: String) {
        scope.launch(Dispatchers.Main) {
            onTabChangedFromWeb(viewName)
        }
    }

    /**
     * Turn-by-Turn Voice Navigation: Speaks guidance out loud using Android's native TTS
     * (e.g. "Ab turn left onto Main Road", "In 200 meters, turn right", "Seedha continue").
     */
    @JavascriptInterface
    fun speakTurnByTurn(instruction: String, distance: String = "") {
        scope.launch(Dispatchers.Main) {
            navVoiceManager.speakNavInstruction(instruction, distance)
        }
    }

    /**
     * Updates real-time navigation status from the active turn-by-turn navigation overlay.
     */
    @JavascriptInterface
    fun updateNavStatus(
        isActive: Boolean,
        destinationName: String,
        currentInstruction: String,
        distance: String,
        nextInstruction: String
    ) {
        scope.launch(Dispatchers.Main) {
            navVoiceManager.updateNavState(
                isActive = isActive,
                destinationName = destinationName,
                currentInstruction = currentInstruction,
                distance = distance,
                nextInstruction = nextInstruction
            )
            if (isActive) {
                com.example.navigation.ThikanaNavigationService.updateGuidance(
                    context = context,
                    instruction = currentInstruction,
                    distance = distance,
                    nextInstruction = nextInstruction
                )
            } else if (com.example.navigation.ThikanaNavigationService.isNavigating) {
                com.example.navigation.ThikanaNavigationService.stopNavigation(context)
            }
        }
    }

    /**
     * Explicitly starts native Android foreground service for screen-off turn-by-turn navigation
     * and real-time group location sharing (trip presence).
     */
    @JavascriptInterface
    fun startBackgroundNavigation(
        destinationName: String,
        destLat: Double,
        destLng: Double,
        tripPlanId: String = "",
        stepsJson: String = ""
    ) {
        scope.launch(Dispatchers.Main) {
            com.example.navigation.ThikanaNavigationService.startNavigation(
                context = context,
                destinationName = destinationName,
                destLat = destLat,
                destLng = destLng,
                tripPlanId = tripPlanId,
                stepsJson = stepsJson
            )
        }
    }

    /**
     * Stops background navigation service when navigation ends or user exits.
     */
    @JavascriptInterface
    fun stopBackgroundNavigation() {
        scope.launch(Dispatchers.Main) {
            com.example.navigation.ThikanaNavigationService.stopNavigation(context)
        }
    }

    /**
     * Confirms that background navigation and screen-off location sharing are natively supported.
     */
    @JavascriptInterface
    fun isBackgroundNavSupported(): Boolean = true

    /**
     * Stops current navigation speech immediately.
     */
    @JavascriptInterface
    fun stopSpeakingNav() {
        scope.launch(Dispatchers.Main) {
            navVoiceManager.stopSpeaking()
        }
    }

    /**
     * Checks if navigation voice is currently muted.
     */
    @JavascriptInterface
    fun isNavMuted(): Boolean {
        return navVoiceManager.isMuted.value
    }

    /**
     * Toggles navigation voice mute state.
     */
    @JavascriptInterface
    fun toggleNavMute(): Boolean {
        return navVoiceManager.toggleMute()
    }

    /**
     * Re-speaks the current turn-by-turn instruction out loud on request.
     */
    @JavascriptInterface
    fun repeatNavInstruction() {
        scope.launch(Dispatchers.Main) {
            navVoiceManager.repeatCurrentInstruction()
        }
    }

    /**
     * Called by JavaScript when the user taps 'My Location' or 'Navigate' / 'Directions',
     * or when web geolocation triggers.
     * Native Android inspects if device location or permission is off and shows the turn-on popup.
     */
    @JavascriptInterface
    fun onLocationActionClicked(actionType: String = "location_or_nav") {
        scope.launch(Dispatchers.Main) {
            onLocationActionRequested?.invoke(actionType)
        }
    }

    /**
     * Checks if device system location (GPS) is currently ON.
     */
    @JavascriptInterface
    fun isLocationEnabled(): Boolean {
        return com.example.util.LocationHelper.isLocationServicesEnabled(context)
    }

    /**
     * Checks if runtime location permission is granted.
     */
    @JavascriptInterface
    fun hasLocationPermission(): Boolean {
        return com.example.util.LocationHelper.hasLocationPermission(context)
    }

    /**
     * Syncs music playback state from website/app HTML5 audio to native MediaSession
     * and shows lockscreen controls + notification shade media widget.
     */
    @JavascriptInterface
    fun onMusicPlaybackStateChanged(
        isPlaying: Boolean,
        title: String?,
        artist: String?,
        durationSec: Long = 0L,
        positionSec: Long = 0L
    ) {
        scope.launch(Dispatchers.Main) {
            val trackTitle = title?.takeIf { it.isNotBlank() } ?: "Mera Thikaana"
            val trackArtist = artist?.takeIf { it.isNotBlank() } ?: "Ranchi Beats"
            com.example.music.ThikanaMusicService.updatePlayback(
                context = context,
                isPlaying = isPlaying,
                title = trackTitle,
                artist = trackArtist,
                durationSec = durationSec,
                positionSec = positionSec
            )
        }
    }

    /**
     * Returns the cached Firebase Cloud Messaging device token.
     */
    @JavascriptInterface
    fun getFcmToken(): String {
        return com.example.notifications.ThikanaFirebaseMessagingService.getSavedToken(context) ?: ""
    }

    /**
     * Associates the current logged-in user with the device's FCM token on the backend server.
     */
    @JavascriptInterface
    fun syncFcmToken(userId: String?) {
        val token = com.example.notifications.ThikanaFirebaseMessagingService.getSavedToken(context)
        if (!token.isNullOrBlank()) {
            com.example.notifications.ThikanaFirebaseMessagingService.syncTokenWithServer(context, token, userId)
        }
        com.example.notifications.ThikanaNotificationSyncManager.syncNow(context)
    }

    /**
     * Allows web views and in-app scripts to post real Android system notifications (status bar + heads up banner).
     */
    @JavascriptInterface
    fun showNativeNotification(title: String, body: String, type: String, id: String?, url: String?) {
        val data = mutableMapOf<String, String>()
        if (!id.isNullOrBlank()) data["id"] = id
        if (!url.isNullOrBlank()) data["url"] = url
        data["type"] = type

        com.example.notifications.NotificationHelper.showNotification(
            context = context,
            title = if (title.isNotBlank()) title else "Mera Thikaana",
            body = body,
            type = type,
            data = data,
            customId = id?.hashCode()
        )
    }

    /**
     * Checks if push/system notifications are enabled at the Android system level.
     */
    @JavascriptInterface
    fun isNotificationPermissionGranted(): Boolean {
        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Requests native Android 13+ POST_NOTIFICATIONS runtime permission if not granted yet.
     */
    @JavascriptInterface
    fun requestNotificationPermission() {
        val activity = when (context) {
            is android.app.Activity -> context
            is android.content.ContextWrapper -> context.baseContext as? android.app.Activity
            else -> null
        }
        activity?.runOnUiThread {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        activity,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        2001
                    )
                }
            }
        }
    }

    /**
     * Manually triggers an immediate background poll of /api/notifications and /api/messages.
     */
    @JavascriptInterface
    fun syncNotificationsNow() {
        com.example.notifications.ThikanaNotificationSyncManager.syncNow(context)
    }

    /**
     * Opens Android System Settings directly to "Open supported links" so the user can
     * toggle the app to open thikana.pages.dev links automatically instead of Chrome.
     */
    @JavascriptInterface
    fun openDefaultAppSettings() {
        val activity = when (context) {
            is android.app.Activity -> context
            is android.content.ContextWrapper -> context.baseContext as? android.app.Activity
            else -> null
        }
        activity?.runOnUiThread {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                    activity.startActivity(intent)
                } else {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                    activity.startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("ThikanaBridge", "Could not open default app settings", e)
            }
        }
    }

    /**
     * Directly launches WhatsApp with the shared place, trip, or post.
     */
    @JavascriptInterface
    fun shareToWhatsApp(text: String, url: String) {
        val activity = when (context) {
            is android.app.Activity -> context
            is android.content.ContextWrapper -> context.baseContext as? android.app.Activity
            else -> null
        }
        activity?.runOnUiThread {
            try {
                val fullText = if (url.isNotBlank() && !text.contains(url)) {
                    if (text.isNotBlank()) "$text\n$url" else url
                } else {
                    text
                }
                val waIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, fullText)
                    type = "text/plain"
                    setPackage("com.whatsapp")
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activity.startActivity(waIntent)
            } catch (e: Exception) {
                try {
                    val encoded = java.net.URLEncoder.encode(if (url.isNotBlank() && !text.contains(url)) "$text $url" else text, "UTF-8")
                    val fallbackUri = android.net.Uri.parse("https://wa.me/?text=$encoded")
                    val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, fallbackUri).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    activity.startActivity(browserIntent)
                } catch (e2: Exception) {
                    shareText("Share to WhatsApp", text, url)
                }
            }
        }
    }

    /**
     * Native Android Share sheet implementation for trips, places, and posts.
     */
    @JavascriptInterface
    fun shareText(title: String, text: String, url: String) {
        val activity = when (context) {
            is android.app.Activity -> context
            is android.content.ContextWrapper -> context.baseContext as? android.app.Activity
            else -> null
        }
        activity?.runOnUiThread {
            try {
                val fullText = if (url.isNotBlank() && !text.contains(url)) {
                    if (text.isNotBlank()) "$text\n$url" else url
                } else {
                    text
                }
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TITLE, title)
                    putExtra(android.content.Intent.EXTRA_TEXT, fullText)
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, title.ifBlank { "Share via" })
                activity.startActivity(shareIntent)
            } catch (e: Exception) {
                android.util.Log.e("ThikanaBridge", "Error sharing text", e)
            }
        }
    }

    /**
     * Updates badge counts for Messages, Notifications (Bell), Vibes (Feed), and Find Dost.
     */
    @JavascriptInterface
    fun updateBadgeCounts(messages: Int, notifs: Int, vibes: Int, dost: Int) {
        val activity = when (context) {
            is android.app.Activity -> context
            is android.content.ContextWrapper -> context.baseContext as? android.app.Activity
            else -> null
        }
        activity?.runOnUiThread {
            com.example.notifications.ThikanaBadgeManager.update(
                messages = messages,
                notifs = notifs,
                vibes = vibes,
                dost = dost
            )
        }
    }

    /**
     * Updates current authenticated user profile information for the profile tab avatar.
     */
    @JavascriptInterface
    fun updateCurrentUser(userId: String?, handle: String?, avatarUrl: String?, displayName: String?) {
        val activity = when (context) {
            is android.app.Activity -> context
            is android.content.ContextWrapper -> context.baseContext as? android.app.Activity
            else -> null
        }
        activity?.runOnUiThread {
            com.example.user.ThikanaUserManager.update(
                context = context,
                id = userId,
                userHandle = handle,
                userAvatar = avatarUrl,
                name = displayName
            )
        }
    }

    /**
     * Preloads and caches a music track for instantaneous and offline playback.
     */
    @JavascriptInterface
    fun preloadMusicTrack(trackUrl: String?) {
        if (!trackUrl.isNullOrBlank()) {
            val fullUrl = if (trackUrl.startsWith("http")) trackUrl else "https://thikana.pages.dev$trackUrl"
            com.example.music.ThikanaMusicCacheManager.preload(fullUrl)
        }
    }
}

