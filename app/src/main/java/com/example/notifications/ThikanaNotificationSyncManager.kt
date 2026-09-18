package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Background notification sync engine that checks for new likes, comments,
 * follows, and direct messages using the WebView's authenticated session.
 * Ensures notifications are received in real-time even if FCM push delivery
 * is delayed or when the app is running in the background.
 */
object ThikanaNotificationSyncManager {
    private const val TAG = "ThikanaNotifSync"
    private const val PREFS_NAME = "thikana_notif_sync_prefs"
    private const val KEY_SEEN_NOTIF_IDS = "seen_notif_ids"
    private const val KEY_SEEN_MSG_IDS = "seen_msg_ids"
    private const val KEY_INITIAL_SYNC_DONE = "initial_sync_done"
    private const val KEY_CACHED_COOKIE = "cached_session_cookie"
    private const val ALARM_REQUEST_CODE = 44210

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    private var isSyncing = false
    private var isFirstSyncOfSession = true

    fun startPeriodicSync(context: Context) {
        val appContext = context.applicationContext
        scheduleBackgroundAlarm(appContext)

        if (syncJob?.isActive == true) return

        syncJob = scope.launch {
            Log.i(TAG, "Starting periodic notification background sync loop")
            // Small initial delay so app launch isn't competing with WebView initial render
            delay(3_000)
            while (isActive) {
                try {
                    syncNow(appContext)
                } catch (e: Exception) {
                    Log.w(TAG, "Sync loop exception: ${e.message}")
                }
                delay(25_000) // Poll every 25 seconds when app process is alive
            }
        }
    }

    /**
     * Schedules recurring Android AlarmManager wakeups so that notifications
     * continue arriving even if the app process was minimized, backgrounded, or dozing.
     */
    fun scheduleBackgroundAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ThikanaNotificationAlarmReceiver::class.java).apply {
                action = ThikanaNotificationAlarmReceiver.ACTION_ALARM_SYNC
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)

            // 15-minute interval for deep background doze / OS battery preservation
            val intervalMillis = AlarmManager.INTERVAL_FIFTEEN_MINUTES
            val triggerAtMillis = SystemClock.elapsedRealtime() + 60_000L // 1 minute after start

            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                intervalMillis,
                pendingIntent
            )
            Log.d(TAG, "Scheduled background AlarmManager sync for notifications")
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule AlarmManager: ${e.message}")
        }
    }

    /**
     * Records an id as already-alerted so the periodic poller above (syncNotifications/
     * syncDirectMessages) doesn't fire a second, duplicate system notification for the
     * same item a few seconds later — used by ThikanaFirebaseMessagingService right after
     * it shows (or suppresses) a notification for an instant FCM push, since that path
     * writes to Android's notification tray directly and previously had no way of telling
     * this poller "I already handled this one."
     */
    fun markNotificationSeen(context: Context, id: String) {
        if (id.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val seenIds = (prefs.getStringSet(KEY_SEEN_NOTIF_IDS, emptySet()) ?: emptySet()).toMutableSet()
        seenIds.add(id)
        val trimmed = if (seenIds.size > 150) seenIds.toList().takeLast(150).toSet() else seenIds
        prefs.edit().putStringSet(KEY_SEEN_NOTIF_IDS, trimmed).apply()
    }

    /** Same as markNotificationSeen but for the direct-message id cache. */
    fun markMessageSeen(context: Context, id: String) {
        if (id.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val seenIds = (prefs.getStringSet(KEY_SEEN_MSG_IDS, emptySet()) ?: emptySet()).toMutableSet()
        seenIds.add(id)
        val trimmed = if (seenIds.size > 150) seenIds.toList().takeLast(150).toSet() else seenIds
        prefs.edit().putStringSet(KEY_SEEN_MSG_IDS, trimmed).apply()
    }

    fun stopPeriodicSync() {
        // We do NOT cancel the alarm here so that notifications continue to sync in background
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * Caches current session cookies so background sync works even when WebView instance is closed.
     */
    fun saveSessionCookie(context: Context, cookie: String) {
        if (cookie.isNotBlank()) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CACHED_COOKIE, cookie).apply()
        }
    }

    fun syncNow(context: Context) {
        if (isSyncing) return
        val appContext = context.applicationContext

        scope.launch {
            isSyncing = true
            try {
                val cookie = getSessionCookie(appContext)
                if (cookie.isBlank()) {
                    // User not logged in on web view yet
                    return@launch
                }

                syncNotifications(appContext, cookie)
                syncDirectMessages(appContext, cookie)
                isFirstSyncOfSession = false
            } catch (e: Exception) {
                Log.w(TAG, "Notification sync failed: ${e.message}")
            } finally {
                isSyncing = false
            }
        }
    }

    private fun getSessionCookie(context: Context): String {
        return try {
            val liveCookie = CookieManager.getInstance().getCookie("https://thikana.pages.dev")
            if (!liveCookie.isNullOrBlank()) {
                saveSessionCookie(context, liveCookie)
                liveCookie
            } else {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getString(KEY_CACHED_COOKIE, "") ?: ""
            }
        } catch (_: Exception) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_CACHED_COOKIE, "") ?: ""
        }
    }

    private fun syncNotifications(context: Context, cookie: String) {
        try {
            val url = URL("https://thikana.pages.dev/api/notifications")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Cookie", cookie)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (conn.responseCode != 200) {
                conn.disconnect()
                return
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseText)
            val totalUnread = json.optInt("total_unread", 0)
            ThikanaBadgeManager.updateNotifications(totalUnread)
            if (totalUnread <= 0) return

            val notifsArray = json.optJSONArray("notifications") ?: return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val seenIds = (prefs.getStringSet(KEY_SEEN_NOTIF_IDS, emptySet()) ?: emptySet()).toMutableSet()
            val isInitialSyncDone = prefs.getBoolean(KEY_INITIAL_SYNC_DONE, false)
            var newlySeen = false

            // If this is the very first time the app is syncing notifications on this device,
            // seed the existing unread notifications so they do not trigger a barrage of alerts upon opening!
            if (!isInitialSyncDone || (seenIds.isEmpty() && isFirstSyncOfSession)) {
                for (i in 0 until notifsArray.length()) {
                    val n = notifsArray.getJSONObject(i)
                    val id = n.optString("id")
                    if (id.isNotBlank()) {
                        seenIds.add(id)
                    }
                }
                prefs.edit()
                    .putBoolean(KEY_INITIAL_SYNC_DONE, true)
                    .putStringSet(KEY_SEEN_NOTIF_IDS, seenIds.toList().takeLast(150).toSet())
                    .apply()
                Log.d(TAG, "Seeded initial ${seenIds.size} notification IDs without alerting on first run")
                return
            }

            // Check if app is in active foreground - if so, only update badges and web UI, do not annoy user with system popup notifications
            val isAppInForeground = com.example.MainActivity.currentInstance?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true

            for (i in 0 until notifsArray.length()) {
                val n = notifsArray.getJSONObject(i)
                val id = n.optString("id")
                val isRead = n.optBoolean("read", false)

                if (id.isBlank() || isRead || seenIds.contains(id)) {
                    continue
                }

                // New unread notification!
                seenIds.add(id)
                newlySeen = true

                // If user is actively using the app, skip posting noisy system status-bar notifications
                if (isAppInForeground) {
                    continue
                }

                val type = n.optString("type", "social")
                val actorObj = n.optJSONObject("actor")
                val actorName = actorObj?.optString("display_name")
                    ?.takeIf { it.isNotBlank() }
                    ?: actorObj?.optString("handle")
                    ?: "Someone"

                val placeName = n.optString("place_name", "")
                val placeId = n.optString("place_id", "")
                val preview = n.optString("preview", "")

                val bodyText = when (type.lowercase()) {
                    "like" -> if (placeName.isNotBlank()) "$actorName liked your post at $placeName" else "$actorName liked your post"
                    "comment" -> if (placeName.isNotBlank()) "$actorName commented on your post at $placeName" else "$actorName commented on your post"
                    "follow" -> "$actorName started following you"
                    "trending" -> if (placeName.isNotBlank()) "Your thikana $placeName is trending right now 🔥" else "A thikana is trending right now 🔥"
                    "message" -> if (preview.isNotBlank()) "$actorName: $preview" else "$actorName sent you a message"
                    else -> "$actorName sent you an update"
                }

                val dataMap = mutableMapOf(
                    "id" to id,
                    "type" to type
                )
                if (placeId.isNotBlank()) {
                    dataMap["place_id"] = placeId
                    dataMap["url"] = "https://thikana.pages.dev/?place=$placeId"
                }
                actorObj?.optString("id")?.let { actorId ->
                    if (actorId.isNotBlank()) {
                        dataMap["actor_id"] = actorId
                        if (type == "follow") {
                            dataMap["url"] = "https://thikana.pages.dev/?user=$actorId"
                        } else if (type == "message") {
                            dataMap["thread_id"] = actorId
                            dataMap["url"] = "https://thikana.pages.dev/?thread=$actorId"
                        }
                    }
                }

                NotificationHelper.showNotification(
                    context = context,
                    title = "Mera Thikaana",
                    body = bodyText,
                    type = type,
                    data = dataMap,
                    customId = id.hashCode()
                )
            }

            if (newlySeen) {
                // Keep only the most recent 150 IDs to prevent memory growth
                val trimmed = if (seenIds.size > 150) seenIds.toList().takeLast(150).toSet() else seenIds
                prefs.edit().putStringSet(KEY_SEEN_NOTIF_IDS, trimmed).apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking /api/notifications: ${e.message}")
        }
    }

    private fun syncDirectMessages(context: Context, cookie: String) {
        try {
            val url = URL("https://thikana.pages.dev/api/messages")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Cookie", cookie)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (conn.responseCode != 200) {
                conn.disconnect()
                return
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseText)
            val totalUnread = json.optInt("total_unread", 0)
            ThikanaBadgeManager.updateMessages(totalUnread)
            if (totalUnread <= 0) return

            val convos = json.optJSONArray("conversations") ?: return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val seenMsgIds = (prefs.getStringSet(KEY_SEEN_MSG_IDS, emptySet()) ?: emptySet()).toMutableSet()
            val isInitialSyncDone = prefs.getBoolean(KEY_INITIAL_SYNC_DONE, false)
            var newlySeen = false

            // Seed existing message IDs on initial sync so old messages don't pop notifications when opening the app
            if (!isInitialSyncDone || (seenMsgIds.isEmpty() && isFirstSyncOfSession)) {
                for (i in 0 until convos.length()) {
                    val c = convos.getJSONObject(i)
                    val lastMsg = c.optJSONObject("last_message")
                    val msgId = lastMsg?.optString("id")
                    if (!msgId.isNullOrBlank()) {
                        seenMsgIds.add(msgId)
                    }
                }
                prefs.edit().putStringSet(KEY_SEEN_MSG_IDS, seenMsgIds.toList().takeLast(150).toSet()).apply()
                Log.d(TAG, "Seeded initial ${seenMsgIds.size} message IDs without alerting on first run")
                return
            }

            // Check if app is in active foreground
            val isAppInForeground = com.example.MainActivity.currentInstance?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true

            for (i in 0 until convos.length()) {
                val c = convos.getJSONObject(i)
                val unreadCount = c.optInt("unread_count", 0)
                if (unreadCount <= 0) continue

                val lastMsg = c.optJSONObject("last_message") ?: continue
                val msgId = lastMsg.optString("id")
                if (msgId.isBlank() || seenMsgIds.contains(msgId)) continue

                seenMsgIds.add(msgId)
                newlySeen = true

                // Suppress posting notifications if user is already actively using the app
                if (isAppInForeground) {
                    continue
                }

                val otherUser = c.optJSONObject("other_user")
                val senderName = otherUser?.optString("display_name")
                    ?.takeIf { it.isNotBlank() }
                    ?: otherUser?.optString("handle")
                    ?: c.optString("title", "New Message")

                val msgText = lastMsg.optString("text", "Sent you a message")
                val threadId = otherUser?.optString("id") ?: c.optString("id")

                val dataMap = mutableMapOf(
                    "id" to msgId,
                    "type" to "chat",
                    "thread_id" to threadId,
                    "url" to "https://thikana.pages.dev/?thread=$threadId"
                )

                NotificationHelper.showNotification(
                    context = context,
                    title = senderName,
                    body = msgText,
                    type = "chat",
                    data = dataMap,
                    customId = msgId.hashCode()
                )
            }

            if (newlySeen) {
                val trimmed = if (seenMsgIds.size > 150) seenMsgIds.toList().takeLast(150).toSet() else seenMsgIds
                prefs.edit().putStringSet(KEY_SEEN_MSG_IDS, trimmed).apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking /api/messages: ${e.message}")
        }
    }
}
