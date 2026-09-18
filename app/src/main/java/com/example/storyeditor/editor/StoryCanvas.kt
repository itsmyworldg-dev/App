package com.example.storyeditor.editor

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storyeditor.model.Adjustments
import com.example.storyeditor.model.AspectPreset
import com.example.storyeditor.model.BackgroundCutoutMode
import com.example.storyeditor.model.FilterPreset
import com.example.storyeditor.model.MusicTrack
import com.example.storyeditor.model.StickerOverlayItem
import com.example.storyeditor.model.TextHighlightMode
import com.example.storyeditor.model.TextOverlayItem
import com.example.storyeditor.model.TextStylePreset
import com.example.ui.theme.InstagramStoryGradient
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioPink
import kotlin.math.roundToInt

@Composable
fun StoryCanvas(
    bitmap: Bitmap,
    cutoutBitmap: Bitmap?,
    aspectPreset: AspectPreset,
    filterPreset: FilterPreset,
    adjustments: Adjustments,
    cutoutMode: BackgroundCutoutMode,
    textOverlays: List<TextOverlayItem>,
    stickerOverlays: List<StickerOverlayItem>,
    selectedMusicTrack: MusicTrack?,
    showMusicBadge: Boolean,
    onUpdateTextOverlay: (TextOverlayItem) -> Unit,
    onDeleteTextOverlay: (String) -> Unit,
    onUpdateStickerOverlay: (StickerOverlayItem) -> Unit,
    onDeleteStickerOverlay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Combine filter preset matrix + manual adjustments matrix
    val combinedColorMatrix = remember(filterPreset, adjustments) {
        val baseFilterMatrix = filterPreset.getColorMatrix()
        val adjustMatrix = adjustments.toColorMatrix()
        ColorMatrix().apply {
            set(baseFilterMatrix)
            timesAssign(adjustMatrix)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C12))
            .testTag("story_canvas_container"),
        contentAlignment = Alignment.Center
    ) {
        // Frame honoring the selected Aspect Ratio (e.g. 9:16 Story or 1:1 Post)
        Box(
            modifier = Modifier
                .padding(12.dp)
                .aspectRatio(aspectPreset.ratio)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF2A2A38), RoundedCornerShape(16.dp))
                .testTag("story_canvas_frame"),
            contentAlignment = Alignment.Center
        ) {
            // Background layer when subject cutout mode is active
            if (cutoutMode != BackgroundCutoutMode.ORIGINAL && cutoutBitmap != null) {
                if (cutoutMode.brush != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(cutoutMode.brush)
                    )
                } else {
                    // Transparent checkerboard pattern
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val tileSize = 24.dp.toPx()
                        val cols = (size.width / tileSize).toInt() + 1
                        val rows = (size.height / tileSize).toInt() + 1
                        for (r in 0 until rows) {
                            for (c in 0 until cols) {
                                val color = if ((r + c) % 2 == 0) Color(0xFF2A2A38) else Color(0xFF1E1E28)
                                drawRect(
                                    color = color,
                                    topLeft = Offset(c * tileSize, r * tileSize),
                                    size = androidx.compose.ui.geometry.Size(tileSize, tileSize)
                                )
                            }
                        }
                    }
                }

                // Render Subject Cutout Layer
                Image(
                    bitmap = cutoutBitmap.asImageBitmap(),
                    contentDescription = "Subject Cutout",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(combinedColorMatrix)
                )
            } else {
                // Render Normal Base Image with Filter + Adjustments
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Story Base Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(combinedColorMatrix)
                )
            }

            // Vignette Overlay if adjusted
            if (adjustments.vignette > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = (adjustments.vignette * 0.85f).coerceIn(0f, 0.95f))
                                )
                            )
                        )
                )
            }

            // Interactive Draggable & Pinch-Zoomable Text Overlays
            textOverlays.forEach { item ->
                DraggableTextOverlay(
                    item = item,
                    onUpdate = onUpdateTextOverlay,
                    onDelete = { onDeleteTextOverlay(item.id) }
                )
            }

            // Interactive Draggable & Pinch-Zoomable Stickers
            stickerOverlays.forEach { sticker ->
                DraggableStickerOverlay(
                    item = sticker,
                    onUpdate = onUpdateStickerOverlay,
                    onDelete = { onDeleteStickerOverlay(sticker.id) }
                )
            }

            // Instagram Story Music Badge Sticker
            if (showMusicBadge && selectedMusicTrack != null) {
                InstagramMusicBadge(
                    track = selectedMusicTrack,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
fun DraggableTextOverlay(
    item: TextOverlayItem,
    onUpdate: (TextOverlayItem) -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember(item.id) { mutableFloatStateOf(item.offsetX) }
    var offsetY by remember(item.id) { mutableFloatStateOf(item.offsetY) }
    var scale by remember(item.id) { mutableFloatStateOf(item.scale) }
    var rotation by remember(item.id) { mutableFloatStateOf(item.rotation) }
    var isSelected by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .scale(scale)
            .rotate(rotation)
            .pointerInput(item.id) {
                detectTransformGestures { _, pan, zoom, rot ->
                    offsetX += pan.x
                    offsetY += pan.y
                    scale = (scale * zoom).coerceIn(0.5f, 4.0f)
                    rotation += rot
                    onUpdate(
                        item.copy(
                            offsetX = offsetX,
                            offsetY = offsetY,
                            scale = scale,
                            rotation = rotation
                        )
                    )
                }
            }
            .clickable { isSelected = !isSelected }
            .then(
                if (isSelected) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                } else Modifier
            )
            .padding(8.dp)
    ) {
        val fontFamily = when (item.style) {
            TextStylePreset.SERIF -> FontFamily.Serif
            TextStylePreset.TYPEWRITER -> FontFamily.Monospace
            else -> FontFamily.Default
        }

        val fontStyle = if (item.style.isItalic) FontStyle.Italic else FontStyle.Normal
        val fontWeight = if (item.style.isBold) FontWeight.ExtraBold else FontWeight.Normal

        Box(
            modifier = Modifier
                .then(
                    when (item.highlightMode) {
                        TextHighlightMode.SOLID_PILL -> Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(item.highlightColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp)

                        TextHighlightMode.SEMI_TRANSPARENT -> Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(item.highlightColor.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)

                        TextHighlightMode.NEON_OUTLINE -> Modifier
                            .border(2.dp, item.textColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)

                        TextHighlightMode.NONE -> Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    }
                )
        ) {
            Text(
                text = item.text,
                color = item.textColor,
                fontSize = item.fontSize.sp,
                fontFamily = fontFamily,
                fontStyle = fontStyle,
                fontWeight = fontWeight
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .clip(CircleShape)
                    .background(StudioPink)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Delete text", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun DraggableStickerOverlay(
    item: StickerOverlayItem,
    onUpdate: (StickerOverlayItem) -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember(item.id) { mutableFloatStateOf(item.offsetX) }
    var offsetY by remember(item.id) { mutableFloatStateOf(item.offsetY) }
    var scale by remember(item.id) { mutableFloatStateOf(item.scale) }
    var rotation by remember(item.id) { mutableFloatStateOf(item.rotation) }
    var isSelected by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .scale(scale)
            .rotate(rotation)
            .pointerInput(item.id) {
                detectTransformGestures { _, pan, zoom, rot ->
                    offsetX += pan.x
                    offsetY += pan.y
                    scale = (scale * zoom).coerceIn(0.5f, 3.5f)
                    rotation += rot
                    onUpdate(
                        item.copy(
                            offsetX = offsetX,
                            offsetY = offsetY,
                            scale = scale,
                            rotation = rotation
                        )
                    )
                }
            }
            .clickable { isSelected = !isSelected }
            .then(
                if (isSelected) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                } else Modifier
            )
            .padding(6.dp)
    ) {
        if (!item.tagLabel.isNullOrBlank()) {
            // Instagram Style Location / Question / Time Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.emoji ?: "📍",
                        fontSize = 14.sp
                    )
                    Text(
                        text = item.tagLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        } else if (!item.emoji.isNullOrBlank()) {
            Text(
                text = item.emoji,
                fontSize = 48.sp
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .clip(CircleShape)
                    .background(StudioPink)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Delete sticker", tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
fun InstagramMusicBadge(
    track: MusicTrack,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "music_waves")
    val waveHeight by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_anim"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(InstagramStoryGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Music",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            Column {
                Text(
                    text = "${track.title} • ${track.artist}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${track.genre} • ${track.bpm} BPM",
                    color = StudioCyan,
                    fontSize = 10.sp
                )
            }

            // Animated mini sound visualizer
            Row(
                modifier = Modifier.height(14.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(modifier = Modifier.width(2.dp).height((12 * waveHeight).dp).background(StudioCyan))
                Box(modifier = Modifier.width(2.dp).height((14 * (1.2f - waveHeight)).dp).background(StudioPink))
                Box(modifier = Modifier.width(2.dp).height((10 * waveHeight).dp).background(Color.White))
            }
        }
    }
}
