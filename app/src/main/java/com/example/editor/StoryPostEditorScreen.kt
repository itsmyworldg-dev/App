package com.example.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.CameraMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class EditorToolTab {
    NONE,
    FILTERS,
    ADJUST,
    COLLAGE,
    GEMINI_AI,
    DOODLE,
    STICKERS
}

@Composable
fun StoryPostEditorScreen(
    initialBitmaps: List<Bitmap>,
    initialMode: CameraMode,
    initialTemplate: CollageTemplate,
    onDismiss: () -> Unit,
    onPostCreated: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Mode state: Story (9:16) or Post (1:1)
    var isStoryRatio by remember { mutableStateOf(initialMode == CameraMode.STORY || initialMode == CameraMode.COLLAGE) }

    // Collage state
    var currentTemplate by remember { mutableStateOf(initialTemplate) }
    val collageSlots = remember {
        mutableStateListOf<CollageSlotData>().apply {
            val count = maxOf(initialTemplate.slotCount, initialBitmaps.size).coerceAtLeast(1)
            for (i in 0 until count) {
                add(CollageSlotData(i, initialBitmaps.getOrNull(i)))
            }
        }
    }
    var activeCollageSlotIndex by remember { mutableIntStateOf(0) }

    // Single active working bitmap (for filters, AI edits, etc.)
    var workingBitmap by remember {
        mutableStateOf(initialBitmaps.firstOrNull() ?: Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))
    }
    var originalBitmapForCompare by remember { mutableStateOf(workingBitmap) }

    // Keep workingBitmap in sync with slot 0 if in single mode
    LaunchedEffect(workingBitmap) {
        if (collageSlots.isNotEmpty() && currentTemplate == CollageTemplate.SINGLE) {
            collageSlots[0] = collageSlots[0].copy(bitmap = workingBitmap)
        }
    }

    // Active tool tab
    var activeTab by remember { mutableStateOf(EditorToolTab.NONE) }

    // Filter & Adjustment state
    var selectedFilterPreset by remember { mutableStateOf(FilterPreset.NORMAL) }
    var adjustments by remember { mutableStateOf(ImageAdjustments()) }

    // Doodle state
    val doodleStrokes = remember { mutableStateListOf<DrawnStroke>() }
    var doodleColor by remember { mutableStateOf(Color(0xFFFF3B5C)) }
    var doodleStrokeWidth by remember { mutableFloatStateOf(8f) }

    // Badges / Stickers state
    val activeBadges = remember { mutableStateListOf<BadgeSticker>() }

    // Text Stickers state
    val activeTextStickers = remember { mutableStateListOf<TextSticker>() }
    var isTextEditorOpen by remember { mutableStateOf(false) }
    var newTextContent by remember { mutableStateOf("") }
    var newTextColor by remember { mutableStateOf(Color.White) }
    var newTextHasBg by remember { mutableStateOf(true) }

    // Gemini AI states
    var isGeminiLoading by remember { mutableStateOf(false) }
    var geminiStatusMessage by remember { mutableStateOf("") }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var customApiKeyInput by remember { mutableStateOf("") }
    var showCustomPromptDialog by remember { mutableStateOf(false) }
    var customPromptText by remember { mutableStateOf("") }

    // Exporting progress
    var isExporting by remember { mutableStateOf(false) }

    // Gallery launcher to replace slot photo
    val slotImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                    if (bmp != null && activeCollageSlotIndex < collageSlots.size) {
                        collageSlots[activeCollageSlotIndex] = collageSlots[activeCollageSlotIndex].copy(bitmap = bmp)
                        if (activeCollageSlotIndex == 0) {
                            workingBitmap = bmp
                            originalBitmapForCompare = bmp
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Trigger Gemini AI generation
    fun runGeminiEdit(prompt: String) {
        val effectiveKey = GeminiStoryEngine.getEffectiveApiKey(context)
        if (effectiveKey.isBlank()) {
            showApiKeyDialog = true
            return
        }

        scope.launch {
            isGeminiLoading = true
            geminiStatusMessage = "Gemini AI is transforming your image in seconds... ✨"
            val targetBmp = if (currentTemplate == CollageTemplate.SINGLE) {
                workingBitmap
            } else {
                collageSlots.getOrNull(activeCollageSlotIndex)?.bitmap ?: workingBitmap
            }

            val result = GeminiStoryEngine.editImageWithGemini(
                context = context,
                sourceBitmap = targetBmp,
                prompt = prompt,
                isStoryRatio = isStoryRatio
            )

            isGeminiLoading = false
            result.onSuccess { aiBitmap ->
                originalBitmapForCompare = targetBmp
                if (currentTemplate == CollageTemplate.SINGLE) {
                    workingBitmap = aiBitmap
                    collageSlots[0] = collageSlots[0].copy(bitmap = aiBitmap)
                } else {
                    if (activeCollageSlotIndex < collageSlots.size) {
                        collageSlots[activeCollageSlotIndex] = collageSlots[activeCollageSlotIndex].copy(bitmap = aiBitmap)
                    }
                }
                Toast.makeText(context, "Gemini edit applied! ✨", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "AI Edit failed: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Save or Share action
    fun performExport(action: String) {
        scope.launch {
            isExporting = true
            val finalBmp = ImageSaveUtil.renderFinalBitmap(
                context = context,
                template = currentTemplate,
                slots = collageSlots.toList(),
                basePreset = selectedFilterPreset,
                adjustments = adjustments,
                strokes = doodleStrokes.toList(),
                stickers = activeBadges.toList(),
                textStickers = activeTextStickers.toList(),
                isStoryRatio = isStoryRatio
            )

            when (action) {
                "gallery" -> {
                    val savedUri = ImageSaveUtil.saveBitmapToGallery(context, finalBmp)
                    isExporting = false
                    if (savedUri != null) {
                        Toast.makeText(context, "Saved to Gallery! 📸", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                    }
                }
                "share" -> {
                    val cacheUri = ImageSaveUtil.saveBitmapToCache(context, finalBmp)
                    isExporting = false
                    ImageSaveUtil.launchShareIntent(context, cacheUri, "My Story on Mera Thikaana ✨")
                }
                "post" -> {
                    val cacheUri = ImageSaveUtil.saveBitmapToCache(context, finalBmp)
                    isExporting = false
                    onPostCreated(cacheUri)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- TOP BAR: Back, Ratio, Doodle, Text, Badges, Gemini Magic Button ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Discard",
                        tint = Color.White
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ratio Switcher (Story 9:16 vs Post 1:1)
                    Surface(
                        onClick = { isStoryRatio = !isStoryRatio },
                        shape = RoundedCornerShape(100.dp),
                        color = Color.Black.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (isStoryRatio) "9:16 STORY" else "1:1 POST",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    // ✨ Gemini AI Fast Edit Trigger (Pill with vibrant Instagram sunset gradient)
                    Surface(
                        onClick = {
                            activeTab = if (activeTab == EditorToolTab.GEMINI_AI) EditorToolTab.NONE else EditorToolTab.GEMINI_AI
                        },
                        shape = RoundedCornerShape(100.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF3B5C), Color(0xFFFFA033), Color(0xFF8E24AA))
                                )
                            )
                            .testTag("gemini_ai_magic_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Gemini AI Edits",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Gemini AI",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Text Tool (Aa)
                    IconButton(
                        onClick = { isTextEditorOpen = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatColorText,
                            contentDescription = "Add Text",
                            tint = Color.White
                        )
                    }

                    // Stickers Tool
                    IconButton(
                        onClick = {
                            activeTab = if (activeTab == EditorToolTab.STICKERS) EditorToolTab.NONE else EditorToolTab.STICKERS
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (activeTab == EditorToolTab.STICKERS) Color(0xFFFF3B5C) else Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mood,
                            contentDescription = "Add Stickers",
                            tint = Color.White
                        )
                    }

                    // Doodle Tool
                    IconButton(
                        onClick = {
                            activeTab = if (activeTab == EditorToolTab.DOODLE) EditorToolTab.NONE else EditorToolTab.DOODLE
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (activeTab == EditorToolTab.DOODLE) Color(0xFFFF3B5C) else Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Brush,
                            contentDescription = "Doodle",
                            tint = Color.White
                        )
                    }
                }
            }

            // --- MAIN CANVAS AREA (Preview, Filter, Doodle, Text, Stickers) ---
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val canvasWidth = maxWidth
                val canvasHeight = maxHeight
                val targetAspect = if (isStoryRatio) 9f / 16f else 1f

                Box(
                    modifier = Modifier
                        .aspectRatio(targetAspect)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                ) {
                    val combinedMatrix = adjustments.buildCombinedMatrix(selectedFilterPreset)
                    val colorFilter = ColorFilter.colorMatrix(combinedMatrix)

                    // 1. Photo / Collage Layer
                    CollageCanvas(
                        template = currentTemplate,
                        slots = collageSlots.toList(),
                        activeSlotIndex = activeCollageSlotIndex,
                        onSlotClicked = { slotIdx ->
                            activeCollageSlotIndex = slotIdx
                            slotImagePickerLauncher.launch("image/*")
                        },
                        colorFilter = colorFilter,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 2. Doodle Freehand Drawing Layer
                    DoodleCanvas(
                        strokes = doodleStrokes.toList(),
                        currentStrokeColor = doodleColor,
                        currentStrokeWidth = doodleStrokeWidth,
                        isDoodleModeEnabled = activeTab == EditorToolTab.DOODLE,
                        onStrokeFinished = { stroke ->
                            doodleStrokes.add(stroke)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 3. Badges / Story Stickers Layer (draggable)
                    activeBadges.forEachIndexed { idx, badge ->
                        DraggableBadgeItem(
                            badge = badge,
                            onPositionChanged = { newX, newY ->
                                activeBadges[idx] = badge.copy(offsetXFraction = newX, offsetYFraction = newY)
                            },
                            onDelete = {
                                activeBadges.removeAt(idx)
                            }
                        )
                    }

                    // 4. Text Stickers Layer (draggable)
                    activeTextStickers.forEachIndexed { idx, textSticker ->
                        DraggableTextItem(
                            textSticker = textSticker,
                            onPositionChanged = { newX, newY ->
                                activeTextStickers[idx] = textSticker.copy(offsetXFraction = newX, offsetYFraction = newY)
                            },
                            onDelete = {
                                activeTextStickers.removeAt(idx)
                            }
                        )
                    }
                }
            }

            // --- SECONDARY TOOL DRAWER (Collage, Filters, Adjust, Gemini AI Prompts, Doodle Palette) ---
            AnimatedVisibility(
                visible = activeTab != EditorToolTab.NONE,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (activeTab) {
                        EditorToolTab.FILTERS -> {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Instagram Filters",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    FilterPreset.entries.forEach { preset ->
                                        val isSelected = preset == selectedFilterPreset
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clickable { selectedFilterPreset = preset }
                                                .padding(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) Color(0xFFFF3B5C) else Color.White.copy(alpha = 0.3f),
                                                        shape = RoundedCornerShape(12.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Image(
                                                    bitmap = workingBitmap.asImageBitmap(),
                                                    contentDescription = preset.displayName,
                                                    contentScale = ContentScale.Crop,
                                                    colorFilter = ColorFilter.colorMatrix(preset.toComposeColorMatrix()),
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = preset.displayName,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color(0xFFFF3B5C) else Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        EditorToolTab.ADJUST -> {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = "Photo Adjustments",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                AdjustmentSlider(
                                    label = "Brightness",
                                    value = adjustments.brightness,
                                    valueRange = -80f..80f,
                                    onValueChange = { adjustments = adjustments.copy(brightness = it) }
                                )
                                AdjustmentSlider(
                                    label = "Contrast",
                                    value = adjustments.contrast,
                                    valueRange = 0.6f..1.6f,
                                    onValueChange = { adjustments = adjustments.copy(contrast = it) }
                                )
                                AdjustmentSlider(
                                    label = "Warmth",
                                    value = adjustments.warmth,
                                    valueRange = -40f..40f,
                                    onValueChange = { adjustments = adjustments.copy(warmth = it) }
                                )
                            }
                        }

                        EditorToolTab.COLLAGE -> {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Story Collage Layouts",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    CollageTemplate.entries.forEach { template ->
                                        val isSelected = template == currentTemplate
                                        Surface(
                                            onClick = {
                                                currentTemplate = template
                                                while (collageSlots.size < template.slotCount) {
                                                    collageSlots.add(CollageSlotData(collageSlots.size, null))
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) Color(0xFFFF3B5C) else Color(0xFF2A2A2A),
                                            border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.2f))
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.GridView,
                                                    contentDescription = template.displayName,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = template.displayName,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        EditorToolTab.GEMINI_AI -> {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "✨ Instant Gemini AI Story Edits",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFE082)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Compare / Revert to Original button
                                        TextButton(
                                            onClick = {
                                                workingBitmap = originalBitmapForCompare
                                                if (collageSlots.isNotEmpty()) {
                                                    collageSlots[activeCollageSlotIndex] = collageSlots[activeCollageSlotIndex].copy(bitmap = originalBitmapForCompare)
                                                }
                                                Toast.makeText(context, "Reverted to original", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text("Revert", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                                        }

                                        // API Key setting button
                                        IconButton(
                                            onClick = { showApiKeyDialog = true },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = "Configure Gemini Key",
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "Tap any style below to transform your story with Gemini in seconds:",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // Curated Prompts Carousel
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    // Custom Prompt card
                                    Surface(
                                        onClick = { showCustomPromptDialog = true },
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF2C223B),
                                        border = BorderStroke(1.dp, Color(0xFFE040FB)),
                                        modifier = Modifier.width(130.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text("✏️ Custom AI", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Type your own prompt...", fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
                                        }
                                    }

                                    // Pre-crafted best story prompts
                                    GeminiStoryEngine.curatedPrompts.forEach { promptItem ->
                                        Surface(
                                            onClick = { runGeminiEdit(promptItem.prompt) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color(0xFF262626),
                                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.2f)),
                                            modifier = Modifier.width(140.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(promptItem.emoji, fontSize = 16.sp)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = promptItem.title,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Color.White
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = promptItem.subtitle,
                                                    fontSize = 9.sp,
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    maxLines = 2
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        EditorToolTab.DOODLE -> {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Freehand Brush & Colors", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    TextButton(
                                        onClick = {
                                            if (doodleStrokes.isNotEmpty()) {
                                                doodleStrokes.removeLast()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo stroke", tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Undo", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // Colors row
                                val palette = listOf(
                                    Color.White,
                                    Color(0xFFFF3B5C),
                                    Color(0xFFFFA033),
                                    Color(0xFFFFEB3B),
                                    Color(0xFF00E676),
                                    Color(0xFF00E5FF),
                                    Color(0xFFE040FB),
                                    Color.Black
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    palette.forEach { col ->
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(col)
                                                .border(
                                                    width = if (doodleColor == col) 2.5.dp else 1.dp,
                                                    color = if (doodleColor == col) Color(0xFFFFD700) else Color.White.copy(alpha = 0.4f),
                                                    shape = CircleShape
                                                )
                                                .clickable { doodleColor = col }
                                        )
                                    }
                                }
                            }
                        }

                        EditorToolTab.STICKERS -> {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Add Story Badges & Emojis", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    StoryStickersCatalog.getAvailableBadges().forEach { badge ->
                                        Surface(
                                            onClick = {
                                                activeBadges.add(badge.copy(id = java.util.UUID.randomUUID().toString()))
                                                activeTab = EditorToolTab.NONE
                                                Toast.makeText(context, "Sticker added! Drag to move", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(100.dp),
                                            color = Color.White,
                                            shadowElevation = 4.dp
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(badge.iconEmoji, fontSize = 16.sp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(badge.title, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }

            // --- BOTTOM PRIMARY NAVIGATION BAR (Filters, Adjust, Collage, Gemini, Save, Share, Post) ---
            Surface(
                color = Color(0xFF141414),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    // Quick Action Category Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EditorQuickTabButton(
                            icon = Icons.Default.ColorLens,
                            label = "Filters",
                            isSelected = activeTab == EditorToolTab.FILTERS,
                            onClick = { activeTab = if (activeTab == EditorToolTab.FILTERS) EditorToolTab.NONE else EditorToolTab.FILTERS }
                        )
                        EditorQuickTabButton(
                            icon = Icons.Default.Tune,
                            label = "Adjust",
                            isSelected = activeTab == EditorToolTab.ADJUST,
                            onClick = { activeTab = if (activeTab == EditorToolTab.ADJUST) EditorToolTab.NONE else EditorToolTab.ADJUST }
                        )
                        EditorQuickTabButton(
                            icon = Icons.Default.GridView,
                            label = "Collage",
                            isSelected = activeTab == EditorToolTab.COLLAGE,
                            onClick = { activeTab = if (activeTab == EditorToolTab.COLLAGE) EditorToolTab.NONE else EditorToolTab.COLLAGE }
                        )
                        EditorQuickTabButton(
                            icon = Icons.Default.AutoAwesome,
                            label = "Gemini",
                            isSelected = activeTab == EditorToolTab.GEMINI_AI,
                            accent = Color(0xFFFFD700),
                            onClick = { activeTab = if (activeTab == EditorToolTab.GEMINI_AI) EditorToolTab.NONE else EditorToolTab.GEMINI_AI }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Final Action Buttons: Save to Gallery, Share, Post Story
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Save Button
                        Button(
                            onClick = { performExport("gallery") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", color = Color.White, fontSize = 12.sp)
                        }

                        // Share Button
                        Button(
                            onClick = { performExport("share") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", color = Color.White, fontSize = 12.sp)
                        }

                        // Post Story Button (Instagram Gradient)
                        Button(
                            onClick = { performExport("post") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .clip(RoundedCornerShape(100.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFF3B5C), Color(0xFFFFA033))
                                    )
                                )
                                .testTag("post_story_btn")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Post", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Your Story", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- GEMINI LOADING OVERLAY ---
        if (isGeminiLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF222222),
                    border = BorderStroke(1.dp, Color(0xFFFF3B5C)),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFF3B5C),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = geminiStatusMessage,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // --- EXPORTING LOADING OVERLAY ---
        if (isExporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFF3B5C))
            }
        }

        // --- TEXT STICKER CREATOR DIALOG ---
        if (isTextEditorOpen) {
            AlertDialog(
                onDismissRequest = { isTextEditorOpen = false },
                title = { Text("Add Text to Story", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newTextContent,
                            onValueChange = { newTextContent = it },
                            placeholder = { Text("Type something...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF3B5C),
                                focusedLabelColor = Color(0xFFFF3B5C)
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val colors = listOf(Color.White, Color(0xFFFF3B5C), Color(0xFFFFEB3B), Color(0xFF00E5FF), Color.Black)
                            colors.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            width = if (newTextColor == col) 2.dp else 1.dp,
                                            color = if (newTextColor == col) Color(0xFFFFD700) else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { newTextColor = col }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newTextContent.isNotBlank()) {
                                activeTextStickers.add(
                                    TextSticker(
                                        text = newTextContent,
                                        textColor = newTextColor,
                                        backgroundColor = if (newTextHasBg) Color.Black.copy(alpha = 0.6f) else null
                                    )
                                )
                                newTextContent = ""
                            }
                            isTextEditorOpen = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B5C))
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isTextEditorOpen = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // --- CUSTOM GEMINI PROMPT DIALOG ---
        if (showCustomPromptDialog) {
            AlertDialog(
                onDismissRequest = { showCustomPromptDialog = false },
                title = { Text("✨ Custom Gemini AI Edit", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Describe how you want Gemini to transform or enhance this photo for your story:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customPromptText,
                            onValueChange = { customPromptText = it },
                            placeholder = { Text("e.g. Add magical fireworks in night sky...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val prompt = customPromptText.trim()
                            showCustomPromptDialog = false
                            if (prompt.isNotBlank()) {
                                runGeminiEdit(prompt)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B5C))
                    ) {
                        Text("Generate")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomPromptDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // --- GEMINI API KEY DIALOG ---
        if (showApiKeyDialog) {
            AlertDialog(
                onDismissRequest = { showApiKeyDialog = false },
                title = { Text("Gemini API Key", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Enter your Google Gemini API Key to enable instant 1-tap AI story generation. You can obtain a free key from Google AI Studio.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customApiKeyInput,
                            onValueChange = { customApiKeyInput = it },
                            placeholder = { Text("AIzaSy...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customApiKeyInput.isNotBlank()) {
                                GeminiStoryEngine.setCustomApiKey(context, customApiKeyInput)
                                Toast.makeText(context, "API Key saved! ✨", Toast.LENGTH_SHORT).show()
                            }
                            showApiKeyDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B5C))
                    ) {
                        Text("Save Key")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApiKeyDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun EditorQuickTabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    accent: Color = Color(0xFFFF3B5C),
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) accent else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) accent else Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun AdjustmentSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.width(75.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF3B5C),
                activeTrackColor = Color(0xFFFF3B5C)
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DraggableBadgeItem(
    badge: BadgeSticker,
    onPositionChanged: (Float, Float) -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(badge.offsetXFraction) }
    var offsetY by remember { mutableFloatStateOf(badge.offsetYFraction) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val parentW = constraints.maxWidth.toFloat()
        val parentH = constraints.maxHeight.toFloat()

        Surface(
            shape = RoundedCornerShape(100.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (offsetX * parentW - 80).roundToInt().coerceIn(0, (parentW - 160).toInt().coerceAtLeast(0)),
                        y = (offsetY * parentH - 24).roundToInt().coerceIn(0, (parentH - 60).toInt().coerceAtLeast(0))
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x / parentW).coerceIn(0.1f, 0.9f)
                        offsetY = (offsetY + dragAmount.y / parentH).coerceIn(0.1f, 0.9f)
                        onPositionChanged(offsetX, offsetY)
                    }
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(badge.iconEmoji, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(badge.title, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onDelete() }
                )
            }
        }
    }
}

@Composable
fun DraggableTextItem(
    textSticker: TextSticker,
    onPositionChanged: (Float, Float) -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(textSticker.offsetXFraction) }
    var offsetY by remember { mutableFloatStateOf(textSticker.offsetYFraction) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val parentW = constraints.maxWidth.toFloat()
        val parentH = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (offsetX * parentW - 60).roundToInt().coerceIn(0, (parentW - 120).toInt().coerceAtLeast(0)),
                        y = (offsetY * parentH - 20).roundToInt().coerceIn(0, (parentH - 50).toInt().coerceAtLeast(0))
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x / parentW).coerceIn(0.1f, 0.9f)
                        offsetY = (offsetY + dragAmount.y / parentH).coerceIn(0.1f, 0.9f)
                        onPositionChanged(offsetX, offsetY)
                    }
                }
                .clip(RoundedCornerShape(8.dp))
                .background(textSticker.backgroundColor ?: Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = textSticker.text,
                    color = textSticker.textColor,
                    fontSize = textSticker.fontSizeSp.sp,
                    fontWeight = if (textSticker.isBold) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = textSticker.textColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onDelete() }
                )
            }
        }
    }
}
