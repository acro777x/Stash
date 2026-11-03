package com.eurtlabs.stash.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import coil.Coil
import coil.request.ImageRequest
import com.eurtlabs.stash.MainActivity
import com.eurtlabs.stash.R
import com.eurtlabs.stash.StashApplication
import com.eurtlabs.stash.data.model.PlaybackRepeatMode
import com.eurtlabs.stash.data.model.TrackInfo
import com.eurtlabs.stash.player.MusicPlayerManager
import com.eurtlabs.stash.data.storage.PlaylistStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * High-priority Media Playback Foreground Service.
 * Full MediaSessionCompat + MediaMetadataCompat integration:
 * Activates Tecno POVA Dynamic Port (Dynamic Island), Android 16 Fluid Media Notifications,
 * and maintains 24/7 background audio without system sleep.
 */
class MusicPlaybackService : Service() {

    companion object {
        const val NOTIFICATION_ID = 2002
        const val WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes

        const val ACTION_PLAY = "com.eurtlabs.stash.ACTION_PLAY"
        const val ACTION_PAUSE = "com.eurtlabs.stash.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.eurtlabs.stash.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.eurtlabs.stash.ACTION_NEXT"
        const val ACTION_STOP = "com.eurtlabs.stash.ACTION_STOP"
        const val ACTION_UPDATE_NOTIFICATION = "com.eurtlabs.stash.ACTION_UPDATE_NOTIFICATION"
        const val ACTION_TOGGLE_LIKE = "com.eurtlabs.stash.ACTION_TOGGLE_LIKE"
        const val ACTION_TOGGLE_REPEAT = "com.eurtlabs.stash.ACTION_TOGGLE_REPEAT"

        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_ARTIST = "EXTRA_ARTIST"
        const val EXTRA_ART_URL = "EXTRA_ART_URL"
        const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
        const val EXTRA_POSITION_MS = "EXTRA_POSITION_MS"
        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"

        fun start(
            context: Context,
            track: TrackInfo?,
            isPlaying: Boolean,
            positionMs: Long = 0L,
            durationMs: Long = 0L
        ) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
                putExtra(EXTRA_TITLE, track?.title ?: "Playing Music")
                putExtra(EXTRA_ARTIST, track?.artists?.joinToString(", ") ?: "Stash Music")
                putExtra(EXTRA_ART_URL, track?.albumArtUrl ?: "")
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_POSITION_MS, positionMs)
                putExtra(EXTRA_DURATION_MS, durationMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(
            context: Context,
            track: TrackInfo?,
            isPlaying: Boolean,
            positionMs: Long = 0L,
            durationMs: Long = 0L
        ) {
            start(context, track, isPlaying, positionMs, durationMs)
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, MusicPlaybackService::class.java))
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main)

    private var currentTitle = "Stash Music"
    private var currentArtist = "Playing Audio"
    private var currentArtUrl = ""
    private var isPlaying = true
    private var currentPositionMs = 0L
    private var currentDurationMs = 0L
    private var cachedArtBitmap: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Stash::MusicPlaybackWakeLock").apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Stash::MusicWifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }

            // Initialize MediaSessionCompat for Tecno Dynamic Port / Island & Android SystemUI
            mediaSession = MediaSessionCompat(this, "StashMediaSession").apply {
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        MusicPlayerManager.play()
                    }

                    override fun onPause() {
                        MusicPlayerManager.pause()
                    }

                    override fun onSkipToNext() {
                        MusicPlayerManager.skipNext()
                    }

                    override fun onSkipToPrevious() {
                        MusicPlayerManager.skipPrevious()
                    }

                    override fun onSeekTo(pos: Long) {
                        MusicPlayerManager.seekTo(pos)
                    }

                    override fun onStop() {
                        MusicPlayerManager.stop()
                    }

                    override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                        when (action) {
                            ACTION_TOGGLE_LIKE -> {
                                val track = MusicPlayerManager.playerState.value.currentTrack
                                if (track != null) {
                                    PlaylistStore.toggleLike(track)
                                    updatePlaybackState(isPlaying)
                                    postForegroundNotification()
                                }
                            }
                            ACTION_TOGGLE_REPEAT -> {
                                MusicPlayerManager.toggleRepeatMode()
                                updatePlaybackState(isPlaying)
                                postForegroundNotification()
                            }
                        }
                    }
                })

            // Real-time synchronization of Like/Heart state in notification
            scope.launch {
                PlaylistStore.likedTrackIds.collect {
                    updatePlaybackState(isPlaying)
                    postForegroundNotification()
                }
            }
                isActive = true
            }
        } catch (e: Exception) {
            // Ignore init failure
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                MusicPlayerManager.play()
            }
            ACTION_PAUSE -> {
                MusicPlayerManager.pause()
            }
            ACTION_PREVIOUS -> {
                MusicPlayerManager.skipPrevious()
            }
            ACTION_NEXT -> {
                MusicPlayerManager.skipNext()
            }
            ACTION_STOP -> {
                try {
                    updatePlaybackState(playing = false)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } catch (e: Exception) {
                    // Ignore
                }
                return START_NOT_STICKY
            }
                        ACTION_TOGGLE_LIKE -> {
                val track = MusicPlayerManager.playerState.value.currentTrack
                if (track != null) {
                    PlaylistStore.toggleLike(track)
                    updatePlaybackState(isPlaying)
                    postForegroundNotification()
                }
                return START_STICKY
            }
            ACTION_TOGGLE_REPEAT -> {
                MusicPlayerManager.toggleRepeatMode()
                updatePlaybackState(isPlaying)
                postForegroundNotification()
                return START_STICKY
            }
            ACTION_UPDATE_NOTIFICATION -> {
                // Re-acquire wake lock to prevent background kill
                wakeLock?.let { if (!it.isHeld) it.acquire(WAKE_LOCK_TIMEOUT_MS) }

                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: currentArtist
                val newArtUrl = intent.getStringExtra(EXTRA_ART_URL) ?: ""
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                currentPositionMs = intent.getLongExtra(EXTRA_POSITION_MS, currentPositionMs)
                currentDurationMs = intent.getLongExtra(EXTRA_DURATION_MS, currentDurationMs)

                if (newArtUrl != currentArtUrl && newArtUrl.isNotBlank()) {
                    currentArtUrl = newArtUrl
                    fetchArtworkBitmap(newArtUrl)
                } else {
                    updateMediaSessionMetadata()
                }

                updatePlaybackState(playing = isPlaying)
                postForegroundNotification()
            }
        }
        return START_STICKY
    }

    private fun updatePlaybackState(playing: Boolean) {
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SET_RATING

        val currentTrack = MusicPlayerManager.playerState.value.currentTrack
        val isLiked = currentTrack != null && PlaylistStore.isLiked(currentTrack.id)
        val repeatMode = MusicPlayerManager.playerState.value.repeatMode
        val repeatIcon = when (repeatMode) {
            PlaybackRepeatMode.ONE -> R.drawable.ic_repeat_one
            else -> R.drawable.ic_repeat
        }

        val likeCustomAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_TOGGLE_LIKE,
            if (isLiked) "Unlike" else "Like",
            if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        ).build()

        val repeatCustomAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_TOGGLE_REPEAT,
            "Repeat",
            repeatIcon
        ).build()

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .addCustomAction(likeCustomAction)
            .addCustomAction(repeatCustomAction)
            .setState(state, currentPositionMs, 1.0f)
            .build()

        mediaSession?.setPlaybackState(playbackState)
    }

    private fun updateMediaSessionMetadata() {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, currentArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, currentArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDurationMs)

        cachedArtBitmap?.let {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
        }

        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun fetchArtworkBitmap(url: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(this@MusicPlaybackService)
                    .data(url)
                    .size(512, 512)
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(this@MusicPlaybackService).execute(request)
                val drawable = result.drawable
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    cachedArtBitmap = drawable.bitmap
                    withContext(Dispatchers.Main) {
                        updateMediaSessionMetadata()
                        postForegroundNotification()
                    }
                }
            } catch (e: Exception) {
                // Fallback metadata update
                withContext(Dispatchers.Main) {
                    updateMediaSessionMetadata()
                }
            }
        }
    }

    private fun postForegroundNotification() {
        val notification = buildMediaNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildMediaNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseAction = if (isPlaying) {
            val pauseIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                pauseIntent
            ).build()
        } else {
            val playIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PLAY },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                playIntent
            ).build()
        }

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val currentTrack = MusicPlayerManager.playerState.value.currentTrack
        val isLiked = currentTrack != null && PlaylistStore.isLiked(currentTrack.id)
        val repeatMode = MusicPlayerManager.playerState.value.repeatMode
        val repeatIcon = when (repeatMode) {
            PlaybackRepeatMode.ONE -> R.drawable.ic_repeat_one
            else -> R.drawable.ic_repeat
        }

        val likeIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_TOGGLE_LIKE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val likeAction = NotificationCompat.Action.Builder(
            if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
            if (isLiked) "Liked" else "Like",
            likeIntent
        ).build()

        val repeatIntent = PendingIntent.getService(
            this,
            5,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_TOGGLE_REPEAT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val repeatAction = NotificationCompat.Action.Builder(
            repeatIcon,
            "Repeat",
            repeatIntent
        ).build()

        val builder = NotificationCompat.Builder(this, StashApplication.PLAYBACK_CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSubText("Stash Music • Lossless")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .addAction(likeAction) // 0: Like
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent) // 1: Prev
            .addAction(playPauseAction) // 2: Play/Pause
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent) // 3: Next
            .addAction(repeatAction) // 4: Repeat
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )

        cachedArtBitmap?.let {
            builder.setLargeIcon(it)
        }

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // Prevent leaked coroutines
        mediaSession?.apply {
            isActive = false
            release()
        }
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
    }
}
