package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storyeditor.camera.SnapchatCameraView
import com.example.storyeditor.collage.CollageMakerScreen
import com.example.storyeditor.contract.StoryEditorContract
import com.example.storyeditor.editor.StoryEditorScreen
import com.example.ui.theme.InstagramStoryGradient
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioPink

enum class AppScreen {
    CAMERA,
    COLLAGE,
    EDITOR
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialUriString = intent.getStringExtra(StoryEditorContract.EXTRA_INITIAL_URI)
        val initialMode = intent.getStringExtra(StoryEditorContract.EXTRA_INITIAL_MODE)

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                var currentScreen by remember {
                    mutableStateOf(
                        if (initialMode == "COLLAGE") AppScreen.COLLAGE
                        else AppScreen.CAMERA
                    )
                }

                var editorBitmap by remember {
                    mutableStateOf<Bitmap?>(null)
                }

                // Check if an initial URI was passed via Intent
                LaunchedEffect(initialUriString) {
                    if (!initialUriString.isNullOrBlank()) {
                        try {
                            val uri = Uri.parse(initialUriString)
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val bmp = BitmapFactory.decodeStream(stream)
                                if (bmp != null) {
                                    editorBitmap = bmp
                                    currentScreen = AppScreen.EDITOR
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
                            when (screen) {
                                AppScreen.CAMERA -> {
                                    SnapchatCameraView(
                                        onPhotoCaptured = { bitmap ->
                                            editorBitmap = bitmap
                                            currentScreen = AppScreen.EDITOR
                                        },
                                        onClose = {
                                            // Load a sample photo or exit
                                            val sample = BitmapFactory.decodeResource(
                                                context.resources,
                                                R.drawable.sample_portrait
                                            )
                                            if (sample != null) {
                                                editorBitmap = sample
                                                currentScreen = AppScreen.EDITOR
                                            }
                                        },
                                        onNavigateToCollage = {
                                            currentScreen = AppScreen.COLLAGE
                                        }
                                    )
                                }
                                AppScreen.COLLAGE -> {
                                    CollageMakerScreen(
                                        onCollageReadyForEditor = { renderedCollage ->
                                            editorBitmap = renderedCollage
                                            currentScreen = AppScreen.EDITOR
                                        },
                                        onBack = { currentScreen = AppScreen.CAMERA }
                                    )
                                }
                                AppScreen.EDITOR -> {
                                    val safeBitmap = editorBitmap ?: remember {
                                        BitmapFactory.decodeResource(
                                            context.resources,
                                            R.drawable.sample_portrait
                                        )
                                    }
                                    StoryEditorScreen(
                                        initialBitmap = safeBitmap,
                                        onBack = { currentScreen = AppScreen.CAMERA }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
