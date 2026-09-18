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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.storyeditor.model.TextHighlightMode
import com.example.storyeditor.model.TextOverlayItem
import com.example.storyeditor.model.TextStylePreset
import com.example.ui.theme.InstagramStoryGradient
import com.example.ui.theme.StudioPink

@Composable
fun AddTextDialog(
    onDismiss: () -> Unit,
    onConfirm: (TextOverlayItem) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(TextStylePreset.MODERN) }
    var selectedColor by remember { mutableStateOf(Color.White) }
    var selectedHighlight by remember { mutableStateOf(TextHighlightMode.SOLID_PILL) }

    val colorPalette = listOf(
        Color.White,
        Color(0xFFFFEB3B), // Neon Yellow
        Color(0xFF00E5FF), // Cyan
        Color(0xFFFF4081), // Hot Pink
        Color(0xFFFF9100), // Amber Orange
        Color(0xFF00E676), // Mint Green
        Color(0xFFD500F9), // Purple
        Color.Black
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C26)),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Add Text Overlay",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Live Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF101018)),
                    contentAlignment = Alignment.Center
                ) {
                    val fontFamily = when (selectedStyle) {
                        TextStylePreset.SERIF -> FontFamily.Serif
                        TextStylePreset.TYPEWRITER -> FontFamily.Monospace
                        else -> FontFamily.Default
                    }
                    val fontStyle = if (selectedStyle.isItalic) FontStyle.Italic else FontStyle.Normal
                    val fontWeight = if (selectedStyle.isBold) FontWeight.ExtraBold else FontWeight.Normal

                    Box(
                        modifier = Modifier
                            .then(
                                when (selectedHighlight) {
                                    TextHighlightMode.SOLID_PILL -> Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedColor == Color.Black) Color.White else Color.Black.copy(alpha = 0.8f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                    TextHighlightMode.SEMI_TRANSPARENT -> Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                    TextHighlightMode.NEON_OUTLINE -> Modifier
                                        .border(2.dp, selectedColor, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                    TextHighlightMode.NONE -> Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                }
                            )
                    ) {
                        Text(
                            text = text.ifBlank { "Your Story Text" },
                            color = selectedColor,
                            fontSize = 20.sp,
                            fontFamily = fontFamily,
                            fontStyle = fontStyle,
                            fontWeight = fontWeight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Type something...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StudioPink,
                        unfocusedBorderColor = Color(0xFF323246),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Font Style Preset Selector
                Text("Font Style", color = Color(0xFFA0A0B5), fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(TextStylePreset.entries) { preset ->
                        val isSelected = selectedStyle == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) StudioPink else Color(0xFF282838))
                                .clickable { selectedStyle = preset }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(preset.fontName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Highlight Mode Selector
                Text("Highlight Pill", color = Color(0xFFA0A0B5), fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(TextHighlightMode.entries) { mode ->
                        val isSelected = selectedHighlight == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) StudioPink else Color(0xFF282838))
                                .clickable { selectedHighlight = mode }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = when (mode) {
                                    TextHighlightMode.NONE -> "No Pill"
                                    TextHighlightMode.SOLID_PILL -> "Solid Pill"
                                    TextHighlightMode.SEMI_TRANSPARENT -> "Translucent"
                                    TextHighlightMode.NEON_OUTLINE -> "Neon Outline"
                                },
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Color Palette
                Text("Color Palette", color = Color(0xFFA0A0B5), fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    colorPalette.forEach { c ->
                        val isSelected = selectedColor == c
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) StudioPink else Color(0xFF555566),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = c }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF282838))
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(InstagramStoryGradient)
                            .clickable {
                                if (text.isNotBlank()) {
                                    onConfirm(
                                        TextOverlayItem(
                                            text = text,
                                            style = selectedStyle,
                                            textColor = selectedColor,
                                            highlightMode = selectedHighlight,
                                            highlightColor = if (selectedColor == Color.Black) Color.White else Color.Black.copy(alpha = 0.8f)
                                        )
                                    )
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Add to Canvas", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
