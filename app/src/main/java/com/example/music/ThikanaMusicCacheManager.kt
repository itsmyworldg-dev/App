package com.example.music

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Intelligent local disk cache and media streaming server for Thikana music tracks.
 * Intercepts audio media requests, caches tracks to local storage, and supports HTTP 206
 * Range requests for instantaneous scrubbing and 100% offline playback.
 */
object ThikanaMusicCacheManager {
    private const val TAG = "ThikanaMusicCache"
    private const val MAX_CACHE_BYTES = 250L * 1024L * 1024L // 250 MB
    private var cacheDir: File? = null

    fun init(context: Context) {
        val dir = File(context.cacheDir, "thikana_music_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        cacheDir = dir
        trimCacheIfNeeded()
    }

    fun isAudioRequest(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("/api/media/") && (
            lower.contains("track_") ||
            lower.endsWith(".mpeg") ||
            lower.endsWith(".mp3") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".ogg") ||
            lower.endsWith(".wav") ||
            lower.endsWith(".aac")
        )
    }

    fun getCacheKey(url: String): String {
        val uri = Uri.parse(url)
        val lastSegment = uri.lastPathSegment
        if (!lastSegment.isNullOrBlank() && lastSegment.contains("track_")) {
            return lastSegment.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        }
        return md5(url) + ".mpeg"
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getCachedFile(url: String): File? {
        val dir = cacheDir ?: return null
        val file = File(dir, getCacheKey(url))
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Handles an intercepted audio web request. Returns a WebResourceResponse serving from
     * the local cache with byte range seeking support, or triggers background caching.
     */
    fun handleAudioRequest(request: WebResourceRequest?, url: String): WebResourceResponse? {
        val cachedFile = getCachedFile(url)

        val mimeType = when {
            url.endsWith(".mp3", true) || url.endsWith(".mpeg", true) -> "audio/mpeg"
            url.endsWith(".m4a", true) || url.endsWith(".aac", true) -> "audio/mp4"
            url.endsWith(".ogg", true) -> "audio/ogg"
            url.endsWith(".wav", true) -> "audio/wav"
            else -> "audio/mpeg"
        }

        // 1. Serving directly from completed local disk cache
        if (cachedFile != null && cachedFile.length() > 0) {
            val totalLength = cachedFile.length()
            val rangeHeader = request?.requestHeaders?.get("Range") ?: request?.requestHeaders?.get("range")

            if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
                val rangeSpec = rangeHeader.removePrefix("bytes=").trim()
                val parts = rangeSpec.split("-")
                val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                val end = parts.getOrNull(1)?.toLongOrNull() ?: (totalLength - 1L)
                val clampedEnd = minOf(end, totalLength - 1L)
                val contentLength = maxOf(0L, clampedEnd - start + 1L)

                try {
                    val fis = FileInputStream(cachedFile)
                    fis.channel.position(start)
                    val limitedStream = LimitedInputStream(fis, contentLength)

                    val headers = mapOf(
                        "Content-Type" to mimeType,
                        "Content-Range" to "bytes $start-$clampedEnd/$totalLength",
                        "Content-Length" to contentLength.toString(),
                        "Accept-Ranges" to "bytes",
                        "Access-Control-Allow-Origin" to "*",
                        "Cache-Control" to "public, max-age=31536000, immutable"
                    )

                    Log.d(TAG, "Serving CACHED audio range $start-$clampedEnd/$totalLength: $url")
                    return WebResourceResponse(mimeType, null, 206, "Partial Content", headers, limitedStream)
                } catch (e: Exception) {
                    Log.w(TAG, "Error serving range from cached audio", e)
                }
            }

            // Return full cached response
            try {
                val headers = mapOf(
                    "Content-Type" to mimeType,
                    "Content-Length" to totalLength.toString(),
                    "Accept-Ranges" to "bytes",
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "public, max-age=31536000, immutable"
                )
                Log.d(TAG, "Serving CACHED audio full (${totalLength} bytes): $url")
                return WebResourceResponse(mimeType, null, 200, "OK", headers, FileInputStream(cachedFile))
            } catch (e: Exception) {
                Log.w(TAG, "Error opening cached audio stream", e)
            }
        }

        // 2. Track is not yet cached on disk:
        // Trigger asynchronous background download so it's ready for repeat plays and future tracks
        preload(url)
        return null
    }

    /**
     * Proactively downloads and caches a music track in the background.
     */
    fun preload(url: String) {
        val dir = cacheDir ?: return
        val targetFile = File(dir, getCacheKey(url))
        if (targetFile.exists() && targetFile.length() > 0) {
            return // already cached
        }

        CoroutineScope(Dispatchers.IO).launch {
            val tempFile = File(dir, "${targetFile.name}.tmp_${System.currentTimeMillis()}")
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) ThikanaApp")
                }
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.length() > 0) {
                        tempFile.renameTo(targetFile)
                        Log.i(TAG, "Cached music track: ${targetFile.name} (${targetFile.length()} bytes)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Preload failed for $url: ${e.message}")
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    private fun trimCacheIfNeeded() {
        val dir = cacheDir ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val files = dir.listFiles() ?: return@launch
                var totalBytes = files.sumOf { it.length() }
                if (totalBytes > MAX_CACHE_BYTES) {
                    val sorted = files.sortedBy { it.lastModified() }
                    for (f in sorted) {
                        totalBytes -= f.length()
                        f.delete()
                        if (totalBytes <= MAX_CACHE_BYTES * 0.75) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cache trim error", e)
            }
        }
    }
}

class LimitedInputStream(private val wrapped: InputStream, private var remaining: Long) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0) return -1
        val b = wrapped.read()
        if (b != -1) remaining--
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val toRead = minOf(len.toLong(), remaining).toInt()
        val readBytes = wrapped.read(b, off, toRead)
        if (readBytes > 0) remaining -= readBytes
        return readBytes
    }

    override fun close() {
        wrapped.close()
    }
}
