package com.example.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class ThikanaFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "ThikanaFCM"
        private const val PREFS_NAME = "thikana_fcm_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"

        fun getSavedToken(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_FCM_TOKEN, null)
        }

        fun saveToken(context: Context, token: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
        }

        /**
         * Sends device token to backend server asynchronously so push notifications can reach this device.
         */
        fun syncTokenWithServer(context: Context, token: String, userId: String? = null) {
            thread {
                try {
                    val apiUrl = "https://thikana.pages.dev/api/save-fcm-token"
                    val url = URL(apiUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    
                    // Attach authenticated session cookie from WebView so backend authorizes the token
                    try {
                        val cookieManager = android.webkit.CookieManager.getInstance()
                        val cookie = cookieManager.getCookie("https://thikana.pages.dev")
                        if (!cookie.isNullOrBlank()) {
                            conn.setRequestProperty("Cookie", cookie)
                        }
                    } catch (ce: Exception) {
                        Log.w(TAG, "Cookie lookup warning: ${ce.message}")
                    }

                    conn.doOutput = true
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000

                    val payload = JSONObject().apply {
                        put("token", token)
                        put("platform", "android")
                        if (!userId.isNullOrBlank()) {
                            put("userId", userId)
                        }
                    }

                    conn.outputStream.use { os ->
                        os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }

                    val code = conn.responseCode
                    Log.d(TAG, "Token sync response code: $code")
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not sync FCM token to server yet: ${e.message}")
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New Firebase Cloud Messaging Token generated: $token")
        saveToken(applicationContext, token)
        syncTokenWithServer(applicationContext, token)

        // If app is currently open, inform WebView
        MainActivity.currentInstance?.let { activity ->
            activity.runOnUiThread {
                activity.notifyFcmTokenUpdated(token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Push message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val title = notification?.title
            ?: data["title"]
            ?: data["header"]
            ?: "Mera Thikaana"

        val body = notification?.body
            ?: data["body"]
            ?: data["message"]
            ?: data["text"]
            ?: "You have a new update."

        val type = data["type"] ?: "message"

        // If app is in foreground and user is actively in the conversation, notify web UI
        MainActivity.currentInstance?.let { activity ->
            activity.runOnUiThread {
                activity.onRemotePushMessageReceived(title, body, data)
            }
        }

        // Record this id in the same "already alerted" cache ThikanaNotificationSyncManager's
        // background poller checks, so that poller (which runs independently every 25s and
        // on its own 15-min AlarmManager wakeups) never fires a second, duplicate system
        // notification for the exact same like/comment/message once this instant FCM push
        // has already handled it.
        val id = data["id"]
        val isMessageType = type.lowercase() in setOf("chat", "message", "dm", "group_chat")
        if (!id.isNullOrBlank()) {
            if (isMessageType) {
                ThikanaNotificationSyncManager.markMessageSeen(applicationContext, id)
            } else {
                ThikanaNotificationSyncManager.markNotificationSeen(applicationContext, id)
            }
        }

        // Only show the native heads-up/status-bar notification when the app isn't already
        // open and in the foreground. Matches the suppression the background poller already
        // does (see ThikanaNotificationSyncManager's isAppInForeground checks) — without this,
        // actively chatting in the app still popped a system notification for every message
        // the instant it landed on screen via polling, even though the person was already
        // looking right at it.
        val isAppInForeground = MainActivity.currentInstance
            ?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true
        if (!isAppInForeground) {
            NotificationHelper.showNotification(
                context = applicationContext,
                title = title,
                body = body,
                type = type,
                data = data
            )
        }
    }
}
