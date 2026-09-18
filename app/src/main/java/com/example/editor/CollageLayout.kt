package com.example.editor

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class CollageTemplate(val displayName: String, val slotCount: Int, val iconDesc: String) {
    SINGLE("Single", 1, "Full 1-photo frame"),
    SPLIT_VERTICAL_2("2 Split H", 2, "Side-by-side vertical split"),
    SPLIT_HORIZONTAL_2("2 Split V", 2, "Top and bottom split"),
    SPLIT_3_TOP_HERO("3 Hero Top", 3, "Top hero with 2 sub-photos"),
    SPLIT_3_LEFT_HERO("3 Hero Left", 3, "Left hero with 2 stacked right"),
    GRID_4("2x2 Grid", 4, "Four equal grid quadrants")
}

data class CollageSlotData(
    val index: Int,
    val bitmap: Bitmap? = null
)

@Composable
fun CollageCanvas(
    template: CollageTemplate,
    slots: List<CollageSlotData>,
    activeSlotIndex: Int,
    onSlotClicked: (Int) -> Unit,
    colorFilter: ColorFilter?,
    modifier: Modifier = Modifier,
    spacing: Dp = 4.dp,
    cornerRadius: Dp = 8.dp
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color(0xFF141414))
            .padding(spacing)
    ) {
        when (template) {
            CollageTemplate.SINGLE -> {
                val slot = slots.getOrNull(0) ?: CollageSlotData(0)
                CollageSlotView(
                    slot = slot,
                    isSelected = activeSlotIndex == 0,
                    onClick = { onSlotClicked(0) },
                    colorFilter = colorFilter,
                    cornerRadius = cornerRadius,
                    modifier = Modifier.fillMaxSize()
                )
            }

            CollageTemplate.SPLIT_VERTICAL_2 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    for (i in 0..1) {
                        val slot = slots.getOrNull(i) ?: CollageSlotData(i)
                        CollageSlotView(
                            slot = slot,
                            isSelected = activeSlotIndex == i,
                            onClick = { onSlotClicked(i) },
                            colorFilter = colorFilter,
                            cornerRadius = cornerRadius,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            CollageTemplate.SPLIT_HORIZONTAL_2 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    for (i in 0..1) {
                        val slot = slots.getOrNull(i) ?: CollageSlotData(i)
                        CollageSlotView(
                            slot = slot,
                            isSelected = activeSlotIndex == i,
                            onClick = { onSlotClicked(i) },
                            colorFilter = colorFilter,
                            cornerRadius = cornerRadius,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }

            CollageTemplate.SPLIT_3_TOP_HERO -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    // Top Hero slot
                    val topSlot = slots.getOrNull(0) ?: CollageSlotData(0)
                    CollageSlotView(
                        slot = topSlot,
                        isSelected = activeSlotIndex == 0,
                        onClick = { onSlotClicked(0) },
                        colorFilter = colorFilter,
                        cornerRadius = cornerRadius,
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxWidth()
                    )
                    // Bottom 2 sub-slots
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        for (i in 1..2) {
                            val slot = slots.getOrNull(i) ?: CollageSlotData(i)
                            CollageSlotView(
                                slot = slot,
                                isSelected = activeSlotIndex == i,
                                onClick = { onSlotClicked(i) },
                                colorFilter = colorFilter,
                                cornerRadius = cornerRadius,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }

            CollageTemplate.SPLIT_3_LEFT_HERO -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    // Left Hero slot
                    val leftSlot = slots.getOrNull(0) ?: CollageSlotData(0)
                    CollageSlotView(
                        slot = leftSlot,
                        isSelected = activeSlotIndex == 0,
                        onClick = { onSlotClicked(0) },
                        colorFilter = colorFilter,
                        cornerRadius = cornerRadius,
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    )
                    // Right 2 stacked slots
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        for (i in 1..2) {
                            val slot = slots.getOrNull(i) ?: CollageSlotData(i)
                            CollageSlotView(
                                slot = slot,
                                isSelected = activeSlotIndex == i,
                                onClick = { onSlotClicked(i) },
                                colorFilter = colorFilter,
                                cornerRadius = cornerRadius,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
            }

            CollageTemplate.GRID_4 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    // Row 1 (slots 0, 1)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        for (i in 0..1) {
                            val slot = slots.getOrNull(i) ?: CollageSlotData(i)
                            CollageSlotView(
                                slot = slot,
                                isSelected = activeSlotIndex == i,
                                onClick = { onSlotClicked(i) },
                                colorFilter = colorFilter,
                                cornerRadius = cornerRadius,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                    // Row 2 (slots 2, 3)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        for (i in 2..3) {
                            val slot = slots.getOrNull(i) ?: CollageSlotData(i)
                            CollageSlotView(
                                slot = slot,
                                isSelected = activeSlotIndex == i,
                                onClick = { onSlotClicked(i) },
                                colorFilter = colorFilter,
                                cornerRadius = cornerRadius,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollageSlotView(
    slot: CollageSlotData,
    isSelected: Boolean,
    onClick: () -> Unit,
    colorFilter: ColorFilter?,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(cornerRadius)

    val borderModifier = if (isSelected) {
        Modifier.border(
            width = 2.dp,
            brush = Brush.linearGradient(
                listOf(Color(0xFFFFA033), Color(0xFFE91E63), Color(0xFF8E24AA))
            ),
            shape = shape
        )
    } else {
        Modifier.border(0.5.dp, Color.White.copy(alpha = 0.2f), shape)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(borderModifier)
            .background(Color(0xFF222222))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (slot.bitmap != null) {
            Image(
                bitmap = slot.bitmap.asImageBitmap(),
                contentDescription = "Collage slot ${slot.index + 1}",
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add image to slot ${slot.index + 1}",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Slot ${slot.index + 1}",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}
