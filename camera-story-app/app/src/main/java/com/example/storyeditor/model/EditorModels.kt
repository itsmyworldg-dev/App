package com.example.storyeditor.model

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import java.util.UUID

enum class AspectPreset(val label: String, val ratio: Float, val subtitle: String) {
    STORY_9_16("Story", 9f / 16f, "9:16 full screen"),
    POST_1_1("Post", 1f / 1f, "1:1 square feed"),
    PORTRAIT_4_5("Portrait", 4f / 5f, "4:5 Instagram feed"),
    LANDSCAPE_16_9("Cover", 16f / 9f, "16:9 widescreen")
}

enum class FilterPreset(val displayName: String, val tag: String) {
    ORIGINAL("Normal", "Raw"),
    GOLDEN_HOUR("Golden", "Sunset"),
    CYBER_NEON("Cyber", "Futuristic"),
    FILM_35MM("35mm Film", "Vintage"),
    NOIR("Noir", "B&W"),
    PASTEL("Pastel", "Soft"),
    TEAL_ORANGE("Teal/Amber", "Cinematic"),
    RETRO_90S("Retro 90s", "Y2K"),
    MOODY("Moody", "Dark"),
    WARMTH("Sunburst", "Vibrant");

    fun getColorMatrix(): ColorMatrix {
        return when (this) {
            ORIGINAL -> ColorMatrix()
            GOLDEN_HOUR -> {
                ColorMatrix(
                    floatArrayOf(
                        1.2f, 0.05f, 0.0f, 0f, 20f,
                        0.05f, 1.1f, 0.0f, 0f, 10f,
                        0.0f, 0.0f, 0.85f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            CYBER_NEON -> {
                ColorMatrix(
                    floatArrayOf(
                        1.15f, 0f, 0.2f, 0f, 10f,
                        0f, 1.25f, 0.1f, 0f, 15f,
                        0.2f, 0f, 1.35f, 0f, 30f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            FILM_35MM -> {
                ColorMatrix(
                    floatArrayOf(
                        0.95f, 0.1f, 0.05f, 0f, 15f,
                        0.05f, 1.0f, 0.05f, 0f, 15f,
                        0.05f, 0.05f, 0.9f, 0f, 25f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            NOIR -> {
                val matrix = ColorMatrix().apply { setToSaturation(0f) }
                val cm = ColorMatrix(
                    floatArrayOf(
                        1.3f, 0f, 0f, 0f, -20f,
                        0f, 1.3f, 0f, 0f, -20f,
                        0f, 0f, 1.3f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                matrix.timesAssign(cm)
                matrix
            }
            PASTEL -> {
                val matrix = ColorMatrix().apply { setToSaturation(0.75f) }
                val cm = ColorMatrix(
                    floatArrayOf(
                        0.95f, 0f, 0f, 0f, 25f,
                        0.95f, 0.95f, 0f, 0f, 25f,
                        0f, 0f, 0.98f, 0f, 30f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                matrix.timesAssign(cm)
                matrix
            }
            TEAL_ORANGE -> {
                ColorMatrix(
                    floatArrayOf(
                        1.25f, 0f, 0f, 0f, 15f,
                        0f, 1.05f, 0.1f, 0f, 0f,
                        -0.1f, 0.1f, 1.25f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            RETRO_90S -> {
                val matrix = ColorMatrix().apply { setToSaturation(1.25f) }
                val cm = ColorMatrix(
                    floatArrayOf(
                        1.1f, 0f, 0f, 0f, 10f,
                        0f, 1.05f, 0f, 0f, 5f,
                        0f, 0f, 0.95f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                matrix.timesAssign(cm)
                matrix
            }
            MOODY -> {
                val matrix = ColorMatrix().apply { setToSaturation(0.65f) }
                val cm = ColorMatrix(
                    floatArrayOf(
                        0.9f, 0f, 0f, 0f, -10f,
                        0f, 0.9f, 0f, 0f, -5f,
                        0f, 0f, 0.95f, 0f, 5f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                matrix.timesAssign(cm)
                matrix
            }
            WARMTH -> {
                ColorMatrix(
                    floatArrayOf(
                        1.3f, 0f, 0f, 0f, 20f,
                        0f, 1.15f, 0f, 0f, 10f,
                        0f, 0f, 0.85f, 0f, -15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
        }
    }
}

data class Adjustments(
    val brightness: Float = 0f,    // -0.5f to 0.5f
    val contrast: Float = 1f,      // 0.5f to 2.0f
    val saturation: Float = 1f,    // 0f to 2.0f
    val warmth: Float = 0f,        // -0.5f to 0.5f
    val vignette: Float = 0f       // 0f to 1.0f
) {
    fun toColorMatrix(): ColorMatrix {
        val matrix = ColorMatrix()
        // Brightness
        val bOffset = brightness * 255f
        // Contrast
        val c = contrast
        // Warmth shift
        val rWarm = warmth * 40f
        val bWarm = -warmth * 40f

        val cm = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, bOffset + rWarm,
                0f, c, 0f, 0f, bOffset,
                0f, 0f, c, 0f, bOffset + bWarm,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.setToSaturation(saturation)
        matrix.timesAssign(cm)
        return matrix
    }
}

enum class TextStylePreset(val fontName: String, val isItalic: Boolean = false, val isBold: Boolean = false) {
    MODERN("Modern Bold", isBold = true),
    NEON("Neon Glow", isItalic = true),
    SERIF("Editorial Serif", isItalic = true),
    TYPEWRITER("Typewriter", isBold = false),
    STAMP("Sticker Stamp", isBold = true)
}

enum class TextHighlightMode {
    NONE,
    SOLID_PILL,
    SEMI_TRANSPARENT,
    NEON_OUTLINE
}

data class TextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val style: TextStylePreset = TextStylePreset.MODERN,
    val textColor: Color = Color.White,
    val highlightMode: TextHighlightMode = TextHighlightMode.SOLID_PILL,
    val highlightColor: Color = Color.Black.copy(alpha = 0.75f),
    val fontSize: Float = 24f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f
)

data class StickerOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String? = null,
    val tagLabel: String? = null,
    val isMusicSticker: Boolean = false,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f
)

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationText: String = "0:30",
    val genre: String = "Story Audio",
    val bpm: Int = 120,
    val waveformPreview: List<Float> = listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 0.8f, 0.4f, 0.7f),
    val audioUri: String? = null
)

enum class BackgroundCutoutMode(val label: String, val brush: Brush?) {
    ORIGINAL("Original", null),
    TRANSPARENT("Cutout PNG", null),
    NEON_CYBER("Cyberpunk", Brush.linearGradient(listOf(Color(0xFF00F5D4), Color(0xFF7B2CBF)))),
    SUNSET_AESTHETIC("Golden Sunset", Brush.linearGradient(listOf(Color(0xFFFF5E36), Color(0xFFFF0844)))),
    PASTEL_LILAC("Pastel Dream", Brush.linearGradient(listOf(Color(0xFFE0C3FC), Color(0xFF8EC5FC)))),
    STUDIO_DARK("Studio Dark", Brush.radialGradient(listOf(Color(0xFF2A2A38), Color(0xFF0A0A0F)))),
    CLEAN_WHITE("Pure White", Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF0F0F5))))
}

enum class CollageLayoutPreset(val title: String, val iconDescription: String, val cellCount: Int) {
    TWO_SPLIT_V("Split Duo", "Two vertical panels", 2),
    TWO_SPLIT_H("Stacked", "Two horizontal panels", 2),
    FOUR_GRID("4 Grid", "Clean 2x2 grid", 4),
    STORY_STRIP("3 Story Strip", "Three stacked story frames", 3),
    POLAROID_DUO("Polaroid Frames", "Aesthetic duo with margins", 2)
}
