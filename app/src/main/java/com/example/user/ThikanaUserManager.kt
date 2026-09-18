package com.example.user

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Manages the current authenticated user's profile information (avatar, handle, display name)
 * and persists it locally so the avatar and profile identity are immediately visible.
 */
object ThikanaUserManager {
    val avatarUrl = mutableStateOf<String?>(null)
    val handle = mutableStateOf<String?>(null)
    val displayName = mutableStateOf<String?>(null)
    val userId = mutableStateOf<String?>(null)

    private const val PREFS_NAME = "thikana_user_profile_prefs"
    private const val KEY_AVATAR = "user_avatar_url"
    private const val KEY_HANDLE = "user_handle"
    private const val KEY_DISPLAY_NAME = "user_display_name"
    private const val KEY_USER_ID = "user_id"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        avatarUrl.value = prefs.getString(KEY_AVATAR, null)
        handle.value = prefs.getString(KEY_HANDLE, null)
        displayName.value = prefs.getString(KEY_DISPLAY_NAME, null)
        userId.value = prefs.getString(KEY_USER_ID, null)
    }

    fun update(context: Context, id: String?, userHandle: String?, userAvatar: String?, name: String?) {
        val finalAvatar = if (userAvatar.isNullOrBlank()) null else userAvatar
        val finalHandle = if (userHandle.isNullOrBlank()) null else userHandle
        val finalName = if (name.isNullOrBlank()) null else name
        val finalId = if (id.isNullOrBlank()) null else id

        userId.value = finalId
        handle.value = finalHandle
        avatarUrl.value = finalAvatar
        displayName.value = finalName

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_USER_ID, finalId)
            .putString(KEY_HANDLE, finalHandle)
            .putString(KEY_AVATAR, finalAvatar)
            .putString(KEY_DISPLAY_NAME, finalName)
            .apply()
    }

    fun clear(context: Context) {
        userId.value = null
        handle.value = null
        avatarUrl.value = null
        displayName.value = null

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
