package com.example.mitvplayer.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.mitvplayer.Config
import com.example.mitvplayer.R
import com.example.mitvplayer.RecordingManager
import com.example.mitvplayer.data.EpgEntry
import com.example.mitvplayer.data.Kind
import com.example.mitvplayer.data.LiveChannel
import kotlinx.coroutines.delay

/** Reproductor pequeño de la vista previa de En vivo. Solo existe mientras se está viendo un canal. */
@androidx.annotation.OptIn(UnstableApi::class)
class LivePreview(private val context: Context) {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var channel by mutableStateOf<LiveChannel?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            failed = true
            buffering = false
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            buffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) failed = false
        }
    }

    fun play(channel: LiveChannel, url: String) {
        release()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Config.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        exo.addListener(listener)
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
        player = exo
        this.channel = channel
        failed = false
    }

    fun stop() {
        release()
        channel = null
    }

    /** Libera la conexión pero deja el canal elegido (por ejemplo, mientras se graba con una sola conexión). */
    fun pauseKeepingChannel() {
        release()
    }

    private fun release() {
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
        buffering = false
    }
}

/** Reloj y fecha de la barra superior. */
@Composable
fun ClockText(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }
    Text(
        text = "${formatClock(now)}    ${formatDateLong(now)}",
        color = TextPrimary,
        fontSize = 13.sp,
        maxLines = 1,
        modifier = modifier
    )
}

private fun emptyMessage(state: BrowseState<LiveChannel>, radio: Boolean): String = when {
    radio && state.catalog != null && state.rows.size <= 3 ->
        "Tu servidor no tiene categorías de radio.\n(Se buscan categorías cuyo nombre diga \"radio\".)"
    state.selectedId == ROW_FAV ->
        "Aún no tienes canales favoritos.\nMantén pulsado un canal, o pulsa la tecla Menú del control, para añadirlo."
    state.selectedId == ROW_RECENT -> "Todavía no has visto ningún canal."
    else -> "No hay canales en esta categoría"
}

@Composable
fun LiveScreen(vm: AppViewModel, state: BrowseState<LiveChannel>, title: String, radio: Boolean = false) {
    val context = LocalContext.current
    val preview = remember { LivePreview(context) }
    val firstRow = remember { FocusRequester() }

    LaunchedEffect(Unit) { state.ensureLoaded() }
    LaunchedEffect(state.catalog) {
        if (state.catalog != null) {
            withFrameNanos { }
            withFrameNanos { }
            firstRow.tryRequestFocus()
        }
    }

    // La vista previa se corta al salir de la pantalla o al abrir otra app: así no se ocupan dos conexiones.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) preview.stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            preview.stop()
        }
    }

    val channels = state.items

    AppBackground(art = R.drawable.art_live, dim = 0.58f) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = "|  $title",
                onBack = { vm.back() },
                onSearch = { vm.open(Screen.Search(SearchScope.LIVE)) },
                menu = sectionMenu(state, listOf(SortMode.DEFAULT, SortMode.AZ, SortMode.ZA)),
                trailing = {
                    ClockText()
                    Spacer(Modifier.width(8.dp))
                }
            )
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                CategorySidebar(
                    state = state,
                    modifier = Modifier
                        .weight(0.30f)
                        .fillMaxHeight(),
                    firstRowRequester = firstRow
                )
                Box(
                    Modifier
                        .weight(0.36f)
                        .fillMaxHeight()
                ) {
                    SectionStatus(state, emptyMessage(state, radio)) {
                        LazyColumn(
                            state = state.itemsListState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(channels) { index, channel ->
                                ChannelRow(
                                    vm = vm,
                                    channel = channel,
                                    playing = preview.channel?.id == channel.id,
                                    favorite = vm.isFavorite(Kind.LIVE, channel.id),
                                    onClick = {
                                        if (preview.channel?.id == channel.id && !preview.failed) {
                                            // Segundo OK sobre el mismo canal: pantalla completa.
                                            preview.stop()
                                            vm.playLive(context, channels, index)
                                        } else {
                                            vm.markWatched(channel)
                                            preview.play(channel, vm.liveUrl(channel))
                                        }
                                    },
                                    onLongClick = { vm.toggleFavorite(Kind.LIVE, channel.id) }
                                )
                            }
                        }
                    }
                }
                PreviewPane(
                    vm = vm,
                    preview = preview,
                    modifier = Modifier
                        .weight(0.34f)
                        .fillMaxHeight(),
                    onFullscreen = { channel ->
                        preview.stop()
                        val index = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
                        vm.playLive(context, channels, index)
                    },
                    // Ya no hay botón para empezar a grabar aquí; solo para detener una grabación en curso.
                    onRecord = { _ -> RecordingManager.stop() }
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    vm: AppViewModel,
    channel: LiveChannel,
    playing: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val epg by produceState<List<EpgEntry>?>(null, channel.id) { value = vm.epgFor(channel.id) }
    val nowSec = System.currentTimeMillis() / 1000
    val current = epg?.firstOrNull { nowSec >= it.start && nowSec < it.end }
    val progress = current?.let { ((nowSec - it.start).toFloat() / (it.end - it.start).coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f
    val programText = when {
        current != null && current.title.isNotBlank() -> current.title
        epg == null -> ""
        else -> "● En vivo"
    }

    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        scaleOnFocus = 1f,
        selected = playing,
        background = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelLogo(icon = channel.icon, name = channel.name, size = 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = programText,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0x55FFFFFF))
                ) {
                    if (progress > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(AccentColor)
                        )
                    }
                }
            }
            if (favorite) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Star, contentDescription = "Favorito", tint = StarColor, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PreviewPane(
    vm: AppViewModel,
    preview: LivePreview,
    modifier: Modifier,
    onFullscreen: (LiveChannel) -> Unit,
    onRecord: (LiveChannel) -> Unit
) {
    val channel = preview.channel
    val player = preview.player
    val recording = RecordingManager.current
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(recording != null) {
        while (recording != null) {
            tick = System.currentTimeMillis()
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (channel == null) {
                Image(
                    bitmap = rememberArt(R.drawable.art_live),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (player != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            isFocusable = false
                        }
                    },
                    update = { view -> view.player = player },
                    modifier = Modifier.fillMaxSize()
                )
            }
            when {
                preview.failed -> Text(
                    "No se pudo reproducir este canal.\nPulsa OK para reintentar.",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
                channel == null -> Text(
                    "Elige un canal y pulsa OK",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                player == null && recording != null -> Text(
                    "Grabando…\nLa vista previa está en pausa.",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
                preview.buffering -> CircularProgressIndicator(color = AccentColor, modifier = Modifier.size(28.dp))
            }
        }

        if (channel != null) {
            val epg by produceState<List<EpgEntry>?>(null, channel.id) { value = vm.epgFor(channel.id) }
            val nowSec = System.currentTimeMillis() / 1000
            val sorted = epg.orEmpty()
            val now = sorted.firstOrNull { nowSec >= it.start && nowSec < it.end }
            val next = sorted.firstOrNull { it.start >= nowSec }

            Text(
                channel.name,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (now != null && now.title.isNotBlank()) {
                Text(
                    "Ahora: ${now.title}  (${formatHm(now.start)} – ${formatHm(now.end)})",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (next != null && next.title.isNotBlank()) {
                Text(
                    "Después: ${next.title}  (${formatHm(next.start)})",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val favorite = vm.isFavorite(Kind.LIVE, channel.id)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TvButton(
                    text = if (favorite) "★ Favorito" else "☆ Favorito",
                    onClick = { vm.toggleFavorite(Kind.LIVE, channel.id) },
                    compact = true,
                    selected = favorite
                )
                TvButton(text = "Pantalla completa", onClick = { onFullscreen(channel) }, compact = true)
            }
            if (recording != null) {
                val elapsed = ((tick - recording.startedAt) / 1000).coerceAtLeast(0)
                Text(
                    "● Grabando ${recording.channelName} · ${formatDuration(elapsed)}",
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recording != null) {
                TvButton(
                    text = "■ Detener grabación",
                    onClick = { onRecord(channel) },
                    compact = true,
                    selected = true
                )
            }
        }
    }
}
