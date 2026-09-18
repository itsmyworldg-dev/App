package com.example.storyeditor.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.storyeditor.model.AspectPreset
import com.example.storyeditor.model.BackgroundCutoutMode
import java.io.File
import java.io.FileOutputStream

object ImageProcessingUtils {

    fun cropToAspect(source: Bitmap, preset: AspectPreset): Bitmap {
        val targetRatio = preset.ratio
        val srcWidth = source.width
        val srcHeight = source.height
        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()

        val cropWidth: Int
        val cropHeight: Int

        if (srcRatio > targetRatio) {
            // Source is wider than target
            cropHeight = srcHeight
            cropWidth = (srcHeight * targetRatio).toInt().coerceAtMost(srcWidth)
        } else {
            // Source is taller than target
            cropWidth = srcWidth
            cropHeight = (srcWidth / targetRatio).toInt().coerceAtMost(srcHeight)
        }

        val startX = (srcWidth - cropWidth) / 2
        val startY = (srcHeight - cropHeight) / 2

        return Bitmap.createBitmap(source, startX, startY, cropWidth, cropHeight)
    }

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun flipBitmap(source: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val matrix = Matrix().apply {
            postScale(
                if (horizontal) -1f else 1f,
                if (vertical) -1f else 1f
            )
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Smart subject cutout: identifies foreground subject via saliency and center-weighted
     * color variance, generating an alpha cutout mask with smooth feathering.
     */
    fun extractSubjectCutout(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // Sample corner background colors to determine backdrop tone
        val cornerSamples = listOf(
            pixels[0],
            pixels[width - 1],
            pixels[(height - 1) * width],
            pixels[(height - 1) * width + width - 1],
            pixels[width / 2],
            pixels[(height - 1) * width + width / 2]
        )

        var avgBgR = 0
        var avgBgG = 0
        var avgBgB = 0
        for (c in cornerSamples) {
            avgBgR += (c shr 16) and 0xFF
            avgBgG += (c shr 8) and 0xFF
            avgBgB += c and 0xFF
        }
        avgBgR /= cornerSamples.size
        avgBgG /= cornerSamples.size
        avgBgB /= cornerSamples.size

        val centerX = width / 2f
        val centerY = height / 2f
        val maxDist = Math.hypot(centerX.toDouble(), centerY.toDouble()).toFloat()

        val outputPixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val pixel = pixels[index]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Color difference from background corners
                val colorDiff = Math.abs(r - avgBgR) + Math.abs(g - avgBgG) + Math.abs(b - avgBgB)

                // Distance from center (foreground subject usually centers the frame)
                val distFromCenter = Math.hypot((x - centerX).toDouble(), (y - centerY).toDouble()).toFloat()
                val centerWeight = (1.0f - (distFromCenter / maxDist).coerceIn(0f, 1f)) * 0.45f

                // Combined probability score for foreground
                val foregroundScore = (colorDiff / 255f) + centerWeight

                val alpha = when {
                    foregroundScore > 0.45f -> 255
                    foregroundScore < 0.25f -> 0
                    else -> (((foregroundScore - 0.25f) / 0.20f) * 255).toInt().coerceIn(0, 255)
                }

                outputPixels[index] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        output.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Save bitmap to app cache directory and return a secure content:// URI
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileNamePrefix: String = "story_edit"): Uri? {
        return try {
            val cacheImagesDir = File(context.cacheDir, "images")
            if (!cacheImagesDir.exists()) cacheImagesDir.mkdirs()

            val file = File(cacheImagesDir, "${fileNamePrefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Share directly to Instagram Stories or fallback to Android Share Sheet
     */
    fun shareStory(context: Context, uri: Uri) {
        val instagramIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "image/jpeg")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra("source_application", context.packageName)
        }

        val packageManager = context.packageManager
        if (instagramIntent.resolveActivity(packageManager) != null) {
            context.startActivity(instagramIntent)
        } else {
            // General share sheet
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Story to..."))
            Toast.makeText(context, "Opening share sheet", Toast.LENGTH_SHORT).show()
        }
    }
}
