package com.example.storyeditor.model

/**
 * StoryEditorMusicBridge
 *
 * Bridge to connect your existing Android app's music library to the Story Editor.
 *
 * How to use from your existing app:
 * ```kotlin
 * // 1. Pass your existing app's tracks list:
 * StoryEditorMusicBridge.availableTracks = listOf(
 *     MusicTrack(
 *         id = "track_123",
 *         title = "Blinding Lights",
 *         artist = "The Weeknd",
 *         audioUri = "content://... or https://..."
 *     )
 * )
 *
 * // 2. (Optional) Provide a custom launcher for your existing app's Music Picker dialog / Activity:
 * StoryEditorMusicBridge.onOpenMusicPicker = { onTrackSelected ->
 *     // Launch your app's existing music picker
 *     // Once user picks a song: onTrackSelected(chosenMusicTrack)
 * }
 * ```
 */
object StoryEditorMusicBridge {
    /**
     * Set this list from your host app to populate the Music tray.
     * Empty by default so no unwanted sample music is included.
     */
    var availableTracks: List<MusicTrack> = emptyList()

    /**
     * Optional callback to launch your host app's native audio / music picker.
     */
    var onOpenMusicPicker: ((onTrackChosen: (MusicTrack) -> Unit) -> Unit)? = null
}
