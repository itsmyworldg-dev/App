package com.example.ui

import androidx.annotation.DrawableRes
import com.example.R

enum class ThikanaTab(
    val viewKey: String,
    val title: String,
    @DrawableRes val iconRes: Int? = null
) {
    FEED("feed", "Feed", R.drawable.ic_nav_vibes),
    DISCOVER("discover", "Discover", R.drawable.ic_nav_discover),
    CREATE("create", "Create", null),
    MESSAGES("messages", "Messages", R.drawable.ic_nav_messages),
    PROFILE("profile", "Profile", R.drawable.ic_nav_profile);

    companion object {
        fun fromKey(key: String): ThikanaTab {
            return entries.firstOrNull { it.viewKey.equals(key, ignoreCase = true) } ?: FEED
        }

        fun isBottomNavVisible(viewKey: String): Boolean {
            val lower = viewKey.lowercase()
            return when (lower) {
                "chat", "msgthread", "thread", "camera", "editor", "story", "post-editor" -> false
                else -> true
            }
        }
    }
}
