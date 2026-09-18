package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_MESSAGES = "thikana_messages"
    const val CHANNEL_SOCIAL = "thikana_social"
    const val CHANNEL_TRENDING = "thikana_trending"

    fun setupNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Direct & Group Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new messages and group chats"
                enableVibration(true)
            }

            val socialChannel = NotificationChannel(
                CHANNEL_SOCIAL,
                "Likes, Comments & Follows",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when someone likes, comments, or follows you"
                enableVibration(true)
            }

            val trendingChannel = NotificationChannel(
                CHANNEL_TRENDING,
                "Trending Thikanas & Spots",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time hype alerts when your spots or nearby spots trend"
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(
                listOf(messageChannel, socialChannel, trendingChannel)
            )
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        body: String,
        type: String = "social",
        data: Map<String, String> = emptyMap(),
        customId: Int? = null
    ) {
        try {
            setupNotificationChannels(context)

            val channelId = when (type.lowercase()) {
                "chat", "message", "dm", "group_chat" -> CHANNEL_MESSAGES
                "trending", "hype", "spot_alert" -> CHANNEL_TRENDING
                else -> CHANNEL_SOCIAL
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("from_fcm", true)
                data.forEach { (k, v) ->
                    putExtra(k, v)
                }
                if (data.containsKey("url")) {
                    this.data = Uri.parse(data["url"])
                }
            }

            val notifId = customId ?: (data["id"]?.hashCode() ?: System.currentTimeMillis().toInt())

            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.app_icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notifId, builder.build())
            Log.d("NotificationHelper", "Posted notification: $title - $body (id=$notifId)")
        } catch (e: SecurityException) {
            Log.w("NotificationHelper", "Notification permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Error showing notification", e)
        }
    }
}

