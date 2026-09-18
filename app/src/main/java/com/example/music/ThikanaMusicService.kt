package com.example.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class ThikanaMusicService : Service() {

    companion object {
        private const val TAG = "ThikanaMusicService"
        const val CHANNEL_ID = "thikana_music_channel"
        const val NOTIFICATION_ID = 2026

        const val ACTION_UPDATE_STATE = "com.example.music.ACTION_UPDATE_STATE"
        const val ACTION_TOGGLE_PLAY = "com.example.music.ACTION_TOGGLE_PLAY"
        const val ACTION_PLAY = "com.example.music.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.music.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.music.ACTION_NEXT"
        const val ACTION_PREV = "com.example.music.ACTION_PREV"
        const val ACTION_STOP = "com.example.music.ACTION_STOP"

        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_POSITION = "extra_position"

        // Active listener registered by MainActivity to execute web commands
        var commandListener: ((command: String, arg: Long) -> Unit)? = null

        var currentIsPlaying = false
            private set
        var currentTitle = "Mera Thikaana"
            private set
        var currentArtist = "Ranchi Beats"
            private set
        var currentDuration = 0L
            private set
        var currentPosition = 0L
            private set

        fun updatePlayback(
            context: Context,
            isPlaying: Boolean,
            title: String,
            artist: String,
            durationSec: Long = 0L,
            positionSec: Long = 0L
        ) {
            val intent = Intent(context, ThikanaMusicService::class.java).apply {
                action = ACTION_UPDATE_STATE
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_DURATION, durationSec)
                putExtra(EXTRA_POSITION, positionSec)
            }
            try {
                if (isPlaying) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start/update music service", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ThikanaMusicService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop music service", e)
            }
        }
    }

    private var mediaSession: MediaSession? = null
    private var notificationManager: NotificationManager? = null
    private var defaultArtBitmap: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        createNotificationChannel()
        setupMediaSession()

        try {
            defaultArtBitmap = BitmapFactory.decodeResource(resources, R.drawable.app_icon)
        } catch (e: Exception) {
            Log.w(TAG, "Could not load default album art", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Thikaana Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows playback controls on the lock screen and notification shade"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "ThikanaMusicSession").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    dispatchCommand("play")
                }

                override fun onPause() {
                    dispatchCommand("pause")
                }

                override fun onSkipToNext() {
                    dispatchCommand("next")
                }

                override fun onSkipToPrevious() {
                    dispatchCommand("prev")
                }

                override fun onSeekTo(pos: Long) {
                    dispatchCommand("seek", pos / 1000L)
                }

                override fun onStop() {
                    dispatchCommand("pause")
                    stopSelf()
                }
            })

            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_UPDATE_STATE -> {
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Mera Thikaana"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Ranchi Beats"
                val duration = intent.getLongExtra(EXTRA_DURATION, 0L)
                val position = intent.getLongExtra(EXTRA_POSITION, 0L)

                currentIsPlaying = isPlaying
                currentTitle = title.ifBlank { "Mera Thikaana" }
                currentArtist = artist.ifBlank { "Ranchi Beats" }
                currentDuration = duration
                currentPosition = position

                updateMediaSessionState(isPlaying, currentTitle, currentArtist, duration, position)
                buildAndPostNotification(isPlaying, currentTitle, currentArtist)
            }

            ACTION_TOGGLE_PLAY -> {
                dispatchCommand(if (currentIsPlaying) "pause" else "play")
            }

            ACTION_PLAY -> {
                dispatchCommand("play")
            }

            ACTION_PAUSE -> {
                dispatchCommand("pause")
            }

            ACTION_NEXT -> {
                dispatchCommand("next")
            }

            ACTION_PREV -> {
                dispatchCommand("prev")
            }

            ACTION_STOP -> {
                dispatchCommand("pause")
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager?.cancel(NOTIFICATION_ID)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun dispatchCommand(command: String, arg: Long = 0L) {
        val listener = commandListener
        if (listener != null) {
            listener(command, arg)
        } else {
            // If MainActivity is in background or needs an Intent
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("music_command", command)
                putExtra("music_command_arg", arg)
            }
            startActivity(intent)
        }
    }

    private fun updateMediaSessionState(
        isPlaying: Boolean,
        title: String,
        artist: String,
        durationSec: Long,
        positionSec: Long
    ) {
        val session = mediaSession ?: return

        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP

        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setState(state, positionSec * 1000L, 1.0f)
            .build()
        session.setPlaybackState(playbackState)

        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Mera Thikaana")

        if (durationSec > 0) {
            metadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, durationSec * 1000L)
        }
        defaultArtBitmap?.let {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
        }

        session.setMetadata(metadataBuilder.build())
        session.isActive = true
    }

    private fun buildAndPostNotification(isPlaying: Boolean, title: String, artist: String) {
        val session = mediaSession ?: return

        // PendingIntents for Media Actions
        val prevPendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ThikanaMusicService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePlayPendingIntent = PendingIntent.getService(
            this, 2,
            Intent(this, ThikanaMusicService::class.java).apply { action = ACTION_TOGGLE_PLAY },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextPendingIntent = PendingIntent.getService(
            this, 3,
            Intent(this, ThikanaMusicService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this, 4,
            Intent(this, ThikanaMusicService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Launch MainActivity when tapping the notification body
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_music_pause else R.drawable.ic_music_play
        val playPauseText = if (isPlaying) "Pause" else "Play"

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        notificationBuilder
            .setStyle(mediaStyle)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("Mera Thikaana")
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(Notification.Action.Builder(R.drawable.ic_music_prev, "Previous", prevPendingIntent).build())
            .addAction(Notification.Action.Builder(playPauseIcon, playPauseText, togglePlayPendingIntent).build())
            .addAction(Notification.Action.Builder(R.drawable.ic_music_next, "Next", nextPendingIntent).build())

        defaultArtBitmap?.let {
            notificationBuilder.setLargeIcon(it)
        }

        val notification = notificationBuilder.build()

        if (isPlaying) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground service", e)
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        } else {
            // When paused, detach foreground so notification remains dismissible if user desires
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        mediaSession = null
    }
}
