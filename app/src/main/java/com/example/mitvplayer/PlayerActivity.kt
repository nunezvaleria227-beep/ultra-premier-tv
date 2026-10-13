package com.example.mitvplayer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.mitvplayer.data.ResumeEntry
import com.example.mitvplayer.data.SubtitleException
import com.example.mitvplayer.data.SubtitleFiles
import com.example.mitvplayer.data.SubtitleQuery
import com.example.mitvplayer.data.SubtitleService
import com.example.mitvplayer.data.SubtitleSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Datos para recordar dónde se quedó una película o episodio. */
data class ResumeTag(
    /** "movie" o "episode". */
    val type: String,
    val id: String,
    val ext: String,
    val image: String?,
    val category: String = ""
)

/** Un elemento reproducible: canal en vivo, película o episodio. */
data class PlayItem(
    val title: String,
    val subtitle: String?,
    val url: String,
    val isLive: Boolean,
    /** ID del canal, para consultar la guía (EPG). Solo en vivo. */
    val streamId: String? = null,
    /** Si no es null, la app recuerda el minuto en que se dejó de ver. */
    val resume: ResumeTag? = null,
    /** true = empezar desde el principio aunque haya un minuto guardado. */
    val fromStart: Boolean = false,
    /** Datos para buscar sus subtítulos en internet (películas y episodios). */
    val subs: SubtitleQuery? = null
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
    private lateinit var optionsBar: View
    private lateinit var btnSubtitles: TextView
    private lateinit var btnSize: TextView

    private var player: ExoPlayer? = null
    private var items: List<PlayItem> = emptyList()
    private var index = 0
    private var resumePositionMs = 0L
    private var retryCount = 0
    private var epgJob: Job? = null
    private var fetchingSubs = false

    /** Para buscar subtítulos solo una vez por película/episodio e idioma. */
    private val autoSubTried = HashSet<String>()
    private val autoPicked = HashSet<String>()

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
    private val hideBannerTask = object : Runnable {
        override fun run() {
            if (optionsFocused()) {
                // Mientras se usan los botones de arriba no se esconde nada.
                handler.postDelayed(this, BANNER_DURATION_MS)
            } else {
                banner.visibility = View.GONE
                if (items.getOrNull(index)?.isLive == true) optionsBar.visibility = View.GONE
            }
        }
    }
    private val retryTask = Runnable { retryPlayback() }
    private val saveTask = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, SAVE_EVERY_MS)
        }
    }
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

        override fun onTracksChanged(tracks: Tracks) {
            maybeAutoSubtitles(tracks)
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
        optionsBar = findViewById(R.id.options_bar)
        btnSubtitles = findViewById(R.id.btn_subtitles)
        btnSize = findViewById(R.id.btn_size)

        btnSubtitles.setOnClickListener { showSubtitleDialog() }
        btnSize.setOnClickListener { showSizeDialog() }
        // En películas y series los botones aparecen junto con los controles.
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                val item = items.getOrNull(index)
                if (item != null && !item.isLive) {
                    btnSubtitles.visibility = View.VISIBLE
                    optionsBar.visibility = if (visibility == View.VISIBLE) View.VISIBLE else View.GONE
                }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        // Si se cerró en onCreate (no había nada que reproducir), no hay vistas ni reproductor.
        if (!::playerView.isInitialized || items.isEmpty()) return

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
        playerView.subtitleView?.apply {
            setStyle(
                CaptionStyleCompat(
                    Color.WHITE,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    Color.BLACK,
                    null
                )
            )
            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.1f)
        }
        playerView.requestFocus()

        playCurrent(resumePositionMs)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        epgJob?.cancel()
        if (!::playerView.isInitialized || items.isEmpty()) return

        saveProgress()
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
        applySize(item)
        applyTextPreference(item)

        var start = startPositionMs
        val tag = item.resume
        if (!item.isLive && start <= 0L && !item.fromStart && tag != null) {
            // Si ya se había visto un rato, se continúa unos segundos antes de donde se quedó.
            val saved = (application as MiTvApp).repository.library?.progress?.find(tag.type, tag.id)
            if (saved != null && saved.positionMs > 10_000L) {
                start = (saved.positionMs - 3_000L).coerceAtLeast(0L)
                Toast.makeText(this, "Continuando donde te quedaste (${formatPosition(start)})", Toast.LENGTH_SHORT).show()
            }
        }

        if (item.isLive || start <= 0L) {
            exo.setMediaItem(buildMediaItem(item))
        } else {
            exo.setMediaItem(buildMediaItem(item), start)
        }
        exo.prepare()
        exo.playWhenReady = true

        handler.removeCallbacks(saveTask)
        if (!item.isLive && item.resume != null) handler.postDelayed(saveTask, SAVE_EVERY_MS)

        showBanner(item)
    }

    private fun onEnded() {
        val item = items[index]
        if (item.isLive) return
        saveProgress(finished = true)
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

    /** Guarda el minuto actual (o borra la entrada si ya se terminó de ver). */
    private fun saveProgress(finished: Boolean = false) {
        val exo = player ?: return
        val item = items.getOrNull(index) ?: return
        val tag = item.resume ?: return
        if (item.isLive) return
        val store = (application as MiTvApp).repository.library?.progress ?: return

        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val position = exo.currentPosition
        when {
            finished || (duration > 0 && position > duration * 0.95) -> store.remove(tag.type, tag.id)
            position > 30_000L -> store.save(
                ResumeEntry(
                    type = tag.type,
                    id = tag.id,
                    ext = tag.ext,
                    title = item.title,
                    subtitle = item.subtitle,
                    image = tag.image,
                    positionMs = position,
                    durationMs = duration,
                    updatedAt = System.currentTimeMillis(),
                    category = tag.category
                )
            )
        }
    }

    private fun formatPosition(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    // --------------------------------------------------- tamaño de la imagen

    private fun sizeKind(item: PlayItem): String = when {
        item.isLive -> "live"
        item.resume?.type == "episode" -> "episode"
        else -> "movie"
    }

    private fun applySize(item: PlayItem) {
        playerView.resizeMode = when (PlayerPrefs.sizeMode(this, sizeKind(item))) {
            PlayerPrefs.SIZE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerPrefs.SIZE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun showSizeDialog() {
        val item = items.getOrNull(index) ?: return
        val kind = sizeKind(item)
        val labels = arrayOf(
            "Normal (se ve completa)",
            "Llenar la pantalla (estira la imagen)",
            "Acercar (recorta los bordes)"
        )
        AlertDialog.Builder(this)
            .setTitle("Tamaño de la imagen")
            .setSingleChoiceItems(labels, PlayerPrefs.sizeMode(this, kind)) { dialog, which ->
                PlayerPrefs.setSizeMode(this, kind, which)
                applySize(item)
                dialog.dismiss()
            }
            .setOnDismissListener { playerView.requestFocus() }
            .show()
    }

    private fun showOptionsMenu() {
        val item = items.getOrNull(index) ?: return
        if (item.isLive) {
            showSizeDialog()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Opciones")
            .setItems(arrayOf("Subtítulos", "Tamaño de la imagen")) { _, which ->
                if (which == 0) showSubtitleDialog() else showSizeDialog()
            }
            .setOnDismissListener { playerView.requestFocus() }
            .show()
    }

    private fun optionsFocused(): Boolean =
        ::btnSize.isInitialized && (btnSize.hasFocus() || btnSubtitles.hasFocus())

    // ------------------------------------------------------------- subtítulos

    private class TextChoice(
        val group: Tracks.Group,
        val trackIndex: Int,
        val label: String,
        val lang: String?,
        val selected: Boolean
    )

    /** "spa", "es-MX" → "es". */
    private fun normalizedLang(code: String?): String? {
        if (code.isNullOrBlank() || code == "und") return null
        return Util.normalizeLanguageCode(code).substringBefore('-').lowercase().ifEmpty { null }
    }

    private fun languageDisplay(code: String): String =
        java.util.Locale(code).getDisplayLanguage(java.util.Locale("es")).replaceFirstChar { it.uppercase() }.ifEmpty { code }

    private fun textChoices(): List<TextChoice> {
        val exo = player ?: return emptyList()
        val out = ArrayList<TextChoice>()
        for (group in exo.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                val lang = normalizedLang(format.language)
                val name = format.label?.takeIf { it.isNotBlank() }
                    ?: lang?.let { languageDisplay(it) }
                    ?: "Subtítulo ${out.size + 1}"
                out += TextChoice(group, i, name, lang, group.isTrackSelected(i))
            }
        }
        return out
    }

    /** Al empezar un video: subtítulos del idioma preferido si lo hay, o apagados. */
    private fun applyTextPreference(item: PlayItem) {
        val exo = player ?: return
        val pref = if (item.isLive) "off" else SubtitleSettings.language(this)
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, pref == "off")
            .setPreferredTextLanguage(if (pref == "off") null else pref)
            .build()
    }

    /** El video con los subtítulos que ya se bajaron de internet para esta película o episodio. */
    private fun buildMediaItem(item: PlayItem): MediaItem {
        val builder = MediaItem.Builder().setUri(item.url)
        val key = item.subs?.cacheKey
        if (!item.isLive && key != null) {
            val configs = SubtitleFiles.saved(this, key).map { (lang, file) ->
                MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage(lang)
                    .setLabel("${languageDisplay(lang)} (internet)")
                    .build()
            }
            if (configs.isNotEmpty()) builder.setSubtitleConfigurations(configs)
        }
        return builder.build()
    }

    private fun showSubtitleDialog() {
        val exo = player ?: return
        val item = items.getOrNull(index) ?: return
        if (item.isLive) return

        val choices = textChoices()
        val textOff = exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        var checked = 0

        labels += "Desactivados"
        actions += { turnOffSubtitles() }
        choices.forEachIndexed { i, choice ->
            labels += choice.label
            actions += { chooseTrack(choice) }
            if (choice.selected && !textOff) checked = i + 1
        }
        if (item.subs != null) {
            for (lang in listOf("es", "en")) {
                if (choices.none { it.lang == lang }) {
                    labels += "Buscar en internet: ${languageDisplay(lang)}"
                    actions += { searchOnline(lang, fromAuto = false) }
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Subtítulos")
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                actions[which]()
            }
            .setOnDismissListener { playerView.requestFocus() }
            .show()
    }

    private fun chooseTrack(choice: TextChoice) {
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, choice.trackIndex))
            .build()
        // Se recuerda el idioma para los próximos videos.
        choice.lang?.takeIf { it == "es" || it == "en" }?.let { SubtitleSettings.setLanguage(this, it) }
    }

    private fun turnOffSubtitles() {
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        SubtitleSettings.setLanguage(this, "off")
    }

    /**
     * Con un idioma preferido (español o inglés): si el video trae ese subtítulo se activa solo (se elige la pista
     * a propósito, sin depender de cómo escriba el idioma el archivo: "spa", "es", "es-MX"…); si no lo trae,
     * se busca en internet (una sola vez por video).
     */
    private fun maybeAutoSubtitles(tracks: Tracks) {
        val item = items.getOrNull(index) ?: return
        if (item.isLive) return
        val pref = SubtitleSettings.language(this)
        if (pref == "off") return
        val exo = player ?: return

        var foundGroup: Tracks.Group? = null
        var foundIndex = 0
        var anySelected = false
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                if (group.isTrackSelected(i)) anySelected = true
                if (foundGroup == null && normalizedLang(group.getTrackFormat(i).language) == pref) {
                    foundGroup = group
                    foundIndex = i
                }
            }
        }
        val group = foundGroup
        if (group != null) {
            if (!anySelected && autoPicked.add("${item.url}|$pref")) {
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, foundIndex))
                    .build()
            }
            return
        }
        val query = item.subs ?: return
        if (!autoSubTried.add("${query.cacheKey}:$pref")) return
        searchOnline(pref, fromAuto = true)
    }

    private fun searchOnline(lang: String, fromAuto: Boolean) {
        val item = items.getOrNull(index) ?: return
        val query = item.subs ?: return
        if (SubtitleSettings.credentials(this) == null) {
            if (!fromAuto) showNoKeyDialog()
            return
        }
        if (fetchingSubs) return
        fetchingSubs = true
        Toast.makeText(this, "Buscando subtítulos en ${SubtitleService.languageName(lang)}…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                SubtitleService.fetch(this@PlayerActivity, query, lang)
                reloadWithSubtitles(lang)
                Toast.makeText(this@PlayerActivity, "Subtítulos en ${SubtitleService.languageName(lang)} listos", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SubtitleException) {
                Toast.makeText(this@PlayerActivity, e.message ?: "No se pudieron conseguir los subtítulos.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@PlayerActivity,
                    "No se pudieron conseguir los subtítulos (${e.javaClass.simpleName}). Revisa tu conexión.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                fetchingSubs = false
            }
        }
    }

    /** Vuelve a cargar el video en el mismo minuto, ahora con el subtítulo nuevo, y lo activa. */
    private fun reloadWithSubtitles(lang: String) {
        val exo = player ?: return
        val item = items.getOrNull(index) ?: return
        val position = exo.currentPosition
        val wasPlaying = exo.playWhenReady
        SubtitleSettings.setLanguage(this, lang)
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(lang)
            .build()
        exo.setMediaItem(buildMediaItem(item), position)
        exo.prepare()
        exo.playWhenReady = wasPlaying
    }

    private fun showNoKeyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Subtítulos en internet")
            .setMessage(
                "Para buscar subtítulos hace falta una clave gratuita de OpenSubtitles:\n\n" +
                    "1. Entra a opensubtitles.com y crea una cuenta gratis.\n" +
                    "2. En tu cuenta abre \"API consumers\" y crea una clave.\n" +
                    "3. En Ultra Premier TV: Ajustes → Subtítulos en internet, y escribe la clave."
            )
            .setPositiveButton("Entendido", null)
            .setOnDismissListener { playerView.requestFocus() }
            .show()
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

            // Teclas de opciones: Menú (≡) abre Subtítulos / Tamaño; la tecla CC abre los subtítulos.
            if (key == KeyEvent.KEYCODE_MENU) {
                if (event.repeatCount == 0) showOptionsMenu()
                return true
            }
            if (key == KeyEvent.KEYCODE_CAPTIONS) {
                if (event.repeatCount == 0 && !items[index].isLive) showSubtitleDialog()
                return true
            }

            // Con un botón de arriba enfocado: izquierda/derecha/OK los manejan ellos; abajo vuelve al video.
            if (optionsFocused()) {
                when (key) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        playerView.requestFocus()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> return true
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> return super.dispatchKeyEvent(event)
                }
                if (isOk) return super.dispatchKeyEvent(event)
            }

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
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // Primero muestra la información; la siguiente pulsación pasa a los botones de arriba.
                        if (firstPress) {
                            if (optionsBar.visibility == View.VISIBLE) {
                                btnSize.requestFocus()
                                handler.removeCallbacks(hideBannerTask)
                                handler.postDelayed(hideBannerTask, BANNER_DURATION_MS)
                            } else {
                                showBanner(item)
                            }
                        }
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
            hintText.text = "▲ ▼  Cambiar de canal      OK  Información      ◀ ▶  Tamaño      Atrás  Salir"
            hintText.visibility = View.VISIBLE
        } else {
            hintText.visibility = View.GONE
        }

        banner.visibility = View.VISIBLE
        if (item.isLive) {
            btnSubtitles.visibility = View.GONE
            optionsBar.visibility = View.VISIBLE
        }
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
                repository.epgNowNext(streamId)
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
        private const val SAVE_EVERY_MS = 15_000L

        fun start(context: Context, items: List<PlayItem>, startIndex: Int) {
            PlaybackQueue.items = items
            PlaybackQueue.startIndex = startIndex
            context.startActivity(Intent(context, PlayerActivity::class.java))
        }
    }
}
