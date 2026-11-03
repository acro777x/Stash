package com.eurtlabs.stash

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class StashApplication : Application(), ImageLoaderFactory {

    companion object {
        const val CHANNEL_ID = "stash_download_channel"
        const val PLAYBACK_CHANNEL_ID = "stash_playback_channel"
        private const val TAG = "StashApp"
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Limit image cache to 15% of available RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB disk cache
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initNativeEngines()
        com.eurtlabs.stash.data.storage.PlaylistStore.initialize(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Download Channel
            val downloadChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_channel_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(downloadChannel)

            // Playback Channel (High priority for media controls, silent sound)
            val playbackChannel = NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                "Stash Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active music playback controls and artwork notification"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(playbackChannel)
        }
    }

    private fun initNativeEngines() {
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Log.d(TAG, "YoutubeDL and FFmpeg native engines successfully initialized at startup.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YoutubeDL / FFmpeg engines", e)
        }
    }
}
