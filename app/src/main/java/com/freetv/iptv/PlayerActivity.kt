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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "index"
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var overlay: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { overlay.visibility = View.GONE }

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
        playerView.controllerAutoShow = false
        overlay = findViewById(R.id.overlay)
        index = savedInstanceState?.getInt(EXTRA_INDEX) ?: intent.getIntExtra(EXTRA_INDEX, 0)

        if (channels.isEmpty()) { finish(); return }
    }

    override fun onStart() {
        super.onStart()
        if (channels.isNotEmpty()) playCurrent()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_INDEX, index)
    }

    private fun playCurrent() {
        releasePlayer()
        val ch = channels[index]

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ch.userAgent ?: DEFAULT_UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        ch.referrer?.let { http.setDefaultRequestProperties(mapOf("Referer" to it)) }

        val item = MediaItem.Builder().setUri(Uri.parse(ch.url)).apply {
            if (ch.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        }.build()

        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    getString(R.string.play_error, ch.name),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        })
        p.setMediaItem(item)
        p.playWhenReady = true
        p.prepare()
        playerView.player = p
        player = p

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
        // Up/Down (or Channel +/-) switches channel while the controller is hidden.
        if (!playerView.isControllerFullyVisible) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(1); return true }
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
}
