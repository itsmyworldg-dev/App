package com.example

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.system.Os
import android.util.Log
import android.webkit.WebView
import java.io.File

class ThikanaApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        applyMesaSuppression()
    }

    override fun onCreate() {
        super.onCreate()
        prepareEnvironment(this)
        com.example.notifications.NotificationHelper.setupNotificationChannels(this)
        com.example.notifications.ThikanaNotificationSyncManager.scheduleBackgroundAlarm(this)
    }

    companion object {
        private const val TAG = "ThikanaApplication"

        init {
            applyMesaSuppression()
        }

        fun applyMesaSuppression() {
            try {
                // Unset any environment variables that force kms_swrast or override Mesa drivers.
                // Setting MESA_LOADER_DRIVER_OVERRIDE or LIBGL_ALWAYS_SOFTWARE forces Mesa's DRI loader
                // to probe for non-existent /dev/dri/renderD* nodes, generating "Failed to open rendernode".
                try { Os.unsetenv("LIBGL_ALWAYS_SOFTWARE") } catch (_: Throwable) {}
                try { Os.unsetenv("GALLIUM_DRIVER") } catch (_: Throwable) {}
                try { Os.unsetenv("LIBGL_DRI3_DISABLE") } catch (_: Throwable) {}
                try { Os.unsetenv("LIBGL_DRI2_DISABLE") } catch (_: Throwable) {}
                try { Os.unsetenv("LIBGL_KMS_DRIVER") } catch (_: Throwable) {}
                try { Os.unsetenv("DRI_PRIME") } catch (_: Throwable) {}
                try { Os.unsetenv("ANDROID_EMULATOR_USE_SYSTEM_LIBS") } catch (_: Throwable) {}

                // Silence any diagnostic logging if Mesa is internally queried by the graphics stack
                Os.setenv("MESA_LOG_FILE", "/dev/null", true)
                Os.setenv("MESA_DEBUG", "0", true)
                Os.setenv("MESA_NO_ERROR", "1", true)
                Os.setenv("MESA_SILENT", "1", true)
                Os.setenv("MESA_VERBOSE", "0", true)
                Os.setenv("EGL_LOG_LEVEL", "fatal", true)
                Os.setenv("LIBGL_DEBUG", "quiet", true)
                Os.setenv("VK_LOADER_DEBUG", "none", true)
                Os.setenv("LIBGL_SHOW_FPS", "0", true)

                System.setProperty("mesa.debug", "0")
                System.setProperty("egl.log_level", "fatal")
            } catch (_: Throwable) {}
        }

        fun prepareEnvironment(context: Context) {
            // 1. Ensure Mesa environment flags are applied
            applyMesaSuppression()

            // 2. Set WebView data directory suffix for multi-process safety (Android P+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val processName = getProcessName()
                    if (processName != context.packageName) {
                        WebView.setDataDirectorySuffix(processName)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "WebView suffix setup: ${e.message}")
                }
            }

            // 3. Pre-create and fix legitimate Chromium / WebView disk cache directory hierarchies.
            // Chromium SimpleCache expects:
            //   - "HTTP Cache" to be a flat SimpleCache directory (or with its expected subfolders)
            //   - "HTTP Cache/Code Cache/js" and "HTTP Cache/Code Cache/wasm" to exist or not have dangling indexes
            //   - "Code Cache/js" and "Code Cache/wasm" to exist
            try {
                val cacheBase = context.cacheDir

                val directories = listOf(
                    File(cacheBase, "WebView"),
                    File(cacheBase, "WebView/Default"),
                    File(cacheBase, "WebView/Default/HTTP Cache"),
                    File(cacheBase, "WebView/Default/HTTP Cache/Code Cache"),
                    File(cacheBase, "WebView/Default/HTTP Cache/Code Cache/js"),
                    File(cacheBase, "WebView/Default/HTTP Cache/Code Cache/wasm"),
                    File(cacheBase, "WebView/Default/Code Cache"),
                    File(cacheBase, "WebView/Default/Code Cache/js"),
                    File(cacheBase, "WebView/Default/Code Cache/wasm"),
                    File(cacheBase, "WebView/Default/Service Worker"),
                    File(cacheBase, "WebView/Default/Service Worker/CacheStorage"),
                    File(cacheBase, "WebView/Default/Service Worker/ScriptCache")
                )

                for (dir in directories) {
                    if (dir.exists() && !dir.isDirectory) {
                        dir.delete()
                    }
                    if (!dir.exists()) {
                        dir.mkdirs()
                    }
                    // Grant full read/write/execute permissions to owner, group, and sandboxed UIDs
                    dir.setReadable(true, false)
                    dir.setWritable(true, false)
                    dir.setExecutable(true, false)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "WebView cache directory prep: ${e.message}")
            }
        }
    }
}
