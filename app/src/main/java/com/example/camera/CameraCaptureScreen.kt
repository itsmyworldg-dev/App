package com.example.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.editor.CollageSlotData
import com.example.editor.CollageTemplate
import java.io.File
import java.util.concurrent.Executors

enum class CameraMode(val label: String) {
    STORY("STORY"),
    POST("POST"),
    COLLAGE("COLLAGE")
}

@Composable
fun CameraCaptureScreen(
    onDismiss: () -> Unit,
    onProceedToEditor: (List<Bitmap>, CameraMode, CollageTemplate) -> Unit,
    initialMode: CameraMode = CameraMode.POST,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var selectedMode by remember { mutableStateOf(initialMode) }
    var selectedCollageTemplate by remember { mutableStateOf(CollageTemplate.SPLIT_VERTICAL_2) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isCapturing by remember { mutableStateOf(false) }

    // Collage slots accumulator
    val collageBitmaps = remember { mutableStateListOf<Bitmap?>() }
    var activeSlotIndex by remember { mutableIntStateOf(0) }

    // Sync collage slot capacity
    LaunchedEffect(selectedCollageTemplate, selectedMode) {
        if (selectedMode == CameraMode.COLLAGE) {
            while (collageBitmaps.size < selectedCollageTemplate.slotCount) {
                collageBitmaps.add(null)
            }
            while (collageBitmaps.size > selectedCollageTemplate.slotCount) {
                collageBitmaps.removeLast()
            }
        }
    }

    // Gallery Picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            val loadedBitmaps = uris.mapNotNull { uri ->
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (loadedBitmaps.isNotEmpty()) {
                if (selectedMode == CameraMode.COLLAGE) {
                    loadedBitmaps.forEachIndexed { idx, bmp ->
                        val targetSlot = (activeSlotIndex + idx) % selectedCollageTemplate.slotCount
                        if (targetSlot < collageBitmaps.size) {
                            collageBitmaps[targetSlot] = bmp
                        }
                    }
                    val allFilled = collageBitmaps.all { it != null }
                    if (allFilled) {
                        onProceedToEditor(
                            collageBitmaps.filterNotNull(),
                            CameraMode.COLLAGE,
                            selectedCollageTemplate
                        )
                    }
                } else {
                    onProceedToEditor(
                        listOf(loadedBitmaps.first()),
                        selectedMode,
                        CollageTemplate.SINGLE
                    )
                }
            }
        }
    }

    // CameraX objects
    val imageCapture = remember {
        ImageCapture.Builder()
            .setFlashMode(flashMode)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
            } catch (e: Exception) {
                Log.e("CameraCapture", "Failed to obtain ProcessCameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Reactively rebind camera use cases when lensFacing, previewView, or cameraProvider updates
    LaunchedEffect(lensFacing, previewView, cameraProvider, lifecycleOwner, hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        val provider = cameraProvider ?: return@LaunchedEffect
        val view = previewView ?: return@LaunchedEffect

        val targetSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val usableSelector = if (provider.hasCamera(targetSelector)) {
            targetSelector
        } else {
            if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                null
            }
        }

        if (usableSelector == null) {
            Log.e("CameraCapture", "No usable camera found on device")
            return@LaunchedEffect
        }

        try {
            provider.unbindAll()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            provider.bindToLifecycle(
                lifecycleOwner,
                usableSelector,
                preview,
                imageCapture
            )
            Log.i("CameraCapture", "Successfully bound camera with lensFacing: $lensFacing")
        } catch (e: Exception) {
            Log.e("CameraCapture", "Failed to bind camera use cases for lens $lensFacing", e)
        }
    }

    DisposableEffect(cameraProvider) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {}
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Camera Permission",
                    tint = Color(0xFFFF3B5C),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Access Needed",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To capture native Instagram-style stories, posts, and collages, grant camera permission or pick photos from your gallery.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B5C)),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("Grant Camera Access", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("Choose from Gallery", color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Cancel",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(8.dp)
                )
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // CameraX Live Preview View
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Controls: Close, Flash, and Mode specifics
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Camera",
                    tint = Color.White
                )
            }

            // Flash mode toggle
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                val icon = when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                    else -> Icons.Default.FlashOff
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Toggle Flash",
                    tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) Color(0xFFFFD700) else Color.White
                )
            }
        }

        // Collage Mode: Template Selector & Active Slots Tray
        if (selectedMode == CameraMode.COLLAGE) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Template switcher pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    CollageTemplate.entries.filter { it != CollageTemplate.SINGLE }.forEach { template ->
                        val isSelected = template == selectedCollageTemplate
                        Surface(
                            onClick = { selectedCollageTemplate = template },
                            shape = RoundedCornerShape(100.dp),
                            color = if (isSelected) Color(0xFFFF3B5C) else Color.Black.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = template.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Slots miniature preview strip
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until selectedCollageTemplate.slotCount) {
                        val bmp = collageBitmaps.getOrNull(i)
                        val isSlotActive = activeSlotIndex == i
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSlotActive) 2.dp else 1.dp,
                                    color = if (isSlotActive) Color(0xFFFF3B5C) else Color.White.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { activeSlotIndex = i },
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Slot $i",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = "${i + 1}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Done/Next button if at least 1 slot has a photo
                    if (collageBitmaps.any { it != null }) {
                        Surface(
                            onClick = {
                                val filled = collageBitmaps.filterNotNull()
                                if (filled.isNotEmpty()) {
                                    onProceedToEditor(filled, CameraMode.COLLAGE, selectedCollageTemplate)
                                }
                            },
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFF00C853),
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = "Edit",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Camera Action Area: Gallery Picker, Shutter Button, Switch Camera
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Switcher (STORY / POST / COLLAGE)
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                CameraMode.entries.forEach { mode ->
                    val isSelected = mode == selectedMode
                    Text(
                        text = mode.label,
                        color = if (isSelected) Color(0xFFFFE082) else Color.White.copy(alpha = 0.6f),
                        fontSize = if (isSelected) 14.sp else 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clickable { selectedMode = mode }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Controls Row: Gallery thumbnail, Shutter, Flip Camera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "Open Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Shutter Button with animated ring
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFF3B5C), Color(0xFFFFA033))
                            )
                        )
                        .clickable(enabled = !isCapturing) {
                            isCapturing = true
                            val tempFile = File.createTempFile("camera_snap_", ".jpg", context.cacheDir)
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        isCapturing = false
                                        val rawBitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                                        if (rawBitmap != null) {
                                            val rotationDegrees = try {
                                                val exif = ExifInterface(tempFile.absolutePath)
                                                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                                                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                                    else -> 0f
                                                }
                                            } catch (_: Exception) { 0f }

                                            val matrix = Matrix().apply {
                                                if (rotationDegrees != 0f) postRotate(rotationDegrees)
                                                if (lensFacing == CameraSelector.LENS_FACING_FRONT) postScale(-1.0f, 1.0f)
                                            }
                                            val finalBitmap = if (!matrix.isIdentity) {
                                                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                                            } else rawBitmap

                                            if (selectedMode == CameraMode.COLLAGE) {
                                                if (activeSlotIndex < collageBitmaps.size) {
                                                    collageBitmaps[activeSlotIndex] = finalBitmap
                                                    // Auto advance to next empty slot
                                                    val nextEmpty = collageBitmaps.indexOfFirst { it == null }
                                                    if (nextEmpty != -1) {
                                                        activeSlotIndex = nextEmpty
                                                    } else {
                                                        // All filled, launch editor
                                                        onProceedToEditor(
                                                            collageBitmaps.filterNotNull(),
                                                            CameraMode.COLLAGE,
                                                            selectedCollageTemplate
                                                        )
                                                    }
                                                }
                                            } else {
                                                onProceedToEditor(
                                                    listOf(finalBitmap),
                                                    selectedMode,
                                                    CollageTemplate.SINGLE
                                                )
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isCapturing = false
                                        Log.e("CameraCapture", "Failed to capture image", exception)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                    }
                }

                // Switch Camera Lens (Front / Back)
                val switchRotation by animateFloatAsState(
                    targetValue = if (lensFacing == CameraSelector.LENS_FACING_FRONT) 180f else 0f,
                    label = "switch_camera_rotation"
                )
                IconButton(
                    onClick = {
                        val targetLens = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                        val selector = CameraSelector.Builder().requireLensFacing(targetLens).build()
                        val isAvailable = cameraProvider?.hasCamera(selector) ?: true
                        if (isAvailable) {
                            lensFacing = targetLens
                        } else {
                            Toast.makeText(
                                context,
                                if (targetLens == CameraSelector.LENS_FACING_FRONT) {
                                    "Front camera not available on this device"
                                } else {
                                    "Back camera not available on this device"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer { rotationZ = switchRotation }
                    )
                }
            }
        }
    }
}
