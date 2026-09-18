package com.example.storyeditor.collage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storyeditor.model.CollageLayoutPreset
import com.example.ui.theme.InstagramStoryGradient
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioPink

@Composable
fun CollageMakerScreen(
    onCollageReadyForEditor: (Bitmap) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Default sample bitmaps
    val samplePortrait = remember {
        BitmapFactory.decodeResource(context.resources, com.example.R.drawable.sample_portrait)
    }
    val sampleUrban = remember {
        BitmapFactory.decodeResource(context.resources, com.example.R.drawable.sample_urban)
    }
    val sampleNeon = remember {
        BitmapFactory.decodeResource(context.resources, com.example.R.drawable.sample_neon)
    }

    var selectedPreset by remember { mutableStateOf(CollageLayoutPreset.TWO_SPLIT_V) }
    var cellSpacing by remember { mutableFloatStateOf(4f) }
    var cornerRadius by remember { mutableFloatStateOf(8f) }
    var selectedBorderColor by remember { mutableStateOf(Color.Black) }

    // Store bitmaps per cell index
    var cellBitmaps by remember {
        mutableStateOf(
            mapOf(
                0 to samplePortrait,
                1 to sampleUrban,
                2 to sampleNeon,
                3 to samplePortrait
            )
        )
    }

    var activeCellIndexToPick by remember { mutableIntStateOf(0) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        cellBitmaps = cellBitmaps + (activeCellIndexToPick to bmp)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val borderColors = listOf(
        Color.Black,
        Color.White,
        Color(0xFFE0C3FC), // Pastel Lilac
        Color(0xFF1B1B26), // Dark Studio
        Color(0xFFFFD166)  // Warm Sand
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F16))
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("collage_maker_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Text(
                text = "Collage Studio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(InstagramStoryGradient)
                    .clickable {
                        // Render collage into export bitmap
                        val rendered = renderCollageBitmap(
                            layout = selectedPreset,
                            bitmaps = cellBitmaps,
                            spacing = cellSpacing,
                            radius = cornerRadius,
                            bgColor = selectedBorderColor
                        )
                        onCollageReadyForEditor(rendered)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("collage_done_button")
            ) {
                Text("Edit Story", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Center: Interactive Collage Grid Preview (9:16 vertical story aspect)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(selectedBorderColor)
                    .padding(cellSpacing.dp)
            ) {
                when (selectedPreset) {
                    CollageLayoutPreset.TWO_SPLIT_V -> {
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(cellSpacing.dp)) {
                            CollageCell(
                                bitmap = cellBitmaps[0],
                                cornerRadius = cornerRadius,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onPick = {
                                    activeCellIndexToPick = 0
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            )
                            CollageCell(
                                bitmap = cellBitmaps[1],
                                cornerRadius = cornerRadius,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onPick = {
                                    activeCellIndexToPick = 1
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            )
                        }
                    }
                    CollageLayoutPreset.TWO_SPLIT_H -> {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(cellSpacing.dp)) {
                            CollageCell(
                                bitmap = cellBitmaps[0],
                                cornerRadius = cornerRadius,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                onPick = {
                                    activeCellIndexToPick = 0
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            )
                            CollageCell(
                                bitmap = cellBitmaps[1],
                                cornerRadius = cornerRadius,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                onPick = {
                                    activeCellIndexToPick = 1
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            )
                        }
                    }
                    CollageLayoutPreset.FOUR_GRID -> {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(cellSpacing.dp)) {
                            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cellSpacing.dp)) {
                                CollageCell(
                                    bitmap = cellBitmaps[0],
                                    cornerRadius = cornerRadius,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onPick = {
                                        activeCellIndexToPick = 0
                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                )
                                CollageCell(
                                    bitmap = cellBitmaps[1],
                                    cornerRadius = cornerRadius,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onPick = {
                                        activeCellIndexToPick = 1
                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                )
                            }
                            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cellSpacing.dp)) {
                                CollageCell(
                                    bitmap = cellBitmaps[2],
                                    cornerRadius = cornerRadius,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onPick = {
                                        activeCellIndexToPick = 2
                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                )
                                CollageCell(
                                    bitmap = cellBitmaps[3],
                                    cornerRadius = cornerRadius,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onPick = {
                                        activeCellIndexToPick = 3
                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                )
                            }
                        }
                    }
                    CollageLayoutPreset.STORY_STRIP -> {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(cellSpacing.dp)) {
                            for (i in 0..2) {
                                CollageCell(
                                    bitmap = cellBitmaps[i],
                                    cornerRadius = cornerRadius,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    onPick = {
                                        activeCellIndexToPick = i
                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                )
                            }
                        }
                    }
                    CollageLayoutPreset.POLAROID_DUO -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    CollageCell(
                                        bitmap = cellBitmaps[0],
                                        cornerRadius = 6f,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        onPick = {
                                            activeCellIndexToPick = 0
                                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    CollageCell(
                                        bitmap = cellBitmaps[1],
                                        cornerRadius = 6f,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        onPick = {
                                            activeCellIndexToPick = 1
                                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Controls: Presets, Spacing, Border Colors
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181822))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Layout Template Selector
                Text("COLLAGE TEMPLATES", color = Color(0xFFA0A0B5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(CollageLayoutPreset.entries) { preset ->
                        val isSelected = selectedPreset == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) StudioPink else StudioCardElevated)
                                .clickable { selectedPreset = preset }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(preset.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Spacing and Border Radius Sliders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gap", color = Color(0xFFA0A0B5), fontSize = 11.sp)
                            Text("${cellSpacing.toInt()}dp", color = StudioCyan, fontSize = 11.sp)
                        }
                        Slider(
                            value = cellSpacing,
                            onValueChange = { cellSpacing = it },
                            valueRange = 0f..20f,
                            colors = SliderDefaults.colors(thumbColor = StudioPink, activeTrackColor = StudioPink)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Corners", color = Color(0xFFA0A0B5), fontSize = 11.sp)
                            Text("${cornerRadius.toInt()}dp", color = StudioCyan, fontSize = 11.sp)
                        }
                        Slider(
                            value = cornerRadius,
                            onValueChange = { cornerRadius = it },
                            valueRange = 0f..24f,
                            colors = SliderDefaults.colors(thumbColor = StudioPink, activeTrackColor = StudioPink)
                        )
                    }
                }

                // Border Frame Color
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Frame Color", color = Color(0xFFA0A0B5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        borderColors.forEach { c ->
                            val isSelected = selectedBorderColor == c
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) StudioPink else Color(0xFF444455),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedBorderColor = c }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollageCell(
    bitmap: Bitmap?,
    cornerRadius: Float,
    modifier: Modifier = Modifier,
    onPick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(Color(0xFF222230))
            .clickable { onPick() },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Collage cell image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Add, contentDescription = "Add image", tint = Color(0xFFA0A0B5), modifier = Modifier.size(28.dp))
                Text("Tap to add", color = Color(0xFFA0A0B5), fontSize = 10.sp)
            }
        }
    }
}

private fun renderCollageBitmap(
    layout: CollageLayoutPreset,
    bitmaps: Map<Int, Bitmap?>,
    spacing: Float,
    radius: Float,
    bgColor: Color
): Bitmap {
    val outWidth = 1080
    val outHeight = 1920
    val result = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    val bgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(
            (bgColor.alpha * 255).toInt(),
            (bgColor.red * 255).toInt(),
            (bgColor.green * 255).toInt(),
            (bgColor.blue * 255).toInt()
        )
    }
    canvas.drawRect(0f, 0f, outWidth.toFloat(), outHeight.toFloat(), bgPaint)

    val pad = spacing * 3f
    val b0 = bitmaps[0]
    val b1 = bitmaps[1]
    val b2 = bitmaps[2]
    val b3 = bitmaps[3]

    when (layout) {
        CollageLayoutPreset.TWO_SPLIT_V -> {
            val halfW = (outWidth - pad * 3) / 2
            b0?.let { drawScaled(canvas, it, pad, pad, halfW, outHeight - pad * 2) }
            b1?.let { drawScaled(canvas, it, pad * 2 + halfW, pad, halfW, outHeight - pad * 2) }
        }
        CollageLayoutPreset.TWO_SPLIT_H -> {
            val halfH = (outHeight - pad * 3) / 2
            b0?.let { drawScaled(canvas, it, pad, pad, outWidth - pad * 2, halfH) }
            b1?.let { drawScaled(canvas, it, pad, pad * 2 + halfH, outWidth - pad * 2, halfH) }
        }
        CollageLayoutPreset.FOUR_GRID -> {
            val halfW = (outWidth - pad * 3) / 2
            val halfH = (outHeight - pad * 3) / 2
            b0?.let { drawScaled(canvas, it, pad, pad, halfW, halfH) }
            b1?.let { drawScaled(canvas, it, pad * 2 + halfW, pad, halfW, halfH) }
            b2?.let { drawScaled(canvas, it, pad, pad * 2 + halfH, halfW, halfH) }
            b3?.let { drawScaled(canvas, it, pad * 2 + halfW, pad * 2 + halfH, halfW, halfH) }
        }
        CollageLayoutPreset.STORY_STRIP -> {
            val thirdH = (outHeight - pad * 4) / 3
            b0?.let { drawScaled(canvas, it, pad, pad, outWidth - pad * 2, thirdH) }
            b1?.let { drawScaled(canvas, it, pad, pad * 2 + thirdH, outWidth - pad * 2, thirdH) }
            b2?.let { drawScaled(canvas, it, pad, pad * 3 + thirdH * 2, outWidth - pad * 2, thirdH) }
        }
        CollageLayoutPreset.POLAROID_DUO -> {
            val halfH = (outHeight - pad * 3) / 2
            b0?.let { drawScaled(canvas, it, pad * 2, pad * 2, outWidth - pad * 4, halfH - pad * 2) }
            b1?.let { drawScaled(canvas, it, pad * 2, pad * 3 + halfH, outWidth - pad * 4, halfH - pad * 2) }
        }
    }

    return result
}

private fun drawScaled(
    canvas: android.graphics.Canvas,
    src: Bitmap,
    destX: Float,
    destY: Float,
    destWidth: Float,
    destHeight: Float
) {
    val srcRect = android.graphics.Rect(0, 0, src.width, src.height)
    val destRect = android.graphics.RectF(destX, destY, destX + destWidth, destY + destHeight)
    canvas.drawBitmap(src, srcRect, destRect, null)
}
