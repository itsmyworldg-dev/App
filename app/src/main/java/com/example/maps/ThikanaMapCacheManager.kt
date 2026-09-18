package com.example.maps

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Local disk cache for Mappls map tiles, styles, sprites and glyphs.
 *
 * Without this, the WebGL vector map re-fetches every tile/style asset on
 * every cold app launch (WebView's own HTTP cache is small and easily
 * evicted, and a lot of tile responses aren't served with cache headers
 * generous enough to survive a fresh process anyway). This intercepts those
 * requests, serves them instantly from disk once cached, and only ever hits
 * the network again after the on-disk copy expires.
 *
 * shouldInterceptRequest MUST return synchronously, so a cache-miss fetch
 * necessarily blocks whatever thread WebView called this on for as long as
 * the network takes. WebView only hands out a small, shared pool of those
 * threads across every intercepted resource (map tiles, images, scripts,
 * fonts, ...), so anything that makes an individual fetch slow doesn't just
 * delay that one tile — it holds a thread the *next* queued request (map or
 * otherwise) is waiting on. A brand-new plain HttpURLConnection per request
 * pays a full DNS + TCP + TLS handshake every single time, which is exactly
 * the kind of slow fetch that causes that pile-up: tap the map while a page
 * is still loading other resources (so the shared pool is already busy) and
 * a burst of 20-30 first-time tile/sprite/glyph requests each doing their
 * own handshake can easily blow past whatever timeout the map's JS uses to
 * give up and show an error — even though every request would have
 * succeeded individually given enough time. Wait a moment before opening the
 * map and the earlier page load has drained out of that shared pool (and by
 * then a connection to the tile host often already exists from those earlier
 * requests), so the same tiles come back fast.
 *
 * A shared, warm OkHttpClient (connection pooling + HTTP/2 multiplexing)
 * fixes this at the root: every fetch after the first reuses an
 * already-established connection instead of re-handshaking, so each
 * individual shouldInterceptRequest call — and therefore the shared thread
 * it's occupying — finishes much faster under exactly the bursty, contended
 * conditions a quick tap right after launch creates.
 */
object ThikanaMapCacheManager {
    private const val TAG = "ThikanaMapCache"
    private const val MAX_CACHE_BYTES = 150L * 1024L * 1024L // 150 MB
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days - map tiles rarely change

    // Hosts that serve Mappls map tiles, vector styles, sprites, glyphs and fonts.
    // Kept as a set (rather than one fixed host) since Mappls spreads these
    // across a few subdomains (apis., atlas., vector., etc).
    private val MAP_HOST_SUFFIXES = listOf(
        "mappls.com",
        "mapmyindia.com"
    )

    private var cacheDir: File? = null

    // Shared across every tile/style/sprite/glyph fetch so they all pool
    // connections (and multiplex over HTTP/2 where the host supports it)
    // instead of each paying its own DNS+TCP+TLS handshake. 24 idle
    // connections comfortably covers the handful of map subdomains this app
    // talks to, even with several tabs' worth of tiles in flight at once.
    private val httpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(24, 5, TimeUnit.MINUTES))
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        val dir = File(context.cacheDir, "thikana_map_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        cacheDir = dir
        trimCacheIfNeeded()
        prewarmConnections()
    }

    /**
     * Fires a couple of throwaway background requests to the map tile hosts
     * right away (app start), so the DNS lookup + TCP + TLS handshake is
     * already done and sitting warm in httpClient's connection pool by the
     * time the user actually opens the map — instead of paying that cost for
     * the first time exactly when they tap it. This is what turns "wait a
     * moment, then it works" into "works on the first tap" too.
     */
    private fun prewarmConnections() {
        Thread {
            for (host in MAP_HOST_SUFFIXES) {
                try {
                    val request = Request.Builder()
                        .url("https://$host/")
                        .head()
                        .build()
                    httpClient.newCall(request).execute().close()
                } catch (_: Exception) {
                    // Best-effort only — a failed prewarm just means the first
                    // real tile request pays the handshake cost as before.
                }
            }
        }.start()
    }

    fun isMapTileRequest(url: String): Boolean {
        val host = try {
            java.net.URL(url).host?.lowercase() ?: return false
        } catch (e: Exception) {
            return false
        }
        return MAP_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun cacheFileFor(url: String): File? {
        val dir = cacheDir ?: return null
        // Extension is cosmetic here (we always know the real mime type from
        // the original response), but keeping one around makes the cache
        // directory easier to eyeball while debugging.
        val ext = url.substringAfterLast('.', "").substringBefore('?').take(5)
        val suffix = if (ext.isNotBlank() && ext.length <= 4 && ext.matches(Regex("[a-zA-Z0-9]+"))) ".$ext" else ""
        return File(dir, md5(url) + suffix)
    }

    private fun metaFileFor(cachedFile: File): File = File(cachedFile.parentFile, cachedFile.name + ".meta")

    /**
     * Handles an intercepted map-related request. Returns a WebResourceResponse
     * served from disk (fetching and persisting it first if this is the first
     * time we've seen this URL, or if the on-disk copy has aged out), or null
     * to let the WebView fall back to its normal network path if caching fails.
     */
    fun handleMapRequest(request: WebResourceRequest?, url: String): WebResourceResponse? {
        // Only GET requests are safely cacheable this way.
        if (request != null && !request.method.equals("GET", ignoreCase = true)) {
            return null
        }

        val cachedFile = cacheFileFor(url) ?: return null
        val metaFile = metaFileFor(cachedFile)

        if (cachedFile.exists() && cachedFile.length() > 0) {
            val age = System.currentTimeMillis() - cachedFile.lastModified()
            if (age < MAX_AGE_MS) {
                val mimeType = readMime(metaFile) ?: guessMimeType(url)
                return try {
                    Log.d(TAG, "Serving CACHED map asset: $url")
                    WebResourceResponse(
                        mimeType,
                        null,
                        200,
                        "OK",
                        mapOf(
                            "Access-Control-Allow-Origin" to "*",
                            "Cache-Control" to "public, max-age=2592000"
                        ),
                        FileInputStream(cachedFile)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error opening cached map asset, re-fetching", e)
                    fetchAndCache(url, cachedFile, metaFile)
                }
            }
        }

        // Not cached (or expired): fetch synchronously so the map still gets
        // a response this call, and persist it for every future launch.
        return fetchAndCache(url, cachedFile, metaFile)
    }

    private fun fetchAndCache(url: String, cachedFile: File, metaFile: File): WebResourceResponse? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) ThikanaApp")
                .header("Referer", "https://thikana.pages.dev/")
                .build()
            val response = httpClient.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) {
                    // Serve a stale cached copy rather than nothing if the network hiccups.
                    if (cachedFile.exists() && cachedFile.length() > 0) {
                        val mimeType = readMime(metaFile) ?: guessMimeType(url)
                        return WebResourceResponse(mimeType, null, 200, "OK", null, FileInputStream(cachedFile))
                    }
                    return null
                }

                val mimeType = it.body?.contentType()?.toString()?.substringBefore(';')?.trim()
                    .takeUnless { m -> m.isNullOrBlank() } ?: guessMimeType(url)
                val bytes = it.body?.bytes() ?: ByteArray(0)

                val dir = cachedFile.parentFile
                if (dir != null && !dir.exists()) dir.mkdirs()
                FileOutputStream(cachedFile).use { out -> out.write(bytes) }
                metaFile.writeText(mimeType)

                Log.d(TAG, "Cached fresh map asset (${bytes.size} bytes): $url")
                WebResourceResponse(
                    mimeType,
                    null,
                    200,
                    "OK",
                    mapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Cache-Control" to "public, max-age=2592000"
                    ),
                    ByteArrayInputStream(bytes)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Map asset fetch failed for $url: ${e.message}")
            // Offline or network error: fall back to a stale cached copy if we have one
            // so the map still renders something instead of blank/missing tiles.
            if (cachedFile.exists() && cachedFile.length() > 0) {
                val mimeType = readMime(metaFile) ?: guessMimeType(url)
                try {
                    return WebResourceResponse(mimeType, null, 200, "OK", null, FileInputStream(cachedFile))
                } catch (_: Exception) {
                }
            }
            null
        }
    }

    private fun readMime(metaFile: File): String? =
        try {
            if (metaFile.exists()) metaFile.readText().trim().takeIf { it.isNotBlank() } else null
        } catch (e: Exception) {
            null
        }

    private fun guessMimeType(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".pbf") || path.endsWith(".mvt") -> "application/x-protobuf"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".js") || url.contains("map_sdk") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            else -> "application/octet-stream"
        }
    }

    private fun trimCacheIfNeeded() {
        val dir = cacheDir ?: return
        Thread {
            try {
                val files = dir.listFiles()?.filterNot { it.name.endsWith(".meta") } ?: return@Thread
                var totalBytes = files.sumOf { it.length() }
                if (totalBytes > MAX_CACHE_BYTES) {
                    val sorted = files.sortedBy { it.lastModified() }
                    for (f in sorted) {
                        totalBytes -= f.length()
                        File(f.parentFile, f.name + ".meta").delete()
                        f.delete()
                        if (totalBytes <= MAX_CACHE_BYTES * 0.75) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cache trim error", e)
            }
        }.start()
    }
}
