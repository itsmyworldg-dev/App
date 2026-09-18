package com.example.storyeditor.editor

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.IntegrationInstructions
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storyeditor.ai.AiEditResult
import com.example.storyeditor.ai.GeminiEditorService
import com.example.storyeditor.contract.StoryEditorContract
import com.example.storyeditor.guide.IntegrationGuideDialog
import com.example.storyeditor.model.Adjustments
import com.example.storyeditor.model.AspectPreset
import com.example.storyeditor.model.BackgroundCutoutMode
import com.example.storyeditor.model.FilterPreset
import com.example.storyeditor.model.MusicTrack
import com.example.storyeditor.model.StoryEditorMusicBridge
import com.example.storyeditor.model.StickerOverlayItem
import com.example.storyeditor.model.TextOverlayItem
import com.example.storyeditor.util.ImageProcessingUtils
import com.example.ui.theme.InstagramStoryGradient
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioPink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditorTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    FILTERS("Filters", Icons.Default.Filter),
    ADJUST("Adjust", Icons.Default.Tune),
    TRANSFORM("Crop", Icons.Default.Crop),
    TEXT_STICKERS("Text", Icons.Default.TextFields),
    CUTOUT("Cutout", Icons.Default.AutoAwesome),
    AI("Gemini AI", Icons.Default.AutoAwesome),
    MUSIC("Music", Icons.Default.MusicNote)
}

@Composable
fun StoryEditorScreen(
    initialBitmap: Bitmap,
    onBack: () -> Unit,
    onDone: ((Uri) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeBitmap by remember { mutableStateOf(initialBitmap) }
    var cutoutBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessingCutout by remember { mutableStateOf(false) }

    var aspectPreset by remember { mutableStateOf(AspectPreset.STORY_9_16) }
    var filterPreset by remember { mutableStateOf(FilterPreset.ORIGINAL) }
    var adjustments by remember { mutableStateOf(Adjustments()) }
    var cutoutMode by remember { mutableStateOf(BackgroundCutoutMode.ORIGINAL) }

    var textOverlays by remember { mutableStateOf(listOf<TextOverlayItem>()) }
    var stickerOverlays by remember { mutableStateOf(listOf<StickerOverlayItem>()) }

    var showAddTextDialog by remember { mutableStateOf(false) }
    var showIntegrationGuide by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(EditorTab.FILTERS) }

    var isSaving by remember { mutableStateOf(false) }
    var isAiLoading by remember { mutableStateOf(false) }
    var lastAiMessage by remember { mutableStateOf<String?>(null) }

    // Dynamic music tracks provided by host app via StoryEditorMusicBridge
    val musicTracks = remember { StoryEditorMusicBridge.availableTracks }
    var selectedMusicTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var showMusicBadge by remember { mutableStateOf(false) }
    var isPlayingMusicPreview by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("story_editor_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                // Aspect Ratio Quick Toggle Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF22222E))
                        .clickable {
                            aspectPreset = when (aspectPreset) {
                                AspectPreset.STORY_9_16 -> AspectPreset.POST_1_1
                                AspectPreset.POST_1_1 -> AspectPreset.PORTRAIT_4_5
                                AspectPreset.PORTRAIT_4_5 -> AspectPreset.LANDSCAPE_16_9
                                AspectPreset.LANDSCAPE_16_9 -> AspectPreset.STORY_9_16
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${aspectPreset.label} (${when (aspectPreset) {
                            AspectPreset.STORY_9_16 -> "9:16"
                            AspectPreset.POST_1_1 -> "1:1"
                            AspectPreset.PORTRAIT_4_5 -> "4:5"
                            AspectPreset.LANDSCAPE_16_9 -> "16:9"
                        }})",
                        color = StudioCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Integration Guide
                    IconButton(
                        onClick = { showIntegrationGuide = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22222E))
                    ) {
                        Icon(
                            Icons.Default.IntegrationInstructions,
                            contentDescription = "Integration Guide",
                            tint = StudioCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Save to Device / High-Res PNG
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isSaving = true
                                val uri = withContext(Dispatchers.IO) {
                                    ImageProcessingUtils.saveBitmapToCache(context, activeBitmap, "story_export")
                                }
                                isSaving = false
                                if (uri != null) {
                                    Toast.makeText(context, "Saved high-quality story!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22222E))
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = StudioPink, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = "Save Story", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Share to Instagram Story
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(InstagramStoryGradient)
                            .clickable {
                                coroutineScope.launch {
                                    val uri = withContext(Dispatchers.IO) {
                                        ImageProcessingUtils.saveBitmapToCache(context, activeBitmap, "instagram_story")
                                    }
                                    if (uri != null) {
                                        // Also return result to calling app if opened via ActivityResultContract
                                        val activity = context as? Activity
                                        val returnIntent = Intent().apply {
                                            putExtra(StoryEditorContract.EXTRA_RESULT_URI, uri)
                                            data = uri
                                        }
                                        activity?.setResult(Activity.RESULT_OK, returnIntent)

                                        ImageProcessingUtils.shareStory(context, uri)
                                    } else {
                                        Toast.makeText(context, "Failed to prepare story image", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("share_instagram_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Share", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (onDone != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF10B981))
                                .clickable {
                                    coroutineScope.launch {
                                        isSaving = true
                                        val uri = withContext(Dispatchers.IO) {
                                            ImageProcessingUtils.saveBitmapToCache(context, activeBitmap, "spot_photo_${System.currentTimeMillis()}")
                                        }
                                        isSaving = false
                                        if (uri != null) {
                                            onDone(uri)
                                        } else {
                                            Toast.makeText(context, "Failed to prepare image", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("attach_spot_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Attach", tint = Color.White, modifier = Modifier.size(14.dp))
                                Text("Done", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Interactive Story Canvas (Main Viewport)
            StoryCanvas(
                bitmap = activeBitmap,
                cutoutBitmap = cutoutBitmap,
                aspectPreset = aspectPreset,
                filterPreset = filterPreset,
                adjustments = adjustments,
                cutoutMode = cutoutMode,
                textOverlays = textOverlays,
                stickerOverlays = stickerOverlays,
                selectedMusicTrack = selectedMusicTrack,
                showMusicBadge = showMusicBadge,
                onUpdateTextOverlay = { updated ->
                    textOverlays = textOverlays.map { if (it.id == updated.id) updated else it }
                },
                onDeleteTextOverlay = { id ->
                    textOverlays = textOverlays.filter { it.id != id }
                },
                onUpdateStickerOverlay = { updated ->
                    stickerOverlays = stickerOverlays.map { if (it.id == updated.id) updated else it }
                },
                onDeleteStickerOverlay = { id ->
                    stickerOverlays = stickerOverlays.filter { it.id != id }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Active Tool Tray Content (Filters, Adjust, Crop, Text/Stickers, Cutout, Gemini AI, Music)
            Surface(
                color = Color(0xFF14141E),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    when (activeTab) {
                        EditorTab.FILTERS -> {
                            FilterTray(
                                selectedFilter = filterPreset,
                                onFilterSelected = { filterPreset = it }
                            )
                        }
                        EditorTab.ADJUST -> {
                            AdjustTray(
                                adjustments = adjustments,
                                onAdjustmentsChanged = { adjustments = it },
                                onReset = { adjustments = Adjustments() }
                            )
                        }
                        EditorTab.TRANSFORM -> {
                            TransformTray(
                                selectedAspect = aspectPreset,
                                onAspectSelected = { aspectPreset = it },
                                onRotate90 = {
                                    activeBitmap = ImageProcessingUtils.rotateBitmap(activeBitmap, 90f)
                                    cutoutBitmap?.let {
                                        cutoutBitmap = ImageProcessingUtils.rotateBitmap(it, 90f)
                                    }
                                },
                                onFlipHorizontal = {
                                    activeBitmap = ImageProcessingUtils.flipBitmap(activeBitmap, horizontal = true, vertical = false)
                                    cutoutBitmap?.let {
                                        cutoutBitmap = ImageProcessingUtils.flipBitmap(it, horizontal = true, vertical = false)
                                    }
                                },
                                onFlipVertical = {
                                    activeBitmap = ImageProcessingUtils.flipBitmap(activeBitmap, horizontal = false, vertical = true)
                                    cutoutBitmap?.let {
                                        cutoutBitmap = ImageProcessingUtils.flipBitmap(it, horizontal = false, vertical = true)
                                    }
                                }
                            )
                        }
                        EditorTab.TEXT_STICKERS -> {
                            TextStickerTray(
                                onOpenAddText = { showAddTextDialog = true },
                                onAddSticker = { sticker ->
                                    stickerOverlays = stickerOverlays + sticker
                                }
                            )
                        }
                        EditorTab.CUTOUT -> {
                            BackgroundCutoutTray(
                                selectedMode = cutoutMode,
                                isProcessingCutout = isProcessingCutout,
                                hasExtractedCutout = cutoutBitmap != null,
                                onExtractSubject = {
                                    coroutineScope.launch {
                                        isProcessingCutout = true
                                        val cutout = withContext(Dispatchers.Default) {
                                            ImageProcessingUtils.extractSubjectCutout(activeBitmap)
                                        }
                                        cutoutBitmap = cutout
                                        isProcessingCutout = false
                                        if (cutoutMode == BackgroundCutoutMode.ORIGINAL) {
                                            cutoutMode = BackgroundCutoutMode.NEON_CYBER
                                        }
                                        Toast.makeText(context, "Subject extracted with high precision!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onModeSelected = { mode ->
                                    cutoutMode = mode
                                    if (cutoutBitmap == null && mode != BackgroundCutoutMode.ORIGINAL) {
                                        // Auto extract cutout if not done yet
                                        coroutineScope.launch {
                                            isProcessingCutout = true
                                            cutoutBitmap = withContext(Dispatchers.Default) {
                                                ImageProcessingUtils.extractSubjectCutout(activeBitmap)
                                            }
                                            isProcessingCutout = false
                                        }
                                    }
                                }
                            )
                        }
                        EditorTab.AI -> {
                            GeminiAiTray(
                                isLoading = isAiLoading,
                                lastAiSuggestion = lastAiMessage,
                                onApplyPrompt = { prompt ->
                                    coroutineScope.launch {
                                        isAiLoading = true
                                        val result = GeminiEditorService.editWithGemini(activeBitmap, prompt)
                                        isAiLoading = false
                                        when (result) {
                                            is AiEditResult.SuccessImage -> {
                                                activeBitmap = result.bitmap
                                                lastAiMessage = "Gemini edit applied: ${result.description}"
                                                Toast.makeText(context, "AI Edit applied!", Toast.LENGTH_SHORT).show()
                                            }
                                            is AiEditResult.SuccessStyle -> {
                                                lastAiMessage = result.suggestedAdjustments
                                                // Enhance contrast & vibrancy as suggested
                                                filterPreset = FilterPreset.CYBER_NEON
                                                adjustments = adjustments.copy(saturation = 1.35f, contrast = 1.25f)
                                                Toast.makeText(context, "AI Styling suggestions applied!", Toast.LENGTH_SHORT).show()
                                            }
                                            is AiEditResult.Error -> {
                                                lastAiMessage = result.message
                                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        EditorTab.MUSIC -> {
                            MusicTray(
                                tracks = musicTracks,
                                selectedTrack = selectedMusicTrack,
                                showMusicBadge = showMusicBadge,
                                isPlayingPreview = isPlayingMusicPreview,
                                onTrackSelected = {
                                    selectedMusicTrack = it
                                    showMusicBadge = true
                                },
                                onClearTrack = {
                                    selectedMusicTrack = null
                                    showMusicBadge = false
                                    isPlayingMusicPreview = false
                                },
                                onOpenMusicPicker = StoryEditorMusicBridge.onOpenMusicPicker?.let { picker ->
                                    {
                                        picker { chosen ->
                                            selectedMusicTrack = chosen
                                            showMusicBadge = true
                                        }
                                    }
                                },
                                onTogglePlayPreview = {
                                    isPlayingMusicPreview = !isPlayingMusicPreview
                                },
                                onToggleMusicBadge = { showMusicBadge = it }
                            )
                        }
                    }

                    // Bottom Navigation Bar for Tools
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F0F16))
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        EditorTab.entries.forEach { tab ->
                            val isSelected = activeTab == tab
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { activeTab = tab }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) StudioPink else Color(0xFFA0A0B5),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = tab.label,
                                    color = if (isSelected) StudioPink else Color(0xFFA0A0B5),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Text Dialog Modal
        if (showAddTextDialog) {
            AddTextDialog(
                onDismiss = { showAddTextDialog = false },
                onConfirm = { newText ->
                    textOverlays = textOverlays + newText
                    showAddTextDialog = false
                }
            )
        }

        // Integration Guide Dialog Modal
        if (showIntegrationGuide) {
            IntegrationGuideDialog(
                onDismiss = { showIntegrationGuide = false }
            )
        }
    }
}
