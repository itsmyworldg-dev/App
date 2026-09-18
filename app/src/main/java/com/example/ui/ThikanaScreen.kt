package com.example.ui

import android.webkit.WebView
import com.example.MainActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.theme.ThikanaPink
import com.example.ui.theme.ThikanaViolet
import com.example.ui.theme.ThikanaPaper
import com.example.ui.skeleton.ThikanaSkeletonScreen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.example.ui.navigation.NavVoiceGuidanceHud

@Composable
fun ThikanaScreen(
    webView: WebView,
    loadingProgress: Int,
    isLoading: Boolean,
    errorMessage: String?,
    currentTab: ThikanaTab,
    showBottomNav: Boolean = true,
    isUploadChooserOpen: Boolean = false,
    messagesBadgeCount: Int = 0,
    vibesBadgeCount: Int = 0,
    discoverBadgeCount: Int = 0,
    isNavActive: Boolean = false,
    navInstruction: String = "",
    navDistance: String = "",
    navNextInstruction: String = "",
    navDestinationName: String = "",
    isNavMuted: Boolean = false,
    isNavSpeaking: Boolean = false,
    onTabSelected: (ThikanaTab) -> Unit,
    onRetry: () -> Unit,
    onOpenVoiceNav: () -> Unit = {},
    onRepeatNavInstruction: () -> Unit = {},
    onToggleNavMute: () -> Unit = {},
    onOpenNativeCamera: (String) -> Unit = {},
    navBarBottomInset: Dp = 0.dp,
    isInitialLoading: Boolean = false,
    // Kept for the CSS var plumbing described above (MainActivity pushes whatever
    // value comes through here into --native-dock-height). Always called with 0.dp
    // now that there's no native dock to measure.
    onDockHeightChanged: (Dp) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // There is no native floating dock anymore — the web app's own .navbar
    // (visible/hidden entirely by its own CSS and JS, exactly as it is in a
    // browser or installed PWA) is what's shown now. --native-dock-height
    // is kept pinned at 0 so every var(--native-dock-height, ...) fallback
    // in the WebView's CSS resolves the same way it already does on the
    // plain web, instead of reserving space for a dock that no longer exists.
    LaunchedEffect(Unit) {
        onDockHeightChanged(0.dp)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ThikanaPaper)
    ) {
        // Main Hybrid WebView - fills full container, no extra bottom gap
        key(webView) {
            AndroidView(
                factory = {
                    webView.apply {
                        (parent as? android.view.ViewGroup)?.removeView(this)
                        post {
                            if (url == null) {
                                loadUrl(MainActivity.APP_URL)
                            }
                        }
                    }
                },
                onRelease = { releasedView ->
                    (releasedView.parent as? android.view.ViewGroup)?.removeView(releasedView)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("app_webview")
            )
        }

        // Turn-by-Turn Spoken Navigation Guidance HUD (active route navigation)
        NavVoiceGuidanceHud(
            isNavActive = isNavActive,
            instruction = navInstruction,
            distance = navDistance,
            nextInstruction = navNextInstruction,
            destinationName = navDestinationName,
            isMuted = isNavMuted,
            isSpeaking = isNavSpeaking,
            onRepeatInstruction = onRepeatNavInstruction,
            onToggleMute = onToggleNavMute,
            modifier = Modifier.align(Alignment.TopCenter)
        )


        // Floating Gen Z Voice Navigation FAB (icon-only, no outer circle, 70% transparency)
        val voiceNavBottomClearance = if (navBarBottomInset > 0.dp) {
            navBarBottomInset + 68.dp
        } else {
            72.dp
        }

        AnimatedVisibility(
            visible = showBottomNav,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = voiceNavBottomClearance, end = 16.dp)
        ) {
            Surface(
                onClick = onOpenVoiceNav,
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("floating_vibenav_button")
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFFF2A85).copy(alpha = 0.70f),
                                        Color(0xFF8A2BE2).copy(alpha = 0.70f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "VibeNav Voice Navigation",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // The native "Instagram-style" Compose bottom dock that used to render here
        // has been removed in favor of the web app's own .navbar — the same dock
        // shown in a browser/installed PWA, which already works well and is now unhidden inside
        // the WebView (see ThikanaWebViewClient's DOCK_OVERLAY_CSS). currentTab/onTabSelected/
        // the various badge counts are kept as parameters for now (voice-nav commands and the
        // web→native tab-sync bridge still populate them) but no longer drive a rendered dock.

        // Sleek Gradient Top Loading Bar
        AnimatedVisibility(
            visible = isLoading && loadingProgress in 1..99,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .testTag("loading_bar"),
                color = ThikanaPink,
                trackColor = ThikanaViolet.copy(alpha = 0.2f)
            )
        }

        // Shimmering Skeleton Loading for Initial Cold Start (eliminates white screen)
        AnimatedVisibility(
            visible = isInitialLoading,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(durationMillis = 400)),
            modifier = Modifier.fillMaxSize()
        ) {
            ThikanaSkeletonScreen(
                navBarBottomInset = navBarBottomInset
            )
        }

        // Offline / Error Notice Overlay
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 72.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("offline_notice")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = "Mera Thikaana",
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connection Issue",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = errorMessage ?: "Showing offline cached data",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 2
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ThikanaViolet,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.testTag("retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Retry", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
