package com.example.editor

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TextSticker(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val textColor: Color = Color.White,
    val backgroundColor: Color? = Color.Black.copy(alpha = 0.65f),
    val fontSizeSp: Float = 22f,
    val isBold: Boolean = true,
    val offsetXFraction: Float = 0.5f,
    val offsetYFraction: Float = 0.5f
)

data class BadgeSticker(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val iconEmoji: String,
    val subtitle: String? = null,
    val accentColor: Color = Color(0xFFFF3B5C),
    val offsetXFraction: Float = 0.5f,
    val offsetYFraction: Float = 0.35f
)

object StoryStickersCatalog {
    fun getAvailableBadges(): List<BadgeSticker> {
        val currentTimeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        return listOf(
            BadgeSticker(
                title = "Ranchi Vibe",
                iconEmoji = "📍",
                subtitle = "Jharkhand",
                accentColor = Color(0xFFFF3B5C)
            ),
            BadgeSticker(
                title = currentTimeStr,
                iconEmoji = "⏰",
                subtitle = "LIVE",
                accentColor = Color(0xFFFFA033)
            ),
            BadgeSticker(
                title = "Vibe Check",
                iconEmoji = "🔥",
                subtitle = "100% Lit",
                accentColor = Color(0xFFFF5722)
            ),
            BadgeSticker(
                title = "Mera Thikaana",
                iconEmoji = "✨",
                subtitle = "Exclusive",
                accentColor = Color(0xFF8E24AA)
            ),
            BadgeSticker(
                title = "Waterfall Chaser",
                iconEmoji = "🌊",
                subtitle = "Dassam / Hundru",
                accentColor = Color(0xFF00BCD4)
            ),
            BadgeSticker(
                title = "Bhukkad Mode",
                iconEmoji = "🍔",
                subtitle = "Food Trail",
                accentColor = Color(0xFFFF9800)
            ),
            BadgeSticker(
                title = "Now Vibing",
                iconEmoji = "🎵",
                subtitle = "Lofi Beats",
                accentColor = Color(0xFFE91E63)
            ),
            BadgeSticker(
                title = "Aesthetic",
                iconEmoji = "🌸",
                subtitle = "Story Mood",
                accentColor = Color(0xFFE040FB)
            )
        )
    }
}
