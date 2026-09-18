package com.example.storyeditor.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.example.MainActivity

/**
 * ActivityResultContract for integrating StoryEditor into your existing Android app.
 *
 * Example usage in your existing app:
 * ```kotlin
 * val storyEditorLauncher = rememberLauncherForActivityResult(StoryEditorContract()) { resultUri ->
 *     resultUri?.let { uri ->
 *         // Use uri to post to your existing app feed, stories, or upload to backend
 *     }
 * }
 *
 * // To launch:
 * storyEditorLauncher.launch(StoryEditorInput(initialImageUri = myOptionalUri))
 * ```
 */
data class StoryEditorInput(
    val initialImageUri: Uri? = null,
    val initialMode: String = "CAMERA" // or "EDITOR", "COLLAGE"
)

class StoryEditorContract : ActivityResultContract<StoryEditorInput, Uri?>() {

    override fun createIntent(context: Context, input: StoryEditorInput): Intent {
        return Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_INITIAL_URI, input.initialImageUri?.toString())
            putExtra(EXTRA_INITIAL_MODE, input.initialMode)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode == Activity.RESULT_OK && intent != null) {
            return intent.data ?: intent.getParcelableExtra(EXTRA_RESULT_URI)
        }
        return null
    }

    companion object {
        const val EXTRA_INITIAL_URI = "extra_initial_uri"
        const val EXTRA_INITIAL_MODE = "extra_initial_mode"
        const val EXTRA_RESULT_URI = "extra_result_uri"
    }
}
