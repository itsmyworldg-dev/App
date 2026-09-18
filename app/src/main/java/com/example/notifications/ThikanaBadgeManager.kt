package com.example.notifications

import androidx.compose.runtime.mutableIntStateOf

/**
 * Global state manager for navigation and action button badge counts:
 * - Messages unread count
 * - Notifications (Bell icon) unread count
 * - Vibes (Feed) new unviewed posts count
 * - Find Dost (Community/Dost suggestions) count
 */
object ThikanaBadgeManager {
    val messagesCount = mutableIntStateOf(0)
    val notificationsCount = mutableIntStateOf(0)
    val vibesCount = mutableIntStateOf(0)
    val dostCount = mutableIntStateOf(0)

    fun update(messages: Int, notifs: Int, vibes: Int, dost: Int) {
        messagesCount.intValue = messages.coerceAtLeast(0)
        notificationsCount.intValue = notifs.coerceAtLeast(0)
        vibesCount.intValue = vibes.coerceAtLeast(0)
        dostCount.intValue = dost.coerceAtLeast(0)
    }

    fun updateMessages(messages: Int) {
        messagesCount.intValue = messages.coerceAtLeast(0)
    }

    fun updateNotifications(notifs: Int) {
        notificationsCount.intValue = notifs.coerceAtLeast(0)
    }

    fun updateVibes(vibes: Int) {
        vibesCount.intValue = vibes.coerceAtLeast(0)
    }

    fun updateDost(dost: Int) {
        dostCount.intValue = dost.coerceAtLeast(0)
    }

    fun clearMessages() {
        messagesCount.intValue = 0
    }

    fun clearVibes() {
        vibesCount.intValue = 0
    }

    fun clearDost() {
        dostCount.intValue = 0
    }
}
