package com.example.sinilinkcontrol

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ActiveMedia(
    val title: String = "",
    val artist: String = "",
    val albumArt: Bitmap? = null,
    val dominantColor: Int? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val controller: MediaController? = null
)

class MediaNotificationService : NotificationListenerService() {
    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeController: MediaController? = null

    companion object {
        private val _mediaState = MutableStateFlow(ActiveMedia())
        val mediaState: StateFlow<ActiveMedia> = _mediaState

        fun isPermissionGranted(context: Context): Boolean {
            val componentName = ComponentName(context, MediaNotificationService::class.java).flattenToString()
            val enabledListeners = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return enabledListeners?.contains(componentName) == true
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateActiveSession()
        
        try {
            val component = ComponentName(this, MediaNotificationService::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener({ updateActiveSession() }, component)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaState()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMediaState()
        }
    }

    private fun updateActiveSession() {
        try {
            val component = ComponentName(this, MediaNotificationService::class.java)
            val controllers = mediaSessionManager.getActiveSessions(component)
            
            val newController = controllers.firstOrNull { 
                it.playbackState?.state == PlaybackState.STATE_PLAYING || 
                it.playbackState?.state == PlaybackState.STATE_PAUSED 
            } ?: controllers.firstOrNull()

            if (activeController != newController) {
                activeController?.unregisterCallback(callback)
                activeController = newController
                activeController?.registerCallback(callback)
            }
            updateMediaState()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun updateMediaState() {
        val controller = activeController ?: return

        val metadata = controller.metadata
        val pbState = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val rawArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) 
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val current = _mediaState.value
        val newTitle = if (title.isNotEmpty()) title else current.title
        val newArtist = if (artist.isNotEmpty()) artist else current.artist
        val newArt = rawArt ?: current.albumArt

        val isPlaying = pbState?.state == PlaybackState.STATE_PLAYING
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: current.duration
        val position = pbState?.position ?: current.position

        var domColor: Int? = current.dominantColor
        if (rawArt != null && rawArt != current.albumArt) {
            domColor = extractAverageColor(rawArt)
        }

        _mediaState.value = ActiveMedia(
            title = newTitle,
            artist = newArtist,
            albumArt = newArt,
            dominantColor = domColor,
            isPlaying = isPlaying,
            position = position,
            duration = duration,
            controller = controller
        )
    }

    private fun extractAverageColor(bitmap: Bitmap): Int {
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        var r = 0L
        var g = 0L
        var b = 0L
        val pixels = IntArray(64)
        scaled.getPixels(pixels, 0, 8, 0, 0, 8, 8)
        for (pixel in pixels) {
            r += Color.red(pixel)
            g += Color.green(pixel)
            b += Color.blue(pixel)
        }
        scaled.recycle()
        return Color.rgb((r / 64).toInt(), (g / 64).toInt(), (b / 64).toInt())
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        activeController?.unregisterCallback(callback)
        activeController = null
    }
}