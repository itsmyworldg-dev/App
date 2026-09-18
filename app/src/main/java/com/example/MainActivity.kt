package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import com.example.navigation.ThikanaNavigationService
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.camera.CameraCaptureScreen
import com.example.camera.CameraMode
import com.example.editor.CollageTemplate
import com.example.editor.ImageSaveUtil
import com.example.editor.StoryPostEditorScreen
import com.example.storyeditor.camera.SnapchatCameraView
import com.example.storyeditor.collage.CollageMakerScreen
import com.example.storyeditor.editor.StoryEditorScreen
import com.example.ui.MediaSourceChooserSheet
import com.example.music.ThikanaMusicService
import com.example.notifications.NotificationHelper
import com.example.sensor.NavigationSensorBridge
import com.example.ui.ThikanaScreen
import com.example.ui.ThikanaTab
import com.example.ui.dialogs.LocationEnableDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.voice.VibeVoiceSheet
import com.example.util.LocationHelper
import com.example.voice.GeminiVoiceService
import com.example.voice.NavigationVoiceManager
import com.example.voice.VoiceController
import com.example.web.ThikanaNativeBridge
import com.example.web.ThikanaWebChromeClient
import com.example.notifications.ThikanaFirebaseMessagingService
import com.google.firebase.messaging.FirebaseMessaging
import com.example.web.ThikanaWebViewClient
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        const val APP_URL = "https://thikana.pages.dev"
        private const val TAG = "MainActivity"
        var currentInstance: MainActivity? = null

        init {
            ThikanaApplication.applyMesaSuppression()
        }
    }

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var galleryPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var storyMakerPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var appPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var audioPermissionLauncher: ActivityResultLauncher<String>

    private lateinit var sensorBridge: NavigationSensorBridge
    private lateinit var voiceController: VoiceController
    private lateinit var navVoiceManager: NavigationVoiceManager
    private val geminiVoiceService = GeminiVoiceService()

    private val isLocationPromptOpenState = mutableStateOf(false)
    private val isVoiceNavOpenState = mutableStateOf(false)
    private val isVoiceThinkingState = mutableStateOf(false)
    private val voiceAiResponseState = mutableStateOf<String?>(null)
    private val isTtsMutedState = mutableStateOf(false)

    private val currentTabState = mutableStateOf(ThikanaTab.FEED)
    private val isBottomNavVisibleState = mutableStateOf(true)
    private val isUploadChooserOpenState = mutableStateOf(false)
    private val loadingProgressState = mutableIntStateOf(0)
    private val isLoadingState = mutableStateOf(true)
    private val isInitialLoadCompletedState = mutableStateOf(false)
    private val errorMessageState = mutableStateOf<String?>(null)
    private val webViewInstanceState = mutableStateOf<WebView?>(null)

    // Native Instagram Camera & Story/Post Editor States
    val isMediaSourceSheetOpenState = mutableStateOf(false)
    val isNativeCameraOpenState = mutableStateOf(false)
    val isNativeEditorOpenState = mutableStateOf(false)
    val editorBitmapsState = mutableStateOf<List<Bitmap>>(emptyList())
    val editorModeState = mutableStateOf(CameraMode.POST)
    val editorTemplateState = mutableStateOf(CollageTemplate.SINGLE)

    // Native Snapchat Camera, Collage Maker, and Story Editor States
    val isSnapchatCameraOpenState = mutableStateOf(false)
    val isCollageMakerOpenState = mutableStateOf(false)
    val isStoryMakerOpenState = mutableStateOf(false)
    val storyEditorBitmapState = mutableStateOf<Bitmap?>(null)

    fun openNativeCamera(mode: String = "post") {
        isSnapchatCameraOpenState.value = true
    }

    private fun isImageUri(uri: Uri): Boolean {
        val mimeType = try {
            contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }
        if (mimeType != null) return mimeType.startsWith("image/")
        val ext = uri.toString().substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
    }

    // Decodes a gallery-picked image Uri into an editable Bitmap, downsampling large photos
    // and correcting EXIF rotation - mirroring what CameraCaptureScreen does for camera shots
    // so gallery photos land in the same Story/Post editor pipeline.
    private fun decodeGalleryBitmap(uri: Uri): Bitmap? {
        return try {
            val maxDimension = 2048
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val streamOpened = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
                true
            }
            if (streamOpened != true) return null

            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > maxDimension ||
                boundsOptions.outHeight / sampleSize > maxDimension
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val rotationDegrees = try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (_: Exception) {
                0f
            }

            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding gallery image: $uri", e)
            null
        }
    }

    private var lastKnownUrl: String = APP_URL
    private var currentTabKey: String = "feed"
    private var pendingNotificationIntent: Intent? = null
    private var pendingDeepLinkUri: Uri? = null
    private var isPageFinishedLoading: Boolean = false

    private fun updateDockVisibility(url: String? = null) {
        val activeUrl: String = url ?: (if (::webView.isInitialized) webView.url else null) ?: lastKnownUrl
        lastKnownUrl = activeUrl
        if (!ThikanaWebViewClient.isThikanaUrl(activeUrl)) {
            // All pages except thikana pages: the dock should be removed or hidden
            isBottomNavVisibleState.value = false
        } else {
            // On thikana pages: visible if the active view is one of the primary tabs
            isBottomNavVisibleState.value = ThikanaTab.isBottomNavVisible(currentTabKey)
        }
    }

    // Last dock height (in CSS px == dp, since index.html's viewport meta pins
    // initial-scale=1.0) that was successfully pushed into the WebView as
    // --native-dock-height. Cached so onGloballyPositioned's frequent
    // recomposition callbacks don't spam evaluateJavascript with no-op calls,
    // and so onPageLoadFinished can resync the *same* value after a
    // navigation resets the page's inline style.
    private var lastPushedDockHeightPx: Int = -1

    /**
     * Mirrors the exact --kb (keyboard height) pattern already used in
     * app.js's initKeyboardInsets(): measure the real thing natively, hand
     * it to the page as a CSS variable, let CSS do the rest. Called on every
     * dock layout change (ThikanaScreen's onDockHeightChanged) so the web
     * content's bottom clearance is always exactly right — not a guess —
     * across devices, system nav-bar styles, rotations, and dock
     * show/hide transitions.
     *
     * @param force resend even if the value didn't change from last time —
     *   used after a page (re)load, since a fresh document has no memory of
     *   the previously-injected inline style.
     */
    private fun updateDockHeightCss(heightDp: Dp, force: Boolean = false) {
        val heightPx = heightDp.value.let { if (it < 0f) 0f else it }
        val rounded = kotlin.math.round(heightPx).toInt()
        if (!force && rounded == lastPushedDockHeightPx) return
        lastPushedDockHeightPx = rounded
        if (!::webView.isInitialized) return
        val js = "(function(){try{document.documentElement.style.setProperty('--native-dock-height','${rounded}px');}catch(e){}})();"
        try {
            webView.evaluateJavascript(js, null)
        } catch (_: Exception) {
            // WebView not attached / page not ready yet — the next layout
            // pass or onPageLoadFinished resync will retry.
        }
    }

    // Last system-navigation-bar inset (in CSS px == dp) pushed into the WebView as
    // --android-sys-nav-inset. Separate from --native-dock-height (which is about the
    // long-removed native floating dock, not the real OS nav bar) so this fix can't
    // disturb any of the sheet/overlay paddings that already key off that other var.
    private var lastPushedSysNavInsetPx: Int = -1

    /**
     * Android System WebView does not reliably report env(safe-area-inset-bottom) the
     * way Chrome for Android / an installed PWA does, so the web app's own fixed-position
     * .navbar (which relies on that env() value to clear a 3-button/2-button system nav
     * bar) can end up sitting partly underneath it. This measures the real system nav-bar
     * height natively (0 on full gesture-nav devices, where there's nothing to clear) and
     * hands it to the page as a CSS var, mirroring the existing --native-dock-height /
     * --kb plumbing pattern.
     *
     * @param force resend even if unchanged — used after a page (re)load, since a fresh
     *   document has no memory of the previously-injected inline style.
     */
    private fun updateSystemNavInsetCss(heightDp: Dp, force: Boolean = false) {
        val heightPx = heightDp.value.let { if (it < 0f) 0f else it }
        val rounded = kotlin.math.round(heightPx).toInt()
        if (!force && rounded == lastPushedSysNavInsetPx) return
        lastPushedSysNavInsetPx = rounded
        if (!::webView.isInitialized) return
        val js = "(function(){try{document.documentElement.style.setProperty('--android-sys-nav-inset','${rounded}px');}catch(e){}})();"
        try {
            webView.evaluateJavascript(js, null)
        } catch (_: Exception) {
            // WebView not attached / page not ready yet — the next LaunchedEffect
            // firing or onPageLoadFinished resync will retry.
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        ThikanaApplication.prepareEnvironment(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this
        ThikanaApplication.prepareEnvironment(this)
        
        // Ensure notification and status bar icons are always crisp, dark, and visible on the light theme
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars())
            show(WindowInsetsCompat.Type.navigationBars())
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        // Sensor Bridge for silky smooth 60fps compass and GPS updates
        sensorBridge = NavigationSensorBridge(this)
        sensorBridge.startTracking()

        // Turn-by-Turn Voice Navigation Engine
        navVoiceManager = NavigationVoiceManager(this)

        // Configure notification channels
        NotificationHelper.setupNotificationChannels(this)

        // Initialize background notification sync engine
        com.example.notifications.ThikanaNotificationSyncManager.startPeriodicSync(this)

        // Initialize Firebase Cloud Messaging token & background alert receiver
        try {
            val playServicesAvailable = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(this) == com.google.android.gms.common.ConnectionResult.SUCCESS

            if (playServicesAvailable) {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    try {
                        if (task.isSuccessful && !task.result.isNullOrBlank()) {
                            val token = task.result
                            Log.i("MainActivity", "FCM Device Token: $token")
                            FirebaseMessaging.getInstance().isAutoInitEnabled = true
                            ThikanaFirebaseMessagingService.saveToken(this, token)
                            ThikanaFirebaseMessagingService.syncTokenWithServer(this, token)
                            notifyFcmTokenUpdated(token)
                        } else {
                            Log.i("MainActivity", "FCM token not available in this environment, using native background sync.")
                        }
                    } catch (t: Throwable) {
                        Log.i("MainActivity", "FCM registration handled gracefully: ${t.message}")
                    }
                }
            } else {
                Log.i("MainActivity", "Google Play Services unavailable on this environment; relying on ThikanaNotificationSyncManager for real-time alerts.")
            }
        } catch (e: Throwable) {
            Log.i("MainActivity", "FCM initialization deferred: ${e.message}")
        }

        // Process notification launch if opened from push notification
        handleNotificationIntent(intent)

        // Initialize Activity Result Launchers
        setupLaunchers()

        // Initialize Music Cache and User Profile Manager
        com.example.music.ThikanaMusicCacheManager.init(this)
        com.example.maps.ThikanaMapCacheManager.init(this)
        com.example.user.ThikanaUserManager.init(this)

        // Initialize Gen Z Voice Controller
        voiceController = VoiceController(this).apply {
            onSpeechRecognized = { transcript ->
                processVoiceQuery(transcript)
            }
            onSpeechError = { errorMsg ->
                if (voiceAiResponseState.value.isNullOrBlank()) {
                    voiceAiResponseState.value = errorMsg
                }
            }
        }

        // Request all required runtime permissions smoothly (Location, Camera, Audio, Notifications)
        requestEssentialPermissions()

        // Bridge native lockscreen and notification media controls to web audio player
        ThikanaMusicService.commandListener = { command, arg ->
            executeMusicCommand(command, arg)
        }

        // Check if launched via Deep Link (trip, place, post, or web url)
        val startupUri = intent?.data
        if (startupUri != null) {
            val startupUrl = computeInitialUrlFromDeepLink(startupUri)
            if (!startupUrl.isNullOrBlank()) {
                lastKnownUrl = startupUrl
            }
            pendingDeepLinkUri = startupUri
        }

        // Initialize and configure WebView
        webView = createWebView()
        webViewInstanceState.value = webView
        loadInitialUrl(lastKnownUrl.ifBlank { APP_URL })

        // Handle hardware and gesture back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isMediaSourceSheetOpenState.value) {
                    isMediaSourceSheetOpenState.value = false
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    return
                }
                if (isNativeEditorOpenState.value) {
                    isNativeEditorOpenState.value = false
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    return
                }
                if (isNativeCameraOpenState.value) {
                    isNativeCameraOpenState.value = false
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    return
                }
                if (isVoiceNavOpenState.value) {
                    isVoiceNavOpenState.value = false
                    voiceController.stopListening()
                    voiceController.stopSpeaking()
                    return
                }

                if (::webView.isInitialized) {
                    val currentUrl = webView.url ?: lastKnownUrl
                    // If on an external non-Thikana page (e.g. Google Sign-In or third-party web page), directly navigate back
                    if (!ThikanaWebViewClient.isThikanaUrl(currentUrl)) {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            webView.loadUrl(APP_URL)
                        }
                        return
                    }

                    webView.evaluateJavascript(
                        """
                        (function() {
                            var chooser = document.getElementById('uploadChooserOverlay');
                            if (chooser && chooser.classList.contains('active')) {
                                if (typeof closeUploadChooser === 'function') {
                                    closeUploadChooser();
                                } else {
                                    chooser.classList.remove('active');
                                }
                                if (window.AndroidNativeAuth && window.AndroidNativeAuth.onUploadChooserVisibilityChanged) {
                                    window.AndroidNativeAuth.onUploadChooserVisibilityChanged(false);
                                }
                                return true;
                            }
                            var detailOverlay = document.getElementById('overlay');
                            if (detailOverlay && detailOverlay.classList.contains('active')) {
                                if (typeof closeDetail === 'function') {
                                    closeDetail();
                                } else {
                                    detailOverlay.classList.remove('active');
                                }
                                return true;
                            }
                            var msgOverlay = document.getElementById('messagesOverlay');
                            if (msgOverlay && msgOverlay.classList.contains('active')) {
                                var inThread = (typeof window.msgView !== 'undefined' && window.msgView === 'thread') ||
                                               !msgOverlay.classList.contains('msg-inbox-open') ||
                                               msgOverlay.querySelector('.msg-input-row, #msgTextInput, .msg-page') !== null;
                                if (inThread && typeof msgThreadBack === 'function') {
                                    msgThreadBack();
                                    return true;
                                } else if (typeof closeMessages === 'function') {
                                    closeMessages();
                                    return true;
                                } else {
                                    if (typeof hideMessagesUI === 'function') hideMessagesUI();
                                    msgOverlay.classList.remove('active', 'view-in', 'msg-inbox-open');
                                    var targetView = (typeof topView === 'function' ? topView() : 'discover') || 'discover';
                                    if (typeof renderActiveView === 'function') {
                                        renderActiveView(targetView);
                                    } else {
                                        var el = document.getElementById('view-' + targetView) || document.getElementById('view-discover');
                                        if (el) el.classList.add('active');
                                    }
                                    if (window.AndroidNativeAuth && window.AndroidNativeAuth.onWebViewChanged) {
                                        window.AndroidNativeAuth.onWebViewChanged(targetView);
                                    }
                                    return true;
                                }
                            }
                            var activeOverlay = document.querySelector('.overlay.active, .navoverlay.active, .authmodal.active');
                            if (activeOverlay) {
                                activeOverlay.classList.remove('active');
                                var anyActiveView = document.querySelector('.view.active');
                                if (!anyActiveView) {
                                    var fallback = (typeof topView === 'function' ? topView() : 'discover') || 'discover';
                                    if (typeof renderActiveView === 'function') {
                                        renderActiveView(fallback);
                                    } else {
                                        var el = document.getElementById('view-' + fallback) || document.getElementById('view-discover');
                                        if (el) el.classList.add('active');
                                    }
                                    if (window.AndroidNativeAuth && window.AndroidNativeAuth.onWebViewChanged) {
                                        window.AndroidNativeAuth.onWebViewChanged(fallback);
                                    }
                                }
                                return true;
                            }
                            return false;
                        })();
                        """.trimIndent()
                    ) { result ->
                        if (result != "true") {
                            if (webView.canGoBack()) {
                                webView.goBack()
                            } else {
                                isEnabled = false
                                onBackPressedDispatcher.onBackPressed()
                            }
                        }
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent {
            val view = androidx.compose.ui.platform.LocalView.current
            if (!view.isInEditMode) {
                androidx.compose.runtime.SideEffect {
                    val activityWindow = (view.context as? android.app.Activity)?.window ?: window
                    WindowCompat.getInsetsController(activityWindow, view).apply {
                        isAppearanceLightStatusBars = true
                        isAppearanceLightNavigationBars = true
                        show(WindowInsetsCompat.Type.statusBars())
                        show(WindowInsetsCompat.Type.navigationBars())
                    }
                }
            }

            MyApplicationTheme {
                // Precise edge-to-edge window inset management:
                // Status bars and navigation bars are applied cleanly without stacking extra dead gap when keyboard opens
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = Color.White
                ) { _ ->
                    val activeWebView = webViewInstanceState.value ?: webView
                    val isNavActive by navVoiceManager.isNavActive.collectAsStateWithLifecycle()
                    val navInstruction by navVoiceManager.currentInstruction.collectAsStateWithLifecycle()
                    val navDistance by navVoiceManager.distance.collectAsStateWithLifecycle()
                    val navNextInstruction by navVoiceManager.nextInstruction.collectAsStateWithLifecycle()
                    val navDestinationName by navVoiceManager.destinationName.collectAsStateWithLifecycle()
                    val isNavMuted by navVoiceManager.isMuted.collectAsStateWithLifecycle()
                    val isNavSpeaking by navVoiceManager.isSpeaking.collectAsStateWithLifecycle()

                    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

                    // Tells apart a real system dock (3-button or 2-button navigation, which is a solid
                    // bar the OS reserves and reports as "tappable") from full gesture navigation (where
                    // the bottom inset is just a thin swipe-back reservation with no tappable bar at all).
                    // This is the officially documented way to distinguish the two: in 3/2-button mode the
                    // tappable inset equals the navigation bar inset; in gesture mode it's zero.
                    // Additionally check navBarBottom >= 32.dp to reliably cover OEM skins where tappableElement
                    // returns 0 despite 3-button navigation being active.
                    val tappableBottom = WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding()
                    val hasSystemDock = tappableBottom > 0.dp || navBarBottom >= 32.dp

                    // Keep the web app's .navbar lifted clear of a real 3-button/2-button
                    // system nav bar (see updateSystemNavInsetCss doc above) — 0 on
                    // gesture-nav devices, matching plain-web/PWA behavior exactly.
                    androidx.compose.runtime.LaunchedEffect(navBarBottom, hasSystemDock) {
                        updateSystemNavInsetCss(if (hasSystemDock) navBarBottom else 0.dp)
                    }

                    // Fast, zero-gap keyboard layout:
                    // When the soft keyboard is open (imeBottom > 0.dp), the keyboard replaces the system navigation bar.
                    // By setting bottom inset directly to imeBottom (without adding navBarBottom on top),
                    // the chat input row sits snugly and directly above the keyboard with no awkward gap!
                    // When the keyboard is closed:
                    // If bottom nav dock is hidden (chat screen, overlays, navigation) AND the device has a real
                    // system dock (3-button/2-button nav), pad by navBarBottom so content sits cleanly above it.
                    // If the device is on full gesture navigation (no system dock), there's nothing to clear -
                    // let content run all the way to the physical bottom edge of the screen.
                    // If bottom dock is visible (feed, discover, etc.), dock handles its own padding, so bottom is 0.dp.
                    val bottomInset = if (imeBottom > 0.dp) {
                        imeBottom
                    } else if ((!isBottomNavVisibleState.value || isNavActive) && hasSystemDock) {
                        navBarBottom
                    } else {
                        0.dp
                    }

                    ThikanaScreen(
                        webView = activeWebView,
                        loadingProgress = loadingProgressState.intValue,
                        isLoading = isLoadingState.value,
                        isInitialLoading = !isInitialLoadCompletedState.value && errorMessageState.value == null,
                        errorMessage = errorMessageState.value,
                        currentTab = currentTabState.value,
                        showBottomNav = isBottomNavVisibleState.value && !isNavActive,
                        isUploadChooserOpen = isUploadChooserOpenState.value,
                        messagesBadgeCount = com.example.notifications.ThikanaBadgeManager.messagesCount.intValue,
                        vibesBadgeCount = com.example.notifications.ThikanaBadgeManager.vibesCount.intValue,
                        discoverBadgeCount = com.example.notifications.ThikanaBadgeManager.dostCount.intValue,
                        isNavActive = isNavActive,
                        navInstruction = navInstruction,
                        navDistance = navDistance,
                        navNextInstruction = navNextInstruction,
                        navDestinationName = navDestinationName,
                        isNavMuted = isNavMuted,
                        isNavSpeaking = isNavSpeaking,
                        onTabSelected = { selectedTab ->
                            navigateToTab(selectedTab)
                        },
                        onOpenVoiceNav = {
                            openVoiceNav()
                        },
                        onRepeatNavInstruction = {
                            navVoiceManager.repeatCurrentInstruction()
                        },
                        onToggleNavMute = {
                            navVoiceManager.toggleMute()
                        },
                        onRetry = {
                            errorMessageState.value = null
                            isLoadingState.value = true
                            isInitialLoadCompletedState.value = false
                            try {
                                if (::webView.isInitialized) {
                                    webView.reload()
                                } else {
                                    recreateWebView()
                                }
                            } catch (_: Throwable) {
                                recreateWebView()
                            }
                        },
                        onOpenNativeCamera = { mode ->
                            openNativeCamera(mode)
                        },
                        navBarBottomInset = navBarBottom,
                        onDockHeightChanged = { heightDp ->
                            updateDockHeightCss(heightDp)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = statusBarTop, bottom = bottomInset)
                    )

                    // Turn On Location Dialog Popup (when location is off and user taps navigation or my location)
                    LocationEnableDialog(
                        isOpen = isLocationPromptOpenState.value,
                        onTurnOnLocation = {
                            isLocationPromptOpenState.value = false
                            LocationHelper.openLocationSettings(this@MainActivity)
                        },
                        onDismiss = {
                            isLocationPromptOpenState.value = false
                        }
                    )

                    // Gen Z VibeNav Live Voice Assistant Modal Sheet
                    if (isVoiceNavOpenState.value) {
                        val isListening by voiceController.isListening.collectAsStateWithLifecycle()
                        val speechRms by voiceController.speechRms.collectAsStateWithLifecycle()
                        val partialTranscript by voiceController.partialTranscript.collectAsStateWithLifecycle()

                        VibeVoiceSheet(
                            isListening = isListening,
                            speechRms = speechRms,
                            isThinking = isVoiceThinkingState.value,
                            currentTranscript = partialTranscript,
                            aiResponse = voiceAiResponseState.value,
                            isTtsMuted = isTtsMutedState.value,
                            onToggleTtsMute = {
                                isTtsMutedState.value = !isTtsMutedState.value
                                if (isTtsMutedState.value) {
                                    voiceController.stopSpeaking()
                                }
                            },
                            onStartListening = {
                                openVoiceNav()
                            },
                            onStopListening = {
                                voiceController.stopListening()
                            },
                            onSendQuery = { query ->
                                processVoiceQuery(query)
                            },
                            onDismiss = {
                                voiceController.stopListening()
                                voiceController.stopSpeaking()
                                isVoiceNavOpenState.value = false
                            }
                        )
                    }

                    // Media Source Chooser Bottom Sheet (Camera vs Story Maker vs Photos & videos)
                    MediaSourceChooserSheet(
                        visible = isMediaSourceSheetOpenState.value,
                        onDismiss = {
                            isMediaSourceSheetOpenState.value = false
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = null
                        },
                        onCameraSelected = {
                            isMediaSourceSheetOpenState.value = false
                            isSnapchatCameraOpenState.value = true
                        },
                        onStoryMakerSelected = {
                            isMediaSourceSheetOpenState.value = false
                            try {
                                val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "image/*"
                                }
                                val chooser = Intent.createChooser(contentSelectionIntent, "Choose photo for Story Maker")
                                storyMakerPickerLauncher.launch(chooser)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error launching photo picker for Story Maker", e)
                                storyEditorBitmapState.value = BitmapFactory.decodeResource(resources, com.example.R.drawable.sample_portrait)
                                isStoryMakerOpenState.value = true
                            }
                        },
                        onGallerySelected = {
                            isMediaSourceSheetOpenState.value = false
                            try {
                                val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                }
                                val chooser = Intent.createChooser(contentSelectionIntent, "Select photos or videos")
                                // Routed through galleryPickerLauncher so photo picks get the
                                // native Story/Post editor
                                galleryPickerLauncher.launch(chooser)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error launching gallery picker", e)
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = null
                            }
                        }
                    )

                    // Native Snapchat-Style Camera View
                    if (isSnapchatCameraOpenState.value) {
                        SnapchatCameraView(
                            onPhotoCaptured = { bitmap ->
                                storyEditorBitmapState.value = bitmap
                                isSnapchatCameraOpenState.value = false
                                isStoryMakerOpenState.value = true
                            },
                            onClose = {
                                isSnapchatCameraOpenState.value = false
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = null
                            },
                            onNavigateToCollage = {
                                isSnapchatCameraOpenState.value = false
                                isCollageMakerOpenState.value = true
                            }
                        )
                    }

                    // Native Collage Maker View
                    if (isCollageMakerOpenState.value) {
                        CollageMakerScreen(
                            onCollageReadyForEditor = { collageBitmap ->
                                storyEditorBitmapState.value = collageBitmap
                                isCollageMakerOpenState.value = false
                                isStoryMakerOpenState.value = true
                            },
                            onBack = {
                                isCollageMakerOpenState.value = false
                                isSnapchatCameraOpenState.value = true
                            }
                        )
                    }

                    // Native Story Maker & Photo Editor Screen
                    if (isStoryMakerOpenState.value) {
                        val initialBitmap = storyEditorBitmapState.value
                            ?: BitmapFactory.decodeResource(resources, com.example.R.drawable.sample_portrait)
                        StoryEditorScreen(
                            initialBitmap = initialBitmap,
                            onBack = {
                                isStoryMakerOpenState.value = false
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = null
                            },
                            onDone = { createdUri ->
                                isStoryMakerOpenState.value = false
                                if (filePathCallback != null) {
                                    filePathCallback?.onReceiveValue(arrayOf(createdUri))
                                    filePathCallback = null
                                } else {
                                    Toast.makeText(this@MainActivity, "Photo ready! Attaching to spot...", Toast.LENGTH_SHORT).show()
                                    ImageSaveUtil.launchShareIntent(this@MainActivity, createdUri)
                                }
                            },
                            onShareToThikana = { createdUri, destination ->
                                isStoryMakerOpenState.value = false
                                sharePhotoToThikana(createdUri, destination)
                            }
                        )
                    }

                    // Native CameraX Capture View (Story / Post / Collage)
                    if (isNativeCameraOpenState.value) {
                        CameraCaptureScreen(
                            initialMode = editorModeState.value,
                            onDismiss = {
                                isNativeCameraOpenState.value = false
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = null
                            },
                            onProceedToEditor = { bitmaps, mode, template ->
                                editorBitmapsState.value = bitmaps
                                editorModeState.value = mode
                                editorTemplateState.value = template
                                isNativeCameraOpenState.value = false
                                isNativeEditorOpenState.value = true
                            }
                        )
                    }

                    // Native Instagram-Style Story & Post Image Editor with Gemini AI
                    if (isNativeEditorOpenState.value) {
                        StoryPostEditorScreen(
                            initialBitmaps = editorBitmapsState.value,
                            initialMode = editorModeState.value,
                            initialTemplate = editorTemplateState.value,
                            onDismiss = {
                                isNativeEditorOpenState.value = false
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = null
                            },
                            onPostCreated = { createdUri ->
                                isNativeEditorOpenState.value = false
                                if (filePathCallback != null) {
                                    filePathCallback?.onReceiveValue(arrayOf(createdUri))
                                    filePathCallback = null
                                } else {
                                    Toast.makeText(this@MainActivity, "Story ready! Opening share...", Toast.LENGTH_SHORT).show()
                                    ImageSaveUtil.launchShareIntent(this@MainActivity, createdUri)
                                }
                            },
                            onShareToThikana = { createdUri, destination ->
                                isNativeEditorOpenState.value = false
                                sharePhotoToThikana(createdUri, destination)
                            }
                        )
                    }
                }
            }
        }

        // Handle Deep Links if launched with an intent
        handleIntent(intent)

        // Safety fallback: Ensure skeleton dissolves even if network hangs or page takes unusually long
        lifecycleScope.launch {
            kotlinx.coroutines.delay(8500)
            if (!isInitialLoadCompletedState.value) {
                isInitialLoadCompletedState.value = true
            }
        }
    }

    fun sharePhotoToThikana(createdUri: Uri, destination: String) {
        if (filePathCallback != null) {
            filePathCallback?.onReceiveValue(arrayOf(createdUri))
            filePathCallback = null
        }
        val targetMode = if (destination == "gem" || destination == "addgem") "addgem" else "tagspot"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(createdUri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    withContext(Dispatchers.Main) {
                        if (::webView.isInitialized) {
                            val js = """
                                (function() {
                                    if (typeof window.attachPhotoToSpot === 'function') {
                                        window.attachPhotoToSpot('$targetMode', '$base64', 'image/jpeg', 'spot_photo_${System.currentTimeMillis()}.jpg');
                                    } else {
                                        if (typeof showView === 'function') showView('$targetMode');
                                    }
                                })();
                            """.trimIndent()
                            webView.evaluateJavascript(js, null)
                            Toast.makeText(
                                this@MainActivity,
                                if (targetMode == "addgem") "Attaching photo to Hidden Gem..." else "Attaching photo to Tag a Spot...",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send photo to WebView spot", e)
            }
        }
    }

    fun navigateToTab(selectedTab: ThikanaTab) {
        if (selectedTab != ThikanaTab.CREATE) {
            currentTabState.value = selectedTab
            currentTabKey = selectedTab.viewKey

            // Clear badge for the selected tab
            when (selectedTab) {
                ThikanaTab.MESSAGES -> com.example.notifications.ThikanaBadgeManager.clearMessages()
                ThikanaTab.FEED -> com.example.notifications.ThikanaBadgeManager.clearVibes()
                ThikanaTab.DISCOVER -> com.example.notifications.ThikanaBadgeManager.clearDost()
                else -> {}
            }
        }
        val isThikana = ThikanaWebViewClient.isThikanaUrl(if (::webView.isInitialized) webView.url else lastKnownUrl)
        if (isThikana) {
            isBottomNavVisibleState.value = true
            if (::webView.isInitialized) {
                webView.evaluateJavascript("document.body.classList.remove('native-dock-hidden');", null)
                when (selectedTab) {
                    ThikanaTab.CREATE -> {
                        webView.evaluateJavascript(
                            """
                            (function() {
                                var chooser = document.getElementById('uploadChooserOverlay');
                                if (chooser && chooser.classList.contains('active')) {
                                    if (typeof closeUploadChooser === 'function') {
                                        closeUploadChooser();
                                    } else {
                                        chooser.classList.remove('active');
                                    }
                                    if (window.AndroidNativeAuth && window.AndroidNativeAuth.onUploadChooserVisibilityChanged) {
                                        window.AndroidNativeAuth.onUploadChooserVisibilityChanged(false);
                                    }
                                    return;
                                }
                                if (typeof openUploadChooser === 'function') {
                                    openUploadChooser();
                                } else if (typeof openCreateModal === 'function') {
                                    openCreateModal();
                                } else {
                                    var addBtn = document.querySelector('#addBtn, .create-btn, [data-action="create"], .fab-add');
                                    if (addBtn) addBtn.click();
                                    else if (typeof showView === 'function') showView('create');
                                }
                                if (window.AndroidNativeAuth && window.AndroidNativeAuth.onUploadChooserVisibilityChanged) {
                                    window.AndroidNativeAuth.onUploadChooserVisibilityChanged(true);
                                }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                    else -> {
                        webView.evaluateJavascript("if (typeof showView === 'function') showView('${selectedTab.viewKey}');", null)
                    }
                }
            }
        } else {
            isBottomNavVisibleState.value = false
        }
    }

    private fun executeWebAction(action: String) {
        if (!::webView.isInitialized) return
        when {
            action == "SCROLL_DOWN" -> {
                webView.evaluateJavascript("window.scrollBy({ top: 450, behavior: 'smooth' });", null)
            }
            action == "SCROLL_UP" -> {
                webView.evaluateJavascript("window.scrollTo({ top: 0, behavior: 'smooth' });", null)
            }
            action == "RELOAD" -> {
                webView.reload()
            }
            action == "REPEAT_NAV_TURN" -> {
                if (navVoiceManager.isNavActive.value) {
                    navVoiceManager.repeatCurrentInstruction()
                } else {
                    navVoiceManager.speakNavInstruction(
                        "Turn-by-turn voice navigation is active. Tap Start Navigation on any Ranchi spot to get spoken turn directions.",
                        force = true
                    )
                }
            }
            action.startsWith("START_NAV:") -> {
                val dest = action.removePrefix("START_NAV:").trim()
                val safeDest = dest.replace("'", "\\'")
                val js = """
                    (function() {
                        var q = '$safeDest'.toLowerCase();
                        var places = window.PLACES || [];
                        var found = places.find(function(p) {
                            return p.name && p.name.toLowerCase().includes(q);
                        });
                        if (found) {
                            if (typeof window.startNavigation === 'function') {
                                window.startNavigation(found.id);
                            } else if (typeof window.navigateToDestination === 'function') {
                                window.navigateToDestination(found);
                            }
                        } else {
                            if (typeof window.showView === 'function') {
                                window.showView('discover');
                            }
                            var input = document.getElementById('mapSearchInput') || document.querySelector('input[type="search"]');
                            if (input) {
                                input.value = '$safeDest';
                                input.dispatchEvent(new Event('input', { bubbles: true }));
                            }
                        }
                    })();
                """.trimIndent()
                webView.evaluateJavascript(js, null)
            }
        }
    }

    private fun openVoiceNav() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            isVoiceNavOpenState.value = true
            voiceController.startListening()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun processVoiceQuery(query: String) {
        if (query.isBlank()) return
        lifecycleScope.launch {
            isVoiceThinkingState.value = true
            val result = geminiVoiceService.askGemini(query)
            isVoiceThinkingState.value = false
            when (result) {
                is GeminiVoiceService.VoiceCommandResult.Navigate -> {
                    voiceAiResponseState.value = result.genZReply
                    if (!isTtsMutedState.value) {
                        voiceController.speak(result.genZReply)
                    }
                    navigateToTab(result.tab)
                }
                is GeminiVoiceService.VoiceCommandResult.ExecuteAction -> {
                    val reply = if (result.action == "REPEAT_NAV_TURN" && navVoiceManager.isNavActive.value) {
                        val cur = navVoiceManager.currentInstruction.value
                        val dist = navVoiceManager.distance.value
                        if (cur.isNotBlank()) {
                            if (dist.isNotBlank()) "In $dist, $cur. Seedha continue!" else "$cur. Seedha continue!"
                        } else {
                            "Route locked in! Head straight on your route."
                        }
                    } else {
                        result.genZReply
                    }
                    voiceAiResponseState.value = reply
                    if (!isTtsMutedState.value) {
                        voiceController.speak(reply)
                    }
                    executeWebAction(result.action)
                }
                is GeminiVoiceService.VoiceCommandResult.Conversation -> {
                    voiceAiResponseState.value = result.reply
                    if (!isTtsMutedState.value) {
                        voiceController.speak(result.reply)
                    }
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        handleNotificationIntent(intent)

        val musicCmd = intent?.getStringExtra("music_command")
        val musicArg = intent?.getLongExtra("music_command_arg", 0L) ?: 0L
        if (!musicCmd.isNullOrBlank()) {
            executeMusicCommand(musicCmd, musicArg)
        }

        val appLinkData: Uri? = intent?.data
        if (appLinkData != null) {
            processDeepLink(appLinkData)
        }
    }

    private fun computeInitialUrlFromDeepLink(uri: Uri): String? {
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https" && scheme != "thikana" && scheme != "merathikaana") return null

        val tripParam = uri.getQueryParameter("trip")
        if (!tripParam.isNullOrBlank()) return "$APP_URL/?trip=$tripParam"

        val placeParam = uri.getQueryParameter("place")
        if (!placeParam.isNullOrBlank()) return "$APP_URL/?place=$placeParam"

        val postParam = uri.getQueryParameter("post")
        if (!postParam.isNullOrBlank()) return "$APP_URL/?post=$postParam"

        val segments = uri.pathSegments
        for (i in 0 until segments.size) {
            val seg = segments[i].lowercase()
            if ((seg == "trip" || seg == "trips") && i + 1 < segments.size) {
                return "$APP_URL/?trip=${segments[i + 1]}"
            }
            if ((seg == "place" || seg == "places") && i + 1 < segments.size) {
                return "$APP_URL/?place=${segments[i + 1]}"
            }
            if ((seg == "post" || seg == "posts") && i + 1 < segments.size) {
                return "$APP_URL/?post=${segments[i + 1]}"
            }
        }

        if (scheme == "thikana" || scheme == "merathikaana") {
            val host = uri.host?.lowercase()
            val firstSegment = segments.firstOrNull()
            if (host == "trip" && !firstSegment.isNullOrBlank()) return "$APP_URL/?trip=$firstSegment"
            if (host == "place" && !firstSegment.isNullOrBlank()) return "$APP_URL/?place=$firstSegment"
            if (host == "post" && !firstSegment.isNullOrBlank()) return "$APP_URL/?post=$firstSegment"
        }

        val urlStr = uri.toString()
        if (urlStr.contains("thikana.pages.dev")) {
            return urlStr
        }
        return null
    }

    private fun processDeepLink(uri: Uri) {
        val scheme = uri.scheme?.lowercase() ?: return
        if (scheme != "http" && scheme != "https" && scheme != "thikana" && scheme != "merathikaana") return

        if (!::webView.isInitialized || !isPageFinishedLoading) {
            pendingDeepLinkUri = uri
            return
        }

        var tripId: String? = uri.getQueryParameter("trip")
        var placeId: String? = uri.getQueryParameter("place")
        var postId: String? = uri.getQueryParameter("post")

        val segments = uri.pathSegments
        if (tripId.isNullOrBlank() && placeId.isNullOrBlank() && postId.isNullOrBlank()) {
            for (i in 0 until segments.size) {
                val seg = segments[i].lowercase()
                if ((seg == "trip" || seg == "trips") && i + 1 < segments.size) {
                    tripId = segments[i + 1]
                    break
                }
                if ((seg == "place" || seg == "places") && i + 1 < segments.size) {
                    placeId = segments[i + 1]
                    break
                }
                if ((seg == "post" || seg == "posts") && i + 1 < segments.size) {
                    postId = segments[i + 1]
                    break
                }
            }
        }

        if (scheme == "thikana" || scheme == "merathikaana") {
            val host = uri.host?.lowercase()
            val firstSegment = segments.firstOrNull()
            if (tripId.isNullOrBlank() && host == "trip" && !firstSegment.isNullOrBlank()) {
                tripId = firstSegment
            }
            if (placeId.isNullOrBlank() && host == "place" && !firstSegment.isNullOrBlank()) {
                placeId = firstSegment
            }
            if (postId.isNullOrBlank() && host == "post" && !firstSegment.isNullOrBlank()) {
                postId = firstSegment
            }
        }

        runOnUiThread {
            when {
                !tripId.isNullOrBlank() -> {
                    val cleanTripId = tripId.replace(Regex("[^0-9]"), "")
                    if (cleanTripId.isNotEmpty()) {
                        webView.evaluateJavascript(
                            """
                            (function() {
                                if (typeof window.openTripFromLink === 'function') {
                                    window.openTripFromLink($cleanTripId);
                                } else if (typeof window.showView === 'function') {
                                    window.location.search = '?trip=$cleanTripId';
                                }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                }
                !placeId.isNullOrBlank() -> {
                    val cleanPlaceId = placeId.replace(Regex("[^0-9]"), "")
                    if (cleanPlaceId.isNotEmpty()) {
                        webView.evaluateJavascript(
                            "if (typeof window.jumpToPlace === 'function') { window.jumpToPlace('$cleanPlaceId'); }",
                            null
                        )
                    }
                }
                !postId.isNullOrBlank() -> {
                    val cleanPostId = postId.replace(Regex("[^0-9]"), "")
                    if (cleanPostId.isNotEmpty()) {
                        webView.evaluateJavascript(
                            "if (typeof window.openPostViewer === 'function') { window.openPostViewer($cleanPostId); }",
                            null
                        )
                    }
                }
                else -> {
                    val urlStr = uri.toString()
                    if (urlStr.contains("thikana.pages.dev")) {
                        webView.loadUrl(urlStr)
                    }
                }
            }
        }
    }

    /**
     * Executes music playback controls dispatched from the native lockscreen
     * or Android notification shade media notification into the web audio engine.
     */
    fun executeMusicCommand(command: String, arg: Long = 0L) {
        if (!::webView.isInitialized) return
        runOnUiThread {
            when (command) {
                "play" -> {
                    webView.evaluateJavascript(
                        """
                        (function() {
                            try {
                                if (typeof toggleMusicPlay === 'function' && typeof musicIsPlaying === 'function' && !musicIsPlaying()) {
                                    toggleMusicPlay();
                                } else {
                                    var a = document.getElementById('musicAudio');
                                    if (a) a.play();
                                }
                            } catch(e) {}
                        })();
                        """.trimIndent(), null
                    )
                }
                "pause" -> {
                    webView.evaluateJavascript(
                        """
                        (function() {
                            try {
                                if (typeof toggleMusicPlay === 'function' && typeof musicIsPlaying === 'function' && musicIsPlaying()) {
                                    toggleMusicPlay();
                                } else {
                                    var a = document.getElementById('musicAudio');
                                    if (a) a.pause();
                                }
                            } catch(e) {}
                        })();
                        """.trimIndent(), null
                    )
                }
                "next" -> {
                    webView.evaluateJavascript(
                        """
                        (function() {
                            try {
                                if (typeof musicNext === 'function') musicNext();
                            } catch(e) {}
                        })();
                        """.trimIndent(), null
                    )
                }
                "prev" -> {
                    webView.evaluateJavascript(
                        """
                        (function() {
                            try {
                                if (typeof musicPrev === 'function') musicPrev();
                            } catch(e) {}
                        })();
                        """.trimIndent(), null
                    )
                }
                "seek" -> {
                    webView.evaluateJavascript(
                        """
                        (function() {
                            try {
                                var a = document.getElementById('musicAudio');
                                if (a) a.currentTime = $arg;
                            } catch(e) {}
                        })();
                        """.trimIndent(), null
                    )
                }
            }
        }
    }

    /**
     * Receives location fixes directly from ThikanaNavigationService even when screen is locked/off
     * and forwards the coordinates to the web engine.
     */
    fun onBackgroundLocationReceived(location: Location) {
        if (!::webView.isInitialized) return
        runOnUiThread {
            try {
                val js = """
                    (function() {
                        try {
                            if (typeof window.onNavPosition === 'function') {
                                window.onNavPosition({
                                    coords: {
                                        latitude: ${location.latitude},
                                        longitude: ${location.longitude},
                                        accuracy: ${location.accuracy},
                                        heading: ${if (location.hasBearing()) location.bearing else "null"},
                                        speed: ${if (location.hasSpeed()) location.speed else "null"}
                                    },
                                    timestamp: ${location.time}
                                });
                            }
                        } catch(e) {}
                    })();
                """.trimIndent()
                webView.evaluateJavascript(js, null)
            } catch (_: Exception) {}
        }
    }

    private fun setupLaunchers() {
        // File Chooser for Photos, Videos, and Audio
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (filePathCallback == null) return@registerForActivityResult

            var results: Array<Uri>? = null
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val clipData = data?.clipData
                val dataString = data?.dataString

                if (clipData != null) {
                    val count = clipData.itemCount
                    val uriList = ArrayList<Uri>()
                    for (i in 0 until count) {
                        uriList.add(clipData.getItemAt(i).uri)
                    }
                    results = uriList.toTypedArray()
                } else if (data?.data != null) {
                    results = arrayOf(data.data!!)
                } else if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                } else if (cameraImageUri != null) {
                    // Check if camera captured an image
                    results = arrayOf(cameraImageUri!!)
                }
            }

            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
            cameraImageUri = null
        }

        // Gallery Picker for Photos & Videos - photo selections are routed through the same
        // native Story/Post editor (filters, stickers, text, drawing) that camera captures use,
        // instead of being handed straight back to the web app unedited.
        galleryPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (filePathCallback == null) return@registerForActivityResult

            if (result.resultCode != RESULT_OK) {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
                return@registerForActivityResult
            }

            val data = result.data
            val clipData = data?.clipData
            val dataString = data?.dataString
            val uris: List<Uri> = when {
                clipData != null -> (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }
                data?.data != null -> listOf(data.data!!)
                dataString != null -> listOf(Uri.parse(dataString))
                else -> emptyList()
            }

            if (uris.isEmpty()) {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
                return@registerForActivityResult
            }

            val maxEditableSlots = CollageTemplate.entries.maxOf { it.slotCount }
            val canEdit = uris.size <= maxEditableSlots && uris.all { isImageUri(it) }

            if (!canEdit) {
                // Videos (and selections too large for the collage editor) are sent straight
                // through as before - the native editor only supports still photos.
                filePathCallback?.onReceiveValue(uris.toTypedArray())
                filePathCallback = null
                return@registerForActivityResult
            }

            lifecycleScope.launch {
                val bitmaps = withContext(Dispatchers.IO) {
                    uris.mapNotNull { decodeGalleryBitmap(it) }
                }
                if (bitmaps.isEmpty()) {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    return@launch
                }
                editorBitmapsState.value = bitmaps
                editorModeState.value = if (bitmaps.size > 1) CameraMode.COLLAGE else CameraMode.POST
                editorTemplateState.value = when (bitmaps.size) {
                    1 -> CollageTemplate.SINGLE
                    2 -> CollageTemplate.SPLIT_VERTICAL_2
                    3 -> CollageTemplate.SPLIT_3_TOP_HERO
                    else -> CollageTemplate.GRID_4
                }
                isNativeEditorOpenState.value = true
            }
        }

        // Dedicated Story Maker Photo Picker
        storyMakerPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != RESULT_OK || result.data == null) {
                storyEditorBitmapState.value = BitmapFactory.decodeResource(resources, com.example.R.drawable.sample_portrait)
                isStoryMakerOpenState.value = true
                return@registerForActivityResult
            }
            val uri = result.data?.data ?: result.data?.clipData?.getItemAt(0)?.uri
            if (uri != null) {
                lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) { decodeGalleryBitmap(uri) }
                    storyEditorBitmapState.value = bitmap ?: BitmapFactory.decodeResource(resources, com.example.R.drawable.sample_portrait)
                    isStoryMakerOpenState.value = true
                }
            } else {
                storyEditorBitmapState.value = BitmapFactory.decodeResource(resources, com.example.R.drawable.sample_portrait)
                isStoryMakerOpenState.value = true
            }
        }

        // Location Settings Resolution Launcher (Google Play Services 1-tap dialog)
        locationSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                isLocationPromptOpenState.value = false
                if (::sensorBridge.isInitialized) {
                    sensorBridge.startLocationUpdates()
                }
                if (::webView.isInitialized) {
                    webView.evaluateJavascript(
                        "(function() { if (typeof locateUser === 'function') locateUser(); if (typeof checkNavLocation === 'function') checkNavLocation(); })();",
                        null
                    )
                }
            } else {
                // If user declined or cancelled the system dialog, keep custom dialog accessible if needed
                if (!LocationHelper.isLocationServicesEnabled(this@MainActivity)) {
                    isLocationPromptOpenState.value = true
                }
            }
        }

        // Location Permissions
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val granted = fineGranted || coarseGranted

            pendingGeoCallback?.invoke(pendingGeoOrigin, granted, true)
            pendingGeoCallback = null
            pendingGeoOrigin = null

            // If location permission was granted, check if system Location switch is ON
            if (granted) {
                if (::sensorBridge.isInitialized) {
                    sensorBridge.startLocationUpdates()
                }
                if (!LocationHelper.isLocationServicesEnabled(this@MainActivity)) {
                    promptTurnOnLocation()
                }
            }
        }

        // Notification Permission
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            Log.d(TAG, "Notification permission granted: $isGranted")
        }

        // Comprehensive runtime permissions launcher (Location, Camera, Audio, Notifications)
        appPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val locationGranted = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                    (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
            if (locationGranted) {
                if (::sensorBridge.isInitialized) {
                    sensorBridge.startLocationUpdates()
                }
                if (!LocationHelper.isLocationServicesEnabled(this@MainActivity)) {
                    promptTurnOnLocation()
                }
            }
        }

        // Microphone Permission Launcher for VibeNav Voice
        audioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                isVoiceNavOpenState.value = true
                voiceController.startListening()
            } else {
                Toast.makeText(this, "Microphone access needed for speech. You can type commands!", Toast.LENGTH_SHORT).show()
                isVoiceNavOpenState.value = true
            }
        }
    }


    /**
     * Shows a popup / resolution dialog to turn on location whenever navigation or 'My Location' is clicked
     * and the device's location services or permissions are turned off.
     */
    fun promptTurnOnLocation() {
        if (!LocationHelper.hasLocationPermission(this)) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        if (LocationHelper.isLocationServicesEnabled(this)) {
            if (::sensorBridge.isInitialized) {
                sensorBridge.startLocationUpdates()
            }
            return
        }

        // Location switch is OFF: attempt official Google Play Services 1-tap resolution first
        LocationHelper.checkLocationSettings(
            activity = this,
            onResolutionRequired = { resolvable ->
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(resolvable.resolution).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (e: Exception) {
                    Log.w(TAG, "Resolvable resolution failed, opening Compose dialog", e)
                    isLocationPromptOpenState.value = true
                }
            },
            onLocationAlreadyEnabled = {
                if (::sensorBridge.isInitialized) {
                    sensorBridge.startLocationUpdates()
                }
            },
            onResolutionUnavailable = {
                isLocationPromptOpenState.value = true
            }
        )
    }

    private fun requestEssentialPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. Location (Essential for discovery, maps, and nearby thikanas)
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // 2. Camera (For taking thikana photos and videos directly)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        // 3. Audio / Microphone (For videos / voice in posts & reels)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // 4. Notifications (For Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            appPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        // Pre-create and fix all WebView disk cache directory hierarchies and permissions
        ThikanaApplication.prepareEnvironment(this)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#FFF9F4"))
            WebView.setWebContentsDebuggingEnabled(true)

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setGeolocationEnabled(true)
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                safeBrowsingEnabled = false

                // Normalize User-Agent: remove '; wv' and 'Version/4.0' so Google GSI and Cloudflare
                // allow the standard mobile web browser flow without blocking embedded webviews
                val defaultUa = userAgentString
                userAgentString = defaultUa
                    .replace("; wv", "")
                    .replace("Version/4.0 ", "")
            }

            // Enable layer hardware acceleration and smooth scrolling performance
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            isNestedScrollingEnabled = true

            // Register native bridge for 60fps sensor feed, haptics, navigation, and dock sync
            addJavascriptInterface(
                ThikanaNativeBridge(
                    context = this@MainActivity,
                    scope = lifecycleScope,
                    sensorBridge = sensorBridge,
                    navVoiceManager = navVoiceManager,
                    onTabChangedFromWeb = { viewName ->
                        currentTabKey = viewName
                        val isThikana = ThikanaWebViewClient.isThikanaUrl(webView.url ?: lastKnownUrl)
                        if (isThikana) {
                            if (viewName != "chat" && viewName != "msgthread" && viewName != "thread") {
                                currentTabState.value = ThikanaTab.fromKey(viewName)
                            }
                            val isVisible = ThikanaTab.isBottomNavVisible(viewName)
                            isBottomNavVisibleState.value = isVisible
                            val dockClassScript = if (isVisible) {
                                "document.body.classList.remove('native-dock-hidden');"
                            } else {
                                "document.body.classList.add('native-dock-hidden');"
                            }
                            webView.evaluateJavascript(dockClassScript, null)
                        } else {
                            isBottomNavVisibleState.value = false
                        }
                    },
                    onUploadChooserStateChanged = { isOpen ->
                        isUploadChooserOpenState.value = isOpen
                    },
                    onLocationActionRequested = { actionType ->
                        runOnUiThread {
                            Log.d(TAG, "Location action requested from web: $actionType")
                            if (!LocationHelper.isLocationServicesEnabled(this@MainActivity) || !LocationHelper.hasLocationPermission(this@MainActivity)) {
                                promptTurnOnLocation()
                            }
                        }
                    },
                    onOpenCameraRequested = { mode ->
                        openNativeCamera(mode)
                    }
                ),
                "AndroidNativeAuth"
            )

            // Accept cookies (including third-party for Google Sign-In & B2 media)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            webViewClient = ThikanaWebViewClient(
                context = this@MainActivity,
                onPageLoadStarted = { url ->
                    isLoadingState.value = true
                    errorMessageState.value = null
                    updateDockVisibility(url)
                },
                onPageLoadFinished = { url ->
                    isLoadingState.value = false
                    updateDockVisibility(url)
                    isPageFinishedLoading = true

                    // Smooth buffer for web frame compositing before dissolving skeleton
                    window.decorView.postDelayed({
                        isInitialLoadCompletedState.value = true
                    }, 350)

                    // Re-push the last known dock height: a (re)loaded document
                    // has no memory of the --native-dock-height inline style
                    // set on the previous document, so without this the web
                    // content would briefly (or permanently, on a hard reload)
                    // fall back to its 0px default and sit under the dock again.
                    updateDockHeightCss(
                        Dp(lastPushedDockHeightPx.coerceAtLeast(0).toFloat()),
                        force = true
                    )
                    // Same resync, for the same reason, for the system-nav-bar inset var.
                    updateSystemNavInsetCss(
                        Dp(lastPushedSysNavInsetPx.coerceAtLeast(0).toFloat()),
                        force = true
                    )

                    // Resync saved FCM token to web context
                    ThikanaFirebaseMessagingService.getSavedToken(this@MainActivity)?.let { token ->
                        notifyFcmTokenUpdated(token)
                    }

                    // Process pending notification click that opened the app while loading
                    pendingNotificationIntent?.let { pendingIntent ->
                        handleNotificationIntent(pendingIntent)
                        pendingNotificationIntent = null
                    }

                    // Process pending deep link click that opened the app
                    pendingDeepLinkUri?.let { pendingUri ->
                        processDeepLink(pendingUri)
                        pendingDeepLinkUri = null
                    }

                    // Trigger notification sync
                    com.example.notifications.ThikanaNotificationSyncManager.syncNow(this@MainActivity)
                },
                onReceivedLoadError = { errorDesc ->
                    isLoadingState.value = false
                    errorMessageState.value = errorDesc
                    isInitialLoadCompletedState.value = true
                },
                onUrlChanged = { url ->
                    updateDockVisibility(url)
                },
                onRenderProcessCrashed = {
                    runOnUiThread {
                        recreateWebView()
                    }
                }
            )

            webChromeClient = ThikanaWebChromeClient(
                onProgressChange = { progress ->
                    loadingProgressState.intValue = progress
                    isLoadingState.value = progress < 100
                },
                onGeolocationPrompt = { origin, callback ->
                    val fineLocation = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    val coarseLocation = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (fineLocation || coarseLocation) {
                        callback.invoke(origin, true, true)
                        if (!LocationHelper.isLocationServicesEnabled(this@MainActivity)) {
                            promptTurnOnLocation()
                        }
                    } else {
                        pendingGeoOrigin = origin
                        pendingGeoCallback = callback
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                onFileChoose = { callback, params ->
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = callback

                    val acceptTypes = params.acceptTypes
                    val isAudioOnly = acceptTypes != null && acceptTypes.any { type ->
                        type.contains("audio", ignoreCase = true) ||
                        type.endsWith("mp3", ignoreCase = true) ||
                        type.endsWith("m4a", ignoreCase = true) ||
                        type.endsWith("wav", ignoreCase = true) ||
                        type.endsWith("ogg", ignoreCase = true)
                    }

                    if (isAudioOnly) {
                        cameraImageUri = null
                        // Ensure website's general upload chooser overlay is closed/hidden so it doesn't glitch with music
                        isUploadChooserOpenState.value = false
                        webView?.evaluateJavascript(
                            """
                            (function() {
                                try {
                                    if (typeof hideUploadChooserUI === 'function') hideUploadChooserUI();
                                    var chooser = document.getElementById('uploadChooserOverlay');
                                    if (chooser) chooser.classList.remove('active');
                                    if (window.AndroidNativeAuth && window.AndroidNativeAuth.onUploadChooserVisibilityChanged) {
                                        window.AndroidNativeAuth.onUploadChooserVisibilityChanged(false);
                                    }
                                } catch(e) {}
                            })();
                            """.trimIndent(),
                            null
                        )

                        try {
                            val audioIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "audio/*"
                                putExtra(
                                    Intent.EXTRA_MIME_TYPES,
                                    arrayOf("audio/*", "application/ogg", "audio/mpeg", "audio/mp4", "audio/wav", "audio/aac")
                                )
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                            }
                            val chooserIntent = Intent.createChooser(audioIntent, "Select music track")
                            fileChooserLauncher.launch(chooserIntent)
                            true
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening music file chooser", e)
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = null
                            false
                        }
                    } else {
                        // NON-AUDIO: Photos or Videos for Post Details (Tag Spot / Add a hidden gem)
                        // Trigger our native MediaSourceChooserSheet: Camera (with Story & Post AI Editor) vs Photos & videos
                        isMediaSourceSheetOpenState.value = true
                        true
                    }
                },
                onPopupRequested = { parentView, resultMsg ->
                    val transport = resultMsg.obj as? WebView.WebViewTransport
                    if (transport != null) {
                        val popupDialog = android.app.Dialog(this@MainActivity, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen).apply {
                            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                        }

                        val container = android.widget.LinearLayout(this@MainActivity).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.WHITE)
                        }

                        // Top bar with title and close button
                        val header = android.widget.RelativeLayout(this@MainActivity).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                (52 * resources.displayMetrics.density).toInt()
                            )
                            setBackgroundColor(android.graphics.Color.parseColor("#1B2A1E"))
                            setPadding(32, 0, 16, 0)
                        }

                        val titleView = android.widget.TextView(this@MainActivity).apply {
                            text = "Mera Thikaana"
                            setTextColor(android.graphics.Color.WHITE)
                            textSize = 15f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            val lp = android.widget.RelativeLayout.LayoutParams(
                                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                                addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
                            }
                            layoutParams = lp
                        }
                        header.addView(titleView)

                        val closeBtn = android.widget.TextView(this@MainActivity).apply {
                            text = "✕"
                            setTextColor(android.graphics.Color.WHITE)
                            textSize = 18f
                            setPadding(24, 12, 24, 12)
                            val lp = android.widget.RelativeLayout.LayoutParams(
                                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                                addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                            }
                            layoutParams = lp
                            setOnClickListener { popupDialog.dismiss() }
                        }
                        header.addView(closeBtn)
                        container.addView(header)

                        val progressBar = android.widget.ProgressBar(
                            this@MainActivity,
                            null,
                            android.R.attr.progressBarStyleHorizontal
                        ).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                (3 * resources.displayMetrics.density).toInt()
                            )
                            isIndeterminate = false
                            max = 100
                        }
                        container.addView(progressBar)

                        val popupWebView = WebView(this@MainActivity).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                0,
                                1.0f
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportMultipleWindows(true)
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.userAgentString = parentView.settings.userAgentString

                            val popupCookieManager = CookieManager.getInstance()
                            popupCookieManager.setAcceptCookie(true)
                            popupCookieManager.setAcceptThirdPartyCookies(this, true)

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    progressBar.progress = newProgress
                                    progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                                }

                                override fun onCloseWindow(window: WebView?) {
                                    super.onCloseWindow(window)
                                    try {
                                        popupDialog.dismiss()
                                        CookieManager.getInstance().flush()
                                        parentView.evaluateJavascript("if (typeof checkAuth === 'function') checkAuth();", null)
                                    } catch (_: Exception) {}
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val targetUrl = request?.url?.toString() ?: return false
                                    val uri = request.url

                                    // Handle WhatsApp links directly
                                    if (targetUrl.startsWith("whatsapp:") || targetUrl.startsWith("https://wa.me/") || targetUrl.startsWith("http://wa.me/") || targetUrl.startsWith("https://api.whatsapp.com/")) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                setPackage("com.whatsapp")
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            startActivity(intent)
                                            popupDialog.dismiss()
                                            return true
                                        } catch (e: Exception) {
                                            try {
                                                val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                startActivity(fallback)
                                                popupDialog.dismiss()
                                                return true
                                            } catch (_: Exception) {}
                                        }
                                    }

                                    // Handle external communications & custom intents
                                    if (targetUrl.startsWith("tel:") || targetUrl.startsWith("mailto:") || targetUrl.startsWith("sms:") || targetUrl.startsWith("geo:") || targetUrl.startsWith("market:")) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            startActivity(intent)
                                            popupDialog.dismiss()
                                            return true
                                        } catch (_: Exception) {}
                                    }

                                    if (targetUrl.startsWith("intent:")) {
                                        try {
                                            val intent = Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            startActivity(intent)
                                            popupDialog.dismiss()
                                            return true
                                        } catch (_: Exception) {}
                                    }

                                    if (targetUrl.contains("thikana.pages.dev")) {
                                        parentView.loadUrl(targetUrl)
                                        popupDialog.dismiss()
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (url?.contains("thikana.pages.dev") == true) {
                                        CookieManager.getInstance().flush()
                                        parentView.evaluateJavascript("if (typeof checkAuth === 'function') checkAuth();", null)
                                        popupDialog.dismiss()
                                    }
                                }
                            }
                        }
                        container.addView(popupWebView)
                        popupDialog.setContentView(container)
                        popupDialog.setOnDismissListener {
                            try {
                                popupWebView.destroy()
                            } catch (_: Exception) {}
                        }
                        popupDialog.show()

                        transport.webView = popupWebView
                        resultMsg.sendToTarget()
                        true
                    } else {
                        false
                    }
                }
            )
        }

        return webView
    }

    private fun loadInitialUrl(url: String = APP_URL) {
        if (!::webView.isInitialized) return
        val targetUrl = url.ifBlank { APP_URL }
        if (webView.isAttachedToWindow) {
            if (webView.url == null) {
                webView.loadUrl(targetUrl)
            }
        } else {
            webView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    webView.removeOnAttachStateChangeListener(this)
                    webView.post {
                        if (webView.url == null) {
                            webView.loadUrl(targetUrl)
                        }
                    }
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
    }

    private fun recreateWebView() {
        try {
            if (::webView.isInitialized) {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
        } catch (_: Throwable) {}
        webView = createWebView()
        webViewInstanceState.value = webView
        loadInitialUrl(lastKnownUrl.ifBlank { APP_URL })
    }

    override fun onResume() {
        super.onResume()
        if (LocationHelper.isLocationServicesEnabled(this)) {
            isLocationPromptOpenState.value = false
            if (::sensorBridge.isInitialized) {
                sensorBridge.startLocationUpdates()
            }
        }
        if (::sensorBridge.isInitialized) {
            sensorBridge.startTracking()
        }
        if (::webView.isInitialized) {
            webView.onResume()
        }
        com.example.notifications.ThikanaNotificationSyncManager.syncNow(this)
        ThikanaFirebaseMessagingService.getSavedToken(this)?.let { token ->
            notifyFcmTokenUpdated(token)
        }
    }

    override fun onPause() {
        if (::voiceController.isInitialized) {
            voiceController.stopListening()
            voiceController.stopSpeaking()
        }
        if (::sensorBridge.isInitialized) {
            sensorBridge.stopTracking()
        }
        if (::webView.isInitialized) {
            webView.onPause()
        }
        try {
            android.webkit.CookieManager.getInstance().flush()
            com.example.notifications.ThikanaNotificationSyncManager.scheduleBackgroundAlarm(this)
        } catch (_: Exception) {}
        super.onPause()
    }

    override fun onDestroy() {
        if (currentInstance == this) {
            currentInstance = null
        }
        ThikanaNavigationService.stopNavigation(this)
        ThikanaMusicService.commandListener = null
        if (::navVoiceManager.isInitialized) {
            navVoiceManager.destroy()
        }
        if (::voiceController.isInitialized) {
            voiceController.destroy()
        }
        if (::sensorBridge.isInitialized) {
            sensorBridge.stopTracking()
        }
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null || !intent.getBooleanExtra("from_fcm", false)) return

        if (!::webView.isInitialized || !isPageFinishedLoading) {
            pendingNotificationIntent = intent
            return
        }

        val threadId = intent.getStringExtra("thread_id")
        val postId = intent.getStringExtra("post_id")
        val placeId = intent.getStringExtra("place_id")
        val url = intent.getStringExtra("url") ?: intent.dataString
        val type = intent.getStringExtra("type")

        runOnUiThread {
            if (!::webView.isInitialized) return@runOnUiThread
            when {
                !url.isNullOrBlank() -> {
                    webView.loadUrl(url)
                }
                !placeId.isNullOrBlank() -> {
                    webView.evaluateJavascript(
                        "if (typeof window.jumpToPlace === 'function') { window.jumpToPlace('$placeId'); }",
                        null
                    )
                }
                !threadId.isNullOrBlank() -> {
                    webView.evaluateJavascript(
                        "if (typeof window.openThread === 'function') { window.openThread('$threadId'); }",
                        null
                    )
                }
                !postId.isNullOrBlank() -> {
                    webView.evaluateJavascript(
                        "if (typeof window.openPostMenu === 'function') { window.openPostMenu('$postId'); }",
                        null
                    )
                }
                type == "chat" || type == "message" -> {
                    webView.evaluateJavascript(
                        "if (typeof window.openMessages === 'function') { window.openMessages(); }",
                        null
                    )
                }
                else -> {
                    webView.evaluateJavascript(
                        "if (typeof window.openNotifications === 'function') { window.openNotifications(); }",
                        null
                    )
                }
            }
        }
    }

    fun notifyFcmTokenUpdated(token: String) {
        if (!::webView.isInitialized) return
        runOnUiThread {
            try {
                val js = """
                    (function() {
                        try {
                            window.androidFcmToken = '$token';
                            if (typeof window.syncNativeFcmToken === 'function') {
                                window.syncNativeFcmToken('$token');
                            }
                            if (typeof window.onFcmTokenReceived === 'function') {
                                window.onFcmTokenReceived('$token');
                            }
                        } catch(e) {}
                    })();
                """.trimIndent()
                webView.evaluateJavascript(js, null)
            } catch (_: Exception) {}
        }
    }

    fun onRemotePushMessageReceived(title: String, body: String, data: Map<String, String>) {
        if (!::webView.isInitialized) return
        runOnUiThread {
            try {
                val dataJson = org.json.JSONObject(data as Map<*, *>).toString()
                val js = """
                    (function() {
                        try {
                            if (typeof window.onRemotePushNotification === 'function') {
                                window.onRemotePushNotification({
                                    title: ${org.json.JSONObject.quote(title)},
                                    body: ${org.json.JSONObject.quote(body)},
                                    data: $dataJson
                                });
                            }
                        } catch(e) {}
                    })();
                """.trimIndent()
                webView.evaluateJavascript(js, null)
            } catch (_: Exception) {}
        }
    }

}
