package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AssistantDirection
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-visibility Turn-by-Turn Voice Navigation Guidance HUD.
 * Renders at the top when live navigation is active, showing current turn maneuver,
 * distance, destination, spoken instruction, and controls to repeat or mute voice guidance.
 */
@Composable
fun NavVoiceGuidanceHud(
    isNavActive: Boolean,
    instruction: String,
    distance: String,
    nextInstruction: String,
    destinationName: String,
    isMuted: Boolean,
    isSpeaking: Boolean,
    onRepeatInstruction: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isNavActive && instruction.isNotBlank(),
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_speaking")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isSpeaking) 1.25f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(450, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )

        val cleanInstr = instruction.ifBlank { "Finding your route…" }
        val lower = cleanInstr.lowercase()

        val turnIcon = when {
            lower.contains("arrive") || lower.contains("reached") -> Icons.Default.Place
            lower.contains("left") -> Icons.Default.TurnLeft
            lower.contains("right") -> Icons.Default.TurnRight
            lower.contains("seedha") || lower.contains("continue") || lower.contains("straight") -> Icons.Default.Navigation
            else -> Icons.Default.AssistantDirection
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF14141E).copy(alpha = 0.96f),
            shadowElevation = 10.dp,
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        if (isSpeaking) Color(0xFF00F0FF) else Color(0xFFFF2A85),
                        Color(0xFF8A2BE2)
                    )
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("nav_voice_guidance_hud")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Maneuver Turn Icon Box with glowing gradient
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF2A85), Color(0xFF8A2BE2))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = turnIcon,
                            contentDescription = "Turn Direction",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Instruction details
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (distance.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF00F0FF).copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.6f)),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = distance,
                                        color = Color(0xFF00F0FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (destinationName.isNotBlank()) {
                                Text(
                                    text = destinationName,
                                    color = Color(0xFFB0B0C0),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        // Spoken turn direction (e.g. "Ab turn left onto Main Road")
                        Text(
                            text = cleanInstr,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )

                        if (nextInstruction.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = nextInstruction,
                                color = Color(0xFF8A8F98),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Voice controls: Repeat Instruction button
                    IconButton(
                        onClick = onRepeatInstruction,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222232))
                            .testTag("nav_voice_repeat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Repeat turn voice guidance",
                            tint = if (isSpeaking) Color(0xFF00F0FF) else Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .scale(if (isSpeaking) pulseScale else 1f)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Voice controls: Mute/Unmute button
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0xFF332025) else Color(0xFF222232))
                            .testTag("nav_voice_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute Voice" else "Mute Voice",
                            tint = if (isMuted) Color(0xFFFF4B6E) else Color(0xFF00F0FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Spoken feedback pill indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isMuted) Color(0xFFFF4B6E)
                                    else if (isSpeaking) Color(0xFF00F0FF)
                                    else Color(0xFF00E676)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isMuted) "Voice guidance muted"
                            else if (isSpeaking) "Speaking navigation direction…"
                            else "Voice guidance active (turn alerts ON)",
                            color = if (isMuted) Color(0xFFFF8096) else Color(0xFF8E8EA0),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "Tap 🔊 to repeat",
                        color = Color(0xFF6E6E80),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
