package com.example.mitvplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Un elemento reproducible: canal en vivo, película o episodio. */
data class PlayItem(
    val title: String,
    val subtitle: String?,
    val url: String,
    val isLive: Boolean,
    /** ID del canal, para consultar la guía (EPG). Solo en vivo. */
    val streamId: String? = null
)

/** Lista que se le entrega al reproductor justo antes de abrirlo. */
object PlaybackQueue {
    @Volatile
    var items: List<PlayItem> = emptyList()

    @Volatile
    var startIndex: Int = 0
}

/**
 * Reproductor a pantalla completa (Media3 / ExoPlayer).
 *
 * En vivo:   ▲ / CH+ = canal siguiente, ▼ / CH- = canal anterior, OK = información del canal.
 * Películas y series: controles estándar de Media3; al terminar un episodio sigue con el siguiente.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var banner: View
    private lateinit var titleText: TextView
    private lateinit var nowText: TextView
    private lateinit var nextText: TextView
    private lateinit var hintText: TextView
    private lateinit var errorText: TextView

    private var player: ExoPlayer? = null
    private var items: List<PlayItem> = emptyList()
    private var index = 0
    private var resumePositionMs = 0L
    private var retryCount = 0
    private var epgJob: Job? = null

    /** En el celular: deslizar arriba/abajo cambia de canal y tocar muestra la información. */
    private val gestures by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (items.isNotEmpty() && items[index].isLive) showBanner(items[index])
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (items.isEmpty() || !items[index].isLive) return false
                if (Math.abs(velocityY) > 900f && Math.abs(velocityY) > Math.abs(velocityX)) {
                    zap(if (velocityY < 0) +1 else -1)
                    return true
                }
                return false
            }
        })
    }

    private val handler = Handler(Looper.getMainLooper())
    private val hideBannerTask = Runnable { banner.visibility = View.GONE }
    private val retryTask = Runnable { retryPlayback() }
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    retryCount = 0
                    errorText.visibility = View.GONE
                }
                Player.STATE_ENDED -> onEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handleError(error)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        items = PlaybackQueue.items
        if (items.isEmpty()) {
            // Por ejemplo, si el sistema recreó la app: no hay nada que reproducir.
            finish()
            return
        }
        index = (savedInstanceState?.getInt(KEY_INDEX) ?: PlaybackQueue.startIndex)
            .coerceIn(0, items.lastIndex)
        resumePositionMs = savedInstanceState?.getLong(KEY_POSITION) ?: 0L

        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.player_view)
        banner = findViewById(R.id.banner)
        titleText = findViewById(R.id.title_text)
        nowText = findViewById(R.id.now_text)
        nextText = findViewById(R.id.next_text)
        hintText = findViewById(R.id.hint_text)
        errorText = findViewById(R.id.error_text)
    }

    override fun onStart() {
        super.onStart()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Config.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, dataSourceFactory)))
            .build()
        exo.addListener(listener)
        playerView.player = exo
        player = exo
        playerView.requestFocus()

        playCurrent(resumePositionMs)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        epgJob?.cancel()

        val exo = player
        if (exo != null) {
            if (!items[index].isLive) resumePositionMs = exo.currentPosition
            exo.removeListener(listener)
            exo.release()
        }
        player = null
        playerView.player = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_INDEX, index)
        outState.putLong(KEY_POSITION, resumePositionMs)
    }

    // ------------------------------------------------------------- reproducción

    private fun playCurrent(startPositionMs: Long = 0L) {
        val exo = player ?: return
        val item = items[index]

        handler.removeCallbacks(retryTask)
        retryCount = 0
        errorText.visibility = View.GONE
        playerView.useController = !item.isLive

        if (item.isLive || startPositionMs <= 0L) {
            exo.setMediaItem(MediaItem.fromUri(item.url))
        } else {
            exo.setMediaItem(MediaItem.fromUri(item.url), startPositionMs)
        }
        exo.prepare()
        exo.playWhenReady = true

        showBanner(item)
    }

    private fun onEnded() {
        val item = items[index]
        if (item.isLive) return
        if (index < items.lastIndex) {
            index++
            playCurrent()
        } else {
            finish()
        }
    }

    private fun zap(delta: Int) {
        if (items.size < 2) {
            showBanner(items[index])
            return
        }
        index = (index + delta + items.size) % items.size
        playCurrent()
    }

    private fun handleError(error: PlaybackException) {
        if (retryCount < MAX_AUTO_RETRIES) {
            retryCount++
            errorText.text = "Reconectando… ($retryCount/$MAX_AUTO_RETRIES)"
            errorText.visibility = View.VISIBLE
            handler.removeCallbacks(retryTask)
            handler.postDelayed(retryTask, 2_500L)
            return
        }
        val cause = error.cause
        val detail = if (cause is HttpDataSource.InvalidResponseCodeException) {
            "HTTP ${cause.responseCode}"
        } else {
            error.errorCodeName
        }
        errorText.text = "No se pudo reproducir este contenido ($detail).\n\n" +
            "Pulsa OK para reintentar o Atrás para salir."
        errorText.visibility = View.VISIBLE
    }

    private fun retryPlayback() {
        val exo = player ?: return
        handler.removeCallbacks(retryTask)
        if (items[index].isLive) exo.seekToDefaultPosition()
        exo.prepare()
        exo.playWhenReady = true
    }

    // ------------------------------------------------------------ control remoto

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (items.isNotEmpty() && items[index].isLive) gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && items.isNotEmpty()) {
            val key = event.keyCode
            val isOk = key == KeyEvent.KEYCODE_DPAD_CENTER ||
                key == KeyEvent.KEYCODE_ENTER ||
                key == KeyEvent.KEYCODE_NUMPAD_ENTER

            // Error definitivo: OK vuelve a intentar.
            if (isOk && errorText.visibility == View.VISIBLE && retryCount >= MAX_AUTO_RETRIES) {
                retryCount = 0
                errorText.text = "Reintentando…"
                retryPlayback()
                return true
            }

            val item = items[index]
            if (item.isLive) {
                // Sin repetición para no saturar al servidor al mantener pulsada la tecla.
                val firstPress = event.repeatCount == 0
                when (key) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (firstPress) zap(+1)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (firstPress) zap(-1)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> {
                        if (firstPress) showBanner(item)
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        player?.let { it.playWhenReady = !it.playWhenReady }
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ------------------------------------------------------------------- banner

    private fun showBanner(item: PlayItem) {
        val params = banner.layoutParams as FrameLayout.LayoutParams
        if (item.isLive) {
            params.gravity = Gravity.BOTTOM
            banner.setBackgroundResource(R.drawable.bg_scrim_bottom)
        } else {
            params.gravity = Gravity.TOP
            banner.setBackgroundResource(R.drawable.bg_scrim_top)
        }
        banner.layoutParams = params

        titleText.text = item.title
        nowText.visibility = View.GONE
        nextText.visibility = View.GONE

        val subtitle = item.subtitle
        if (!item.isLive && subtitle != null) {
            nowText.text = subtitle
            nowText.visibility = View.VISIBLE
        }

        if (item.isLive) {
            hintText.text = "▲ ▼  Cambiar de canal      OK  Información      Atrás  Salir"
            hintText.visibility = View.VISIBLE
        } else {
            hintText.visibility = View.GONE
        }

        banner.visibility = View.VISIBLE
        handler.removeCallbacks(hideBannerTask)
        handler.postDelayed(hideBannerTask, BANNER_DURATION_MS)

        loadEpg(item)
    }

    private fun loadEpg(item: PlayItem) {
        epgJob?.cancel()
        val streamId = item.streamId
        if (!item.isLive || streamId == null) return

        epgJob = lifecycleScope.launch {
            val repository = (application as MiTvApp).repository
            val entries = try {
                repository.shortEpg(streamId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val sorted = entries.sortedBy { it.start }
            val now = sorted.getOrNull(0)
            val next = sorted.getOrNull(1)
            if (now != null && now.title.isNotBlank()) {
                nowText.text = "Ahora: ${now.title}  (${formatTime(now.start)} – ${formatTime(now.end)})"
                nowText.visibility = View.VISIBLE
            }
            if (next != null && next.title.isNotBlank()) {
                nextText.text = "Después: ${next.title}  (${formatTime(next.start)})"
                nextText.visibility = View.VISIBLE
            }
        }
    }

    private fun formatTime(epochSeconds: Long): String = timeFormat.format(Date(epochSeconds * 1000))

    companion object {
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION = "position"
        private const val MAX_AUTO_RETRIES = 3
        private const val BANNER_DURATION_MS = 5_000L

        fun start(context: Context, items: List<PlayItem>, startIndex: Int) {
            PlaybackQueue.items = items
            PlaybackQueue.startIndex = startIndex
            context.startActivity(Intent(context, PlayerActivity::class.java))
        }
    }
}
