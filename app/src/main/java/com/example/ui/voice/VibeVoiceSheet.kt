package com.example.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GenZPink = Color(0xFFFF2A85)
private val GenZPurple = Color(0xFF8A2BE2)
private val GenZCyan = Color(0xFF00F0FF)
private val GenZDarkBg = Color(0xFF111116)
private val GenZCardBg = Color(0xFF1C1C24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeVoiceSheet(
    isListening: Boolean,
    speechRms: Float,
    isThinking: Boolean,
    currentTranscript: String,
    aiResponse: String?,
    isTtsMuted: Boolean,
    onToggleTtsMute: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onSendQuery: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // Pulse animation for active listening/thinking
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val dynamicRmsScale = if (isListening) {
        1f + (speechRms.coerceIn(0f, 10f) / 10f) * 0.35f
    } else if (isThinking) {
        pulseScale
    } else {
        1f
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GenZDarkBg,
        dragHandle = null,
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding()
            .testTag("vibe_voice_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Brand, Model badge, Mute toggle, Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(GenZPink, GenZPurple))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "VibeNav AI",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "VibeNav",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2E1A47))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "GEMINI LIVE",
                                    color = GenZCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                        Text(
                            text = "Gen Z Voice Navigation & Guide",
                            color = Color(0xFFA0A0B0),
                            fontSize = 12.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleTtsMute,
                        modifier = Modifier.testTag("vibe_voice_tts_toggle")
                    ) {
                        Icon(
                            imageVector = if (isTtsMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isTtsMuted) "Unmute Voice" else "Mute Voice",
                            tint = if (isTtsMuted) Color(0xFF888899) else GenZCyan
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("vibe_voice_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Central Audio Reactive Orb
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(dynamicRmsScale),
                contentAlignment = Alignment.Center
            ) {
                // Outer Glow ring
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isListening) GenZCyan.copy(alpha = 0.5f) else GenZPink.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // Core Orb
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .shadow(16.dp, CircleShape, spotColor = GenZCyan)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (isListening) {
                                    listOf(GenZCyan, GenZPurple)
                                } else if (isThinking) {
                                    listOf(GenZPink, GenZPurple)
                                } else {
                                    listOf(GenZPurple, Color(0xFF221133))
                                }
                            )
                        )
                        .clickable {
                            if (isListening) onStopListening() else onStartListening()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = "Mic Orb",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status message
            Text(
                text = when {
                    isListening -> "Listening... spill the tea or say where to go!"
                    isThinking -> "Vibe checking with Gemini Live..."
                    currentTranscript.isNotBlank() -> "Heard you! Tap mic or speak again."
                    else -> "Tap the mic or choose a quick vibe below"
                },
                color = if (isListening) GenZCyan else Color(0xFFB0B0C4),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live Transcript Bubble (User speech)
            AnimatedVisibility(visible = currentTranscript.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GenZCardBg)
                        .border(1.dp, Color(0xFF333344), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "YOU SAID",
                            color = Color(0xFF888899),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "“$currentTranscript”",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // AI Response Bubble
            AnimatedVisibility(visible = !aiResponse.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2A1636), Color(0xFF1B1A2A))
                            )
                        )
                        .border(1.dp, GenZPink.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = GenZPink,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "VIBENAV",
                                color = GenZPink,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = aiResponse ?: "",
                            color = Color(0xFFF0F0FF),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Suggestion Chips (Gen Z Navigation & Curated Prompts)
            Text(
                text = "QUICK COMMANDS & VIBES",
                color = Color(0xFF707080),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickVibeChip("🧭 Which way do I turn?") { onSendQuery("Which way do I turn?") }
                QuickVibeChip("🔄 Repeat turn guidance") { onSendQuery("Repeat turn instruction") }
                QuickVibeChip("🗺️ Start voice navigation") { onSendQuery("Navigate to Dassam Falls") }
                QuickVibeChip("✨ Take me to Explore") { onSendQuery("Take me to explore") }
                QuickVibeChip("📸 Drop a post") { onSendQuery("Drop a new post") }
                QuickVibeChip("🏠 Go to Feed") { onSendQuery("Go to feed") }
                QuickVibeChip("🌊 Best waterfall spot?") { onSendQuery("What is the best waterfall spot in Ranchi?") }
                QuickVibeChip("🥟 Bussin food spots") { onSendQuery("Best food and momo spots in Ranchi") }
                QuickVibeChip("🔔 Show notifications") { onSendQuery("Show my notifications") }
                QuickVibeChip("👤 My profile aesthetic") { onSendQuery("Take me to my profile") }
                QuickVibeChip("📜 Scroll down") { onSendQuery("Scroll down") }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom Input Bar & Microphone Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (isListening) listOf(GenZCyan, GenZPurple)
                                else listOf(GenZPink, GenZPurple)
                            )
                        )
                        .clickable {
                            if (isListening) onStopListening() else onStartListening()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Toggle Mic",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Text query field with Send button
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = "Say or type navigation query...",
                            color = Color(0xFF6C6C7D),
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = GenZCardBg,
                        unfocusedContainerColor = GenZCardBg,
                        focusedBorderColor = GenZCyan,
                        unfocusedBorderColor = Color(0xFF2E2E38)
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (textInput.isNotBlank()) {
                                    val query = textInput
                                    textInput = ""
                                    onSendQuery(query)
                                }
                            },
                            enabled = textInput.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (textInput.isNotBlank()) GenZCyan else Color(0xFF4C4C5C)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (textInput.isNotBlank()) {
                                val query = textInput
                                textInput = ""
                                onSendQuery(query)
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vibe_voice_text_input")
                )
            }
        }
    }
}

@Composable
private fun QuickVibeChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = GenZCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E3E)),
        modifier = Modifier.testTag("quick_vibe_chip_${text.take(10)}")
    ) {
        Text(
            text = text,
            color = Color(0xFFE2E2F0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
