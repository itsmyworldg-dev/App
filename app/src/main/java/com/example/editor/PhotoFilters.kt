package com.example.editor

import androidx.compose.ui.graphics.ColorMatrix

enum class FilterPreset(val displayName: String, val matrixArray: FloatArray) {
    NORMAL(
        "Normal",
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    CLARENDON(
        "Clarendon",
        floatArrayOf(
            1.2f, 0f, 0f, 0f, 10f,
            0f, 1.15f, 0f, 0f, 10f,
            0f, 0f, 1.3f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    VINTAGE_SEPIA(
        "Sepia",
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 20f,
            0.349f, 0.686f, 0.168f, 0f, 10f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    NOIR(
        "Noir",
        floatArrayOf(
            0.33f, 0.59f, 0.11f, 0f, -10f,
            0.33f, 0.59f, 0.11f, 0f, -10f,
            0.33f, 0.59f, 0.11f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    CYBER_NEON(
        "Cyberpunk",
        floatArrayOf(
            1.3f, 0f, 0.2f, 0f, 25f,
            0f, 0.9f, 0f, 0f, -10f,
            0.1f, 0.1f, 1.4f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    SUNSET_AMBER(
        "Sunset",
        floatArrayOf(
            1.3f, 0.1f, 0f, 0f, 30f,
            0.1f, 1.1f, 0f, 0f, 15f,
            0f, 0f, 0.85f, 0f, -20f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    MOODY_TEAL(
        "Teal Moody",
        floatArrayOf(
            1.1f, 0f, 0f, 0f, 10f,
            0f, 1.15f, 0.1f, 0f, 10f,
            0f, 0.2f, 1.35f, 0f, 25f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    DREAMY_PASTEL(
        "Pastel",
        floatArrayOf(
            1.05f, 0.05f, 0.05f, 0f, 25f,
            0.05f, 1.05f, 0.05f, 0f, 25f,
            0.05f, 0.05f, 1.15f, 0f, 35f,
            0f, 0f, 0f, 1f, 0f
        )
    );

    fun toComposeColorMatrix(): ColorMatrix = ColorMatrix(matrixArray)

    fun toAndroidColorMatrix(): android.graphics.ColorMatrix = android.graphics.ColorMatrix(matrixArray)
}

data class ImageAdjustments(
    val brightness: Float = 0f,   // -100f to 100f
    val contrast: Float = 1f,      // 0.5f to 2.0f
    val saturation: Float = 1f,    // 0f to 2f
    val warmth: Float = 0f         // -50f to 50f
) {
    fun buildCombinedMatrix(basePreset: FilterPreset = FilterPreset.NORMAL): ColorMatrix {
        val cm = android.graphics.ColorMatrix(basePreset.matrixArray)

        // Apply saturation
        if (saturation != 1f) {
            val satMatrix = android.graphics.ColorMatrix().apply {
                setSaturation(saturation)
            }
            cm.postConcat(satMatrix)
        }

        // Apply brightness and warmth
        val b = brightness
        val w = warmth
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f + b

        val adjustArray = floatArrayOf(
            scale, 0f, 0f, 0f, translate + w * 0.8f,
            0f, scale, 0f, 0f, translate + w * 0.3f,
            0f, 0f, scale, 0f, translate - w * 0.8f,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(android.graphics.ColorMatrix(adjustArray))

        return ColorMatrix(cm.array)
    }

    fun buildAndroidCombinedMatrix(basePreset: FilterPreset = FilterPreset.NORMAL): android.graphics.ColorMatrix {
        val cm = android.graphics.ColorMatrix(basePreset.matrixArray)

        if (saturation != 1f) {
            val satMatrix = android.graphics.ColorMatrix().apply {
                setSaturation(saturation)
            }
            cm.postConcat(satMatrix)
        }

        val b = brightness
        val w = warmth
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f + b

        val adjustArray = floatArrayOf(
            scale, 0f, 0f, 0f, translate + w * 0.8f,
            0f, scale, 0f, 0f, translate + w * 0.3f,
            0f, 0f, scale, 0f, translate - w * 0.8f,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(android.graphics.ColorMatrix(adjustArray))
        return cm
    }
}
