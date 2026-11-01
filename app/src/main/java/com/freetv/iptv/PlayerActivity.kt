package com.freetv.iptv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "index"
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }

    private var player: ExoPlayer? = null
    private lateinit var http: DefaultHttpDataSource.Factory
    private lateinit var playerView: PlayerView
    private lateinit var overlay: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { if (!playerView.isControllerFullyVisible) overlay.visibility = View.GONE }

    private val channels get() = ChannelStore.channels
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        playerView = findViewById(R.id.player_view)
        overlay = findViewById(R.id.overlay)
        playerView.controllerAutoShow = false
        // Live TV: no 5s / 15s skip buttons. The previous/next buttons switch channel instead.
        playerView.setShowRewindButton(false)
        playerView.setShowFastForwardButton(false)
        // Keep the channel name visible while the controls are shown.
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (visibility == View.VISIBLE) {
                handler.removeCallbacks(hideOverlay)
                overlay.visibility = View.VISIBLE
            } else {
                overlay.visibility = View.GONE
            }
        })

        index = savedInstanceState?.getInt(EXTRA_INDEX) ?: intent.getIntExtra(EXTRA_INDEX, 0)
        if (channels.isEmpty()) { finish(); return }
        index = index.coerceIn(0, channels.size - 1)
    }

    override fun onStart() {
        super.onStart()
        if (channels.isEmpty()) return
        createPlayer()
        playCurrent()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_INDEX, index)
    }

    private fun createPlayer() {
        http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        // Best picture quality: always choose the highest resolution/bitrate the device can play,
        // instead of starting low and adapting up.
        val trackSelector = DefaultTrackSelector(this)
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setForceHighestSupportedBitrate(true)
                .setViewportSizeToPhysicalDisplaySize(this, true)
        )

        val exo = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .build()
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val name = channels.getOrNull(index)?.name ?: ""
                Toast.makeText(this@PlayerActivity, getString(R.string.play_error, name), Toast.LENGTH_SHORT).show()
            }
        })
        exo.playWhenReady = true
        player = exo
        playerView.player = ChannelSwitchingPlayer(exo)
    }

    private fun playCurrent() {
        val p = player ?: return
        val ch = channels[index]

        // Per-channel HTTP headers; applied to the connections opened for this channel.
        http.setUserAgent(ch.userAgent ?: DEFAULT_UA)
        http.setDefaultRequestProperties(ch.referrer?.let { mapOf("Referer" to it) } ?: emptyMap())

        val item = MediaItem.Builder()
            .setUri(Uri.parse(ch.url))
            .apply {
                if (ch.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }.build()

        p.setMediaItem(item)
        p.prepare()

        overlay.text = getString(R.string.channel_overlay, index + 1, channels.size, ch.name)
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOverlay)
        handler.postDelayed(hideOverlay, 3500)
    }

    private fun zap(delta: Int) {
        if (channels.isEmpty()) return
        index = (index + delta + channels.size) % channels.size
        playCurrent()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Remote: Channel +/- always switches; Up/Down switches while the controls are hidden.
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> { zap(1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { zap(-1); return true }
        }
        if (!playerView.isControllerFullyVisible) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { zap(1); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** Makes the player's ⏮ / ⏭ buttons switch to the previous / next channel. */
    private inner class ChannelSwitchingPlayer(player: Player) : ForwardingPlayer(player) {
        private val channelCommands = listOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().addAll(*channelCommands.toIntArray()).build()

        override fun isCommandAvailable(command: Int): Boolean =
            command in channelCommands || super.isCommandAvailable(command)

        override fun hasNextMediaItem() = true
        override fun hasPreviousMediaItem() = true
        override fun seekToNext() = zap(1)
        override fun seekToNextMediaItem() = zap(1)
        override fun seekToPrevious() = zap(-1)
        override fun seekToPreviousMediaItem() = zap(-1)
    }
}
