package com.example.editor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ImageSaveUtil {

    suspend fun renderFinalBitmap(
        context: Context,
        template: CollageTemplate,
        slots: List<CollageSlotData>,
        basePreset: FilterPreset,
        adjustments: ImageAdjustments,
        strokes: List<DrawnStroke>,
        stickers: List<BadgeSticker>,
        textStickers: List<TextSticker>,
        isStoryRatio: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        val targetWidth = 1080
        val targetHeight = if (isStoryRatio) 1920 else 1080

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.BLACK)

        val cm = adjustments.buildAndroidCombinedMatrix(basePreset)
        val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }

        // 1. Draw base photo(s) / collage
        when (template) {
            CollageTemplate.SINGLE -> {
                val bmp = slots.firstOrNull()?.bitmap
                if (bmp != null) {
                    drawScaledBitmap(canvas, bmp, RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat()), filterPaint)
                }
            }

            CollageTemplate.SPLIT_VERTICAL_2 -> {
                val halfW = targetWidth / 2f
                slots.getOrNull(0)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(0f, 0f, halfW - 4f, targetHeight.toFloat()), filterPaint)
                }
                slots.getOrNull(1)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(halfW + 4f, 0f, targetWidth.toFloat(), targetHeight.toFloat()), filterPaint)
                }
            }

            CollageTemplate.SPLIT_HORIZONTAL_2 -> {
                val halfH = targetHeight / 2f
                slots.getOrNull(0)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(0f, 0f, targetWidth.toFloat(), halfH - 4f), filterPaint)
                }
                slots.getOrNull(1)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(0f, halfH + 4f, targetWidth.toFloat(), targetHeight.toFloat()), filterPaint)
                }
            }

            CollageTemplate.SPLIT_3_TOP_HERO -> {
                val topH = targetHeight * 0.55f
                val halfW = targetWidth / 2f
                slots.getOrNull(0)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(0f, 0f, targetWidth.toFloat(), topH - 4f), filterPaint)
                }
                slots.getOrNull(1)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(0f, topH + 4f, halfW - 4f, targetHeight.toFloat()), filterPaint)
                }
                slots.getOrNull(2)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(halfW + 4f, topH + 4f, targetWidth.toFloat(), targetHeight.toFloat()), filterPaint)
                }
            }

            CollageTemplate.SPLIT_3_LEFT_HERO -> {
                val leftW = targetWidth * 0.55f
                val halfH = targetHeight / 2f
                slots.getOrNull(0)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(0f, 0f, leftW - 4f, targetHeight.toFloat()), filterPaint)
                }
                slots.getOrNull(1)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(leftW + 4f, 0f, targetWidth.toFloat(), halfH - 4f), filterPaint)
                }
                slots.getOrNull(2)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(leftW + 4f, halfH + 4f, targetWidth.toFloat(), targetHeight.toFloat()), filterPaint)
                }
            }

            CollageTemplate.GRID_4 -> {
                val halfW = targetWidth / 2f
                val halfH = targetHeight / 2f
                slots.getOrNull(0)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(0f, 0f, halfW - 4f, halfH - 4f), filterPaint)
                }
                slots.getOrNull(1)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(halfW + 4f, 0f, targetWidth.toFloat(), halfH - 4f), filterPaint)
                }
                slots.getOrNull(2)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(0f, halfH + 4f, halfW - 4f, targetHeight.toFloat()), filterPaint)
                }
                slots.getOrNull(3)?.bitmap?.let {
                    drawScaledBitmap(canvas, it, RectF(halfW + 4f, halfH + 4f, targetWidth.toFloat(), targetHeight.toFloat()), filterPaint)
                }
            }
        }

        // 2. Draw freehand doodle strokes
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            strokePaint.color = stroke.color.toArgbCompat()
            // Scale stroke width proportionally to the high-res canvas
            strokePaint.strokeWidth = stroke.strokeWidth * (targetWidth / 400f)
            if (stroke.points.size > 1) {
                val path = android.graphics.Path()
                val p0 = stroke.points[0]
                path.moveTo(p0.x * (targetWidth / 400f), p0.y * (targetHeight / 700f))
                for (i in 1 until stroke.points.size) {
                    val p = stroke.points[i]
                    path.lineTo(p.x * (targetWidth / 400f), p.y * (targetHeight / 700f))
                }
                canvas.drawPath(path, strokePaint)
            }
        }

        // 3. Draw Badge Stickers
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = targetWidth * 0.045f
            isFakeBoldText = true
            color = android.graphics.Color.BLACK
        }
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = targetWidth * 0.055f
        }

        stickers.forEach { sticker ->
            val cx = sticker.offsetXFraction * targetWidth
            val cy = sticker.offsetYFraction * targetHeight

            val label = "${sticker.iconEmoji} ${sticker.title}"
            val textBounds = Rect()
            badgeTextPaint.getTextBounds(label, 0, label.length, textBounds)
            val paddingH = targetWidth * 0.035f
            val paddingV = targetHeight * 0.012f

            val rectF = RectF(
                cx - textBounds.width() / 2f - paddingH,
                cy - textBounds.height() / 2f - paddingV,
                cx + textBounds.width() / 2f + paddingH,
                cy + textBounds.height() / 2f + paddingV
            )
            canvas.drawRoundRect(rectF, 30f, 30f, badgeBgPaint)

            canvas.drawText(
                label,
                cx - textBounds.width() / 2f,
                cy + textBounds.height() / 2f - 4f,
                badgeTextPaint
            )
        }

        // 4. Draw Text Stickers
        textStickers.forEach { textSticker ->
            val cx = textSticker.offsetXFraction * targetWidth
            val cy = textSticker.offsetYFraction * targetHeight

            val tPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = (textSticker.fontSizeSp * 2.8f).coerceAtLeast(40f)
                isFakeBoldText = textSticker.isBold
                color = textSticker.textColor.toArgbCompat()
            }

            val tBounds = Rect()
            tPaint.getTextBounds(textSticker.text, 0, textSticker.text.length, tBounds)

            val pillBg = textSticker.backgroundColor
            if (pillBg != null) {
                val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = pillBg.toArgbCompat()
                    style = Paint.Style.FILL
                }
                val pH = targetWidth * 0.03f
                val pV = targetHeight * 0.01f
                val pillRect = RectF(
                    cx - tBounds.width() / 2f - pH,
                    cy - tBounds.height() / 2f - pV,
                    cx + tBounds.width() / 2f + pH,
                    cy + tBounds.height() / 2f + pV
                )
                canvas.drawRoundRect(pillRect, 24f, 24f, bgP)
            }

            canvas.drawText(
                textSticker.text,
                cx - tBounds.width() / 2f,
                cy + tBounds.height() / 2f - 4f,
                tPaint
            )
        }

        output
    }

    private fun drawScaledBitmap(canvas: Canvas, src: Bitmap, dst: RectF, paint: Paint) {
        val srcRect = Rect(0, 0, src.width, src.height)
        canvas.drawBitmap(src, srcRect, dst, paint)
    }

    suspend fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "story_export_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        try {
            val filename = "THIKANA_STORY_${System.currentTimeMillis()}.jpg"
            var fos: OutputStream? = null
            var imageUri: Uri? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Thikaana")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos!!)
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/Thikaana"
                val fileDir = File(imagesDir)
                if (!fileDir.exists()) fileDir.mkdirs()
                val image = File(fileDir, filename)
                fos = FileOutputStream(image)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                imageUri = Uri.fromFile(image)
            }
            fos?.close()
            imageUri
        } catch (e: Exception) {
            null
        }
    }

    fun launchShareIntent(context: Context, imageUri: Uri, caption: String = "Made on Mera Thikaana ✨") {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Story to..."))
    }
}

private fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
