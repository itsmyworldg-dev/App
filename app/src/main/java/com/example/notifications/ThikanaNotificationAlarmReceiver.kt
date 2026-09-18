package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver triggered by Android AlarmManager periodically to sync notifications
 * and direct messages even when the app is in background, minimized, or screen is off.
 * Also handles device boot / reboot to re-schedule periodic background sync.
 */
class ThikanaNotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "NotificationAlarmReceiver triggered with action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ThikanaNotificationSyncManager.scheduleBackgroundAlarm(context)
        }

        // Run notification and message sync in background
        ThikanaNotificationSyncManager.syncNow(context)
    }

    companion object {
        private const val TAG = "NotifAlarmReceiver"
        const val ACTION_ALARM_SYNC = "com.example.ACTION_NOTIFICATION_ALARM_SYNC"
    }
}
