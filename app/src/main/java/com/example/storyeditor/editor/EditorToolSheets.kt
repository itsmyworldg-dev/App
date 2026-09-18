package com.example.storyeditor.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storyeditor.ai.GeminiEditorService
import com.example.storyeditor.model.Adjustments
import com.example.storyeditor.model.AspectPreset
import com.example.storyeditor.model.BackgroundCutoutMode
import com.example.storyeditor.model.FilterPreset
import com.example.storyeditor.model.MusicTrack
import com.example.storyeditor.model.StickerOverlayItem
import com.example.ui.theme.InstagramStoryGradient
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOrange
import com.example.ui.theme.StudioPink
import com.example.ui.theme.StudioYellow

@Composable
fun FilterTray(
    selectedFilter: FilterPreset,
    onFilterSelected: (FilterPreset) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("filter_tray")
    ) {
        Text(
            text = "TRENDING FILTERS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFA0A0B5),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.width(16.dp)) }
            items(FilterPreset.entries) { preset ->
                val isSelected = selectedFilter == preset
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onFilterSelected(preset) }
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when (preset) {
                                    FilterPreset.ORIGINAL -> Color(0xFF323242)
                                    FilterPreset.GOLDEN_HOUR -> Color(0xFFFFA726)
                                    FilterPreset.CYBER_NEON -> Color(0xFF00E5FF)
                                    FilterPreset.FILM_35MM -> Color(0xFF8D6E63)
                                    FilterPreset.NOIR -> Color(0xFF212121)
                                    FilterPreset.PASTEL -> Color(0xFFF48FB1)
                                    FilterPreset.TEAL_ORANGE -> Color(0xFF00897B)
                                    FilterPreset.RETRO_90S -> Color(0xFFAB47BC)
                                    FilterPreset.MOODY -> Color(0xFF2E7D32)
                                    FilterPreset.WARMTH -> Color(0xFFFF7043)
                                }
                            )
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) StudioPink else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White)
                        } else {
                            Text(
                                text = preset.tag,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Text(
                        text = preset.displayName,
                        fontSize = 11.sp,
                        color = if (isSelected) Color.White else Color(0xFFA0A0B0),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            item { Spacer(modifier = Modifier.width(16.dp)) }
        }
    }
}

@Composable
fun AdjustTray(
    adjustments: Adjustments,
    onAdjustmentsChanged: (Adjustments) -> Unit,
    onReset: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("Brightness") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("adjust_tray")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MANUAL ADJUSTMENTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA0A0B5)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onReset() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = StudioPink, modifier = Modifier.size(14.dp))
                Text("Reset", fontSize = 11.sp, color = StudioPink, fontWeight = FontWeight.Bold)
            }
        }

        // Horizontal Adjustment Category Tabs
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf("Brightness", "Contrast", "Saturation", "Warmth", "Vignette")
            items(tabs) { tab ->
                val isSelected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) StudioPink else StudioCardElevated)
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tab,
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Active Slider based on selected tab
        when (selectedTab) {
            "Brightness" -> {
                SliderRow(
                    label = "Brightness",
                    value = adjustments.brightness,
                    valueRange = -0.5f..0.5f,
                    valueText = "${(adjustments.brightness * 200).toInt()}%",
                    onValueChange = { onAdjustmentsChanged(adjustments.copy(brightness = it)) }
                )
            }
            "Contrast" -> {
                SliderRow(
                    label = "Contrast",
                    value = adjustments.contrast,
                    valueRange = 0.5f..2.0f,
                    valueText = "${((adjustments.contrast - 1f) * 100).toInt()}%",
                    onValueChange = { onAdjustmentsChanged(adjustments.copy(contrast = it)) }
                )
            }
            "Saturation" -> {
                SliderRow(
                    label = "Saturation",
                    value = adjustments.saturation,
                    valueRange = 0.0f..2.0f,
                    valueText = "${((adjustments.saturation - 1f) * 100).toInt()}%",
                    onValueChange = { onAdjustmentsChanged(adjustments.copy(saturation = it)) }
                )
            }
            "Warmth" -> {
                SliderRow(
                    label = "Warmth / Temperature",
                    value = adjustments.warmth,
                    valueRange = -0.5f..0.5f,
                    valueText = "${(adjustments.warmth * 200).toInt()}%",
                    onValueChange = { onAdjustmentsChanged(adjustments.copy(warmth = it)) }
                )
            }
            "Vignette" -> {
                SliderRow(
                    label = "Vignette Edge Glow",
                    value = adjustments.vignette,
                    valueRange = 0.0f..1.0f,
                    valueText = "${(adjustments.vignette * 100).toInt()}%",
                    onValueChange = { onAdjustmentsChanged(adjustments.copy(vignette = it)) }
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(valueText, color = StudioCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = StudioPink,
                activeTrackColor = StudioPink,
                inactiveTrackColor = Color(0xFF333345)
            )
        )
    }
}

@Composable
fun TransformTray(
    selectedAspect: AspectPreset,
    onAspectSelected: (AspectPreset) -> Unit,
    onRotate90: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("transform_tray")
    ) {
        Text(
            text = "ASPECT RATIO (STORIES & POSTS)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFA0A0B5),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspectPreset.entries.forEach { preset ->
                val isSelected = selectedAspect == preset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) StudioPink else StudioCardElevated)
                        .clickable { onAspectSelected(preset) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = preset.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (preset) {
                                AspectPreset.STORY_9_16 -> "9:16"
                                AspectPreset.POST_1_1 -> "1:1"
                                AspectPreset.PORTRAIT_4_5 -> "4:5"
                                AspectPreset.LANDSCAPE_16_9 -> "16:9"
                            },
                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color(0xFFA0A0B0),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "ORIENTATION & ROTATION",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFA0A0B5),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioCardElevated)
                    .clickable { onRotate90() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = StudioCyan)
                    Text("Rotate 90°", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioCardElevated)
                    .clickable { onFlipHorizontal() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Flip, contentDescription = "Flip H", tint = StudioYellow)
                    Text("Flip H", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioCardElevated)
                    .clickable { onFlipVertical() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Flip, contentDescription = "Flip V", tint = StudioOrange)
                    Text("Flip V", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TextStickerTray(
    onOpenAddText: () -> Unit,
    onAddSticker: (StickerOverlayItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("text_sticker_tray")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TEXT & TRENDING STICKERS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA0A0B5)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(InstagramStoryGradient)
                    .clickable { onOpenAddText() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("add_text_button")
            ) {
                Text("+ Add Text", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Location & Trend Badges", color = Color(0xFFC0C0D0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tags = listOf(
                "📍 PARIS, FRANCE",
                "🌃 TOKYO NIGHTS",
                "🌴 MIAMI BEACH",
                "✨ GOLDEN STATE",
                "☕ CAFE VIBES",
                "🎧 NOW PLAYING",
                "🔥 TRENDING"
            )
            items(tags) { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable {
                            onAddSticker(
                                StickerOverlayItem(
                                    tagLabel = tag,
                                    offsetX = 0f,
                                    offsetY = 0f
                                )
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(tag, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Trending Reaction Emojis", color = Color(0xFFC0C0D0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val emojis = listOf("✨", "🔥", "❤️", "💯", "👑", "📸", "⚡", "💫", "🍒", "🦋", "🥂")
            items(emojis) { emoji ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(StudioCardElevated)
                        .clickable {
                            onAddSticker(
                                StickerOverlayItem(
                                    emoji = emoji,
                                    offsetX = 0f,
                                    offsetY = 0f
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
fun BackgroundCutoutTray(
    selectedMode: BackgroundCutoutMode,
    isProcessingCutout: Boolean,
    hasExtractedCutout: Boolean,
    onExtractSubject: () -> Unit,
    onModeSelected: (BackgroundCutoutMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("cutout_tray")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BACKGROUND REMOVER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA0A0B5)
                )
                Text(
                    text = "High-quality smart subject cutout & backdrops",
                    fontSize = 11.sp,
                    color = Color(0xFF8E8E9E)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (hasExtractedCutout) Modifier.background(StudioCyan)
                        else Modifier.background(InstagramStoryGradient)
                    )
                    .clickable(enabled = !isProcessingCutout) { onExtractSubject() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("extract_subject_button")
            ) {
                if (isProcessingCutout) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Cutout", tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (hasExtractedCutout) "Cutout Ready" else "Extract Subject",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("Aesthetic Backdrops", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(BackgroundCutoutMode.entries) { mode ->
                val isSelected = selectedMode == mode
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onModeSelected(mode) }
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (mode.brush != null) {
                                    Modifier.background(mode.brush)
                                } else if (mode == BackgroundCutoutMode.TRANSPARENT) {
                                    Modifier.background(Color(0xFF3B3B4F))
                                } else {
                                    Modifier.background(Color(0xFF1E1E28))
                                }
                            )
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) StudioPink else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White)
                        } else if (mode == BackgroundCutoutMode.TRANSPARENT) {
                            Text("PNG", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = mode.label,
                        color = if (isSelected) Color.White else Color(0xFFA0A0B5),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GeminiAiTray(
    isLoading: Boolean,
    lastAiSuggestion: String?,
    onApplyPrompt: (String) -> Unit
) {
    var customPrompt by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("gemini_ai_tray")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Gemini AI", tint = StudioCyan)
                Text(
                    text = "GEMINI 1-CLICK AI EDITING",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text("Multimodal AI", fontSize = 10.sp, color = StudioCyan)
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "1-Click Trending Aesthetic Prompts",
            color = Color(0xFFA0A0B5),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(GeminiEditorService.PRESET_PROMPTS) { (title, prompt) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF2A2142), Color(0xFF1B233D))
                            )
                        )
                        .border(1.dp, StudioCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable(enabled = !isLoading) { onApplyPrompt(prompt) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "✨ $title",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Custom AI Prompt Field
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = customPrompt,
                onValueChange = { customPrompt = it },
                placeholder = { Text("E.g. Vintage 70s glow, golden dusk bokeh...", fontSize = 12.sp, color = Color(0xFF7A7A8A)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StudioPink,
                    unfocusedBorderColor = Color(0xFF333348),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            Button(
                onClick = {
                    if (customPrompt.isNotBlank()) {
                        onApplyPrompt(customPrompt)
                    }
                },
                enabled = !isLoading && customPrompt.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = StudioPink),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = StudioCyan, strokeWidth = 2.dp)
                Text("Gemini is reimagining your image...", color = StudioCyan, fontSize = 12.sp)
            }
        }

        if (!lastAiSuggestion.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioCardElevated)
                    .padding(12.dp)
            ) {
                Text(
                    text = lastAiSuggestion,
                    color = Color(0xFFD0D0E0),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun MusicTray(
    tracks: List<MusicTrack>,
    selectedTrack: MusicTrack?,
    showMusicBadge: Boolean,
    isPlayingPreview: Boolean,
    onTrackSelected: (MusicTrack) -> Unit,
    onClearTrack: () -> Unit,
    onOpenMusicPicker: (() -> Unit)? = null,
    onTogglePlayPreview: () -> Unit,
    onToggleMusicBadge: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("music_tray")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "STORY SOUNDTRACK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA0A0B5)
                )
                Text(
                    text = if (selectedTrack != null) "${selectedTrack.title} • ${selectedTrack.artist}" else "Link music from your host application",
                    fontSize = 11.sp,
                    color = Color(0xFF8E8E9E),
                    maxLines = 1
                )
            }

            if (selectedTrack != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sticker", fontSize = 11.sp, color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = showMusicBadge,
                        onCheckedChange = onToggleMusicBadge,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = StudioPink
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (tracks.isEmpty()) {
            // Empty state: No hardcoded music; ready for host app connection
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF14141E))
                    .border(1.dp, Color(0xFF28283C), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(StudioPink.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = StudioPink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "App Music Integration Ready",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Link via StoryEditorMusicBridge.availableTracks in your app",
                                color = Color(0xFFA0A0B5),
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    if (onOpenMusicPicker != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(InstagramStoryGradient)
                                .clickable { onOpenMusicPicker() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("open_music_picker_button")
                        ) {
                            Text("Pick Song", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Tracks List provided by host app
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tracks) { track ->
                    val isSelected = selectedTrack?.id == track.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) StudioPink.copy(alpha = 0.25f) else StudioCardElevated)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) StudioPink else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                if (isSelected) {
                                    onClearTrack()
                                } else {
                                    onTrackSelected(track)
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.width(160.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(InstagramStoryGradient)
                                        .clickable {
                                            onTrackSelected(track)
                                            onTogglePlayPreview()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSelected && isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Stop",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(track.artist, color = Color(0xFFA0A0B0), fontSize = 10.sp, maxLines = 1)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Waveform Preview Bars
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                track.waveformPreview.forEach { h ->
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height((h * 16).dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (isSelected) StudioCyan else Color(0xFF606075))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text("${track.genre} • ${track.durationText}", color = StudioYellow, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
