package com.example.storyeditor.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.widget.Toast
import com.example.storyeditor.model.FilterPreset
import com.example.storyeditor.util.ImageProcessingUtils
import com.example.ui.theme.InstagramStoryGradient
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

@Composable
fun SnapchatCameraView(
    onPhotoCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit,
    onNavigateToCollage: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Photo picker launcher for picking existing gallery images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        onPhotoCaptured(bitmap)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var cameraLensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var showGrid by remember { mutableStateOf(false) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var isCapturing by remember { mutableStateOf(false) }
    var selectedLiveLens by remember { mutableStateOf(FilterPreset.ORIGINAL) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode)
            .build()
    }

    LaunchedEffect(hasCameraPermission, cameraLensFacing, previewView) {
        if (!hasCameraPermission) return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProviderRef = cameraProvider
                cameraProvider.unbindAll()

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraLensFacing)
                    .build()

                cameraInstance = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraProviderRef?.unbindAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("snapchat_camera_screen")
    ) {
        if (hasCameraPermission) {
            // Live Camera Viewfinder with pinch to zoom
            AndroidView(
                factory = {
                    (previewView.parent as? ViewGroup)?.removeView(previewView)
                    previewView
                },
                update = { view ->
                    imageCapture.flashMode = flashMode
                    if (selectedLiveLens == FilterPreset.ORIGINAL) {
                        view.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                    } else {
                        try {
                            val cm = selectedLiveLens.getColorMatrix()
                            val androidCm = android.graphics.ColorMatrix(cm.values)
                            val paint = android.graphics.Paint().apply {
                                colorFilter = android.graphics.ColorMatrixColorFilter(androidCm)
                            }
                            view.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val newZoom = (zoomRatio * zoom).coerceIn(1f, 5f)
                            zoomRatio = newZoom
                            cameraInstance?.cameraControl?.setZoomRatio(newZoom)
                        }
                    }
            )
        } else {
            // Fallback when camera permission not granted or emulator camera unavailable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF14141E)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Camera",
                        tint = StudioCyan,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Access",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Enable camera permission or pick an aesthetic sample / gallery photo to start editing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA0A0B0),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(InstagramStoryGradient)
                                .clickable {
                                    permissionLauncher.launch(android.Manifest.permission.CAMERA)
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text("Grant Permission", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF262638))
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text("Open Gallery", color = Color.White)
                        }
                    }
                }
            }
        }

        // Live Lens Color Overlay Tint fallback
        if (selectedLiveLens != FilterPreset.ORIGINAL) {
            val tintColor = when (selectedLiveLens) {
                FilterPreset.CYBER_NEON -> Color(0x3500E5FF)
                FilterPreset.GOLDEN_HOUR -> Color(0x30FFA726)
                FilterPreset.FILM_35MM -> Color(0x28FFE082)
                FilterPreset.NOIR -> Color(0x60000000)
                FilterPreset.PASTEL -> Color(0x30F48FB1)
                FilterPreset.TEAL_ORANGE -> Color(0x3000B4D8)
                FilterPreset.RETRO_90S -> Color(0x309C27B0)
                FilterPreset.MOODY -> Color(0x381A237E)
                FilterPreset.WARMTH -> Color(0x30FF6F00)
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tintColor)
            )
        }

        // Rule of thirds grid lines
        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stepX = size.width / 3f
                val stepY = size.height / 3f
                val strokeColor = Color.White.copy(alpha = 0.3f)

                drawLine(strokeColor, Offset(stepX, 0f), Offset(stepX, size.height), strokeWidth = 1.dp.toPx())
                drawLine(strokeColor, Offset(stepX * 2, 0f), Offset(stepX * 2, size.height), strokeWidth = 1.dp.toPx())
                drawLine(strokeColor, Offset(0f, stepY), Offset(size.width, stepY), strokeWidth = 1.dp.toPx())
                drawLine(strokeColor, Offset(0f, stepY * 2), Offset(size.width, stepY * 2), strokeWidth = 1.dp.toPx())
            }
        }

        // Top Controls Header: Close on Left, Mode Switcher (Camera | Collage) in Center
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .testTag("close_camera_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close Camera", tint = Color.White)
            }

            // Top Mode Switcher: Camera (Active) vs Collage
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(InstagramStoryGradient)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Camera",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNavigateToCollage() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("mode_switch_to_collage")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Dashboard,
                            contentDescription = null,
                            tint = Color(0xFFA0A0B5),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Collage",
                            color = Color(0xFFA0A0B5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Balance space on the right so the mode switcher remains centered
            Spacer(modifier = Modifier.size(44.dp))
        }

        // Vertical Camera Quick Controls Rail (Snapchat & Instagram style)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 68.dp, end = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Switch Camera (Front/Back)
            IconButton(
                onClick = {
                    cameraLensFacing = if (cameraLensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .testTag("switch_camera_lens_button")
            ) {
                Icon(Icons.Default.Cameraswitch, contentDescription = "Flip Camera", tint = Color.White)
            }

            // Flash toggle
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .testTag("camera_flash_toggle_button")
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash mode",
                    tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) StudioPink else Color.White
                )
            }

            // Grid toggle
            IconButton(
                onClick = { showGrid = !showGrid },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (showGrid) StudioCyan.copy(alpha = 0.35f) else Color.Transparent)
                    .testTag("camera_grid_toggle_button")
            ) {
                Icon(
                    Icons.Default.GridOn,
                    contentDescription = "Toggle Grid",
                    tint = if (showGrid) StudioCyan else Color.White
                )
            }

            // Timer toggle (0, 3s, 10s)
            IconButton(
                onClick = {
                    timerSeconds = when (timerSeconds) {
                        0 -> 3
                        3 -> 10
                        else -> 0
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (timerSeconds > 0) StudioPink.copy(alpha = 0.35f) else Color.Transparent)
                    .testTag("camera_timer_toggle_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = "Timer",
                        tint = if (timerSeconds > 0) StudioPink else Color.White
                    )
                    if (timerSeconds > 0) {
                        Text(
                            text = "${timerSeconds}s",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioPink,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }
            }
        }

        // Countdown Timer Overlay
        AnimatedVisibility(
            visible = countdownRemaining > 0,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$countdownRemaining",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StudioPink
                )
            }
        }

        // Bottom Controls: Snapchat-Style Lens Carousel & Shutter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lens Carousel Selector (Snapchat Style)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item { Spacer(modifier = Modifier.width(32.dp)) }
                items(FilterPreset.entries) { preset ->
                    val isSelected = selectedLiveLens == preset
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedLiveLens = preset }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    when (preset) {
                                        FilterPreset.ORIGINAL -> Color(0xFF333344)
                                        FilterPreset.CYBER_NEON -> Color(0xFF00E5FF)
                                        FilterPreset.GOLDEN_HOUR -> Color(0xFFFFB300)
                                        FilterPreset.FILM_35MM -> Color(0xFF8D6E63)
                                        FilterPreset.NOIR -> Color(0xFF212121)
                                        FilterPreset.PASTEL -> Color(0xFFF48FB1)
                                        else -> Color(0xFF9C27B0)
                                    }
                                )
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.displayName.take(2).uppercase(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = preset.displayName,
                            color = if (isSelected) Color.White else Color(0xFFB0B0C0),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.width(32.dp)) }
            }

            // Bottom Shutter & Gallery Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Picker Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("camera_gallery_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "Pick from gallery",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Snapchat-style Large Glowing Shutter Button
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    StudioPink,
                                    StudioCyan,
                                    Color(0xFFFFB300),
                                    StudioPink
                                )
                            )
                        )
                        .clickable(enabled = !isCapturing) {
                            if (timerSeconds > 0) {
                                coroutineScope.launch {
                                    countdownRemaining = timerSeconds
                                    while (countdownRemaining > 0) {
                                        delay(1000)
                                        countdownRemaining--
                                    }
                                    capturePhoto(
                                        context = context,
                                        imageCapture = imageCapture,
                                        lensFacing = cameraLensFacing,
                                        selectedFilter = selectedLiveLens,
                                        onStart = { isCapturing = true },
                                        onSuccess = { bmp ->
                                            isCapturing = false
                                            onPhotoCaptured(bmp)
                                        },
                                        onError = {
                                            isCapturing = false
                                            Toast.makeText(context, "Could not capture photo", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            } else {
                                capturePhoto(
                                    context = context,
                                    imageCapture = imageCapture,
                                    lensFacing = cameraLensFacing,
                                    selectedFilter = selectedLiveLens,
                                    onStart = { isCapturing = true },
                                    onSuccess = { bmp ->
                                        isCapturing = false
                                        onPhotoCaptured(bmp)
                                    },
                                    onError = {
                                        isCapturing = false
                                        Toast.makeText(context, "Could not capture photo", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .testTag("snapchat_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = StudioPink,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, Color(0xFFE0E0E0), CircleShape)
                        )
                    }
                }

                // Gallery Picker Button (Replaces sample image button)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("camera_gallery_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "Pick from Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    lensFacing: Int,
    selectedFilter: FilterPreset,
    onStart: () -> Unit,
    onSuccess: (Bitmap) -> Unit,
    onError: () -> Unit
) {
    onStart()
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val buffer: ByteBuffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                image.close()

                if (bitmap != null) {
                    // Check rotation from imageProxy metadata
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    if (rotationDegrees != 0 || lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        val matrix = Matrix()
                        matrix.postRotate(rotationDegrees.toFloat())
                        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            // Mirror front camera
                            matrix.postScale(-1f, 1f)
                        }
                        bitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                        )
                    }
                    // Apply the selected live filter to the captured photo
                    if (selectedFilter != FilterPreset.ORIGINAL) {
                        bitmap = ImageProcessingUtils.applyFilterToBitmap(bitmap, selectedFilter)
                    }
                    onSuccess(bitmap)
                } else {
                    onError()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
                onError()
            }
        }
    )
}
