package com.example.mitvplayer.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.mitvplayer.Config
import com.example.mitvplayer.data.LiveChannel
import com.example.mitvplayer.data.searchKey

/** Un reproductor por celda. Solo la celda con audio activo suena; las demás van en silencio. */
@androidx.annotation.OptIn(UnstableApi::class)
class MultiPlayerHost(private val context: Context) {
    val players = mutableStateMapOf<Int, ExoPlayer>()

    fun assign(index: Int, url: String, active: Boolean) {
        remove(index)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Config.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        // Con varias imágenes a la vez se limita la calidad para que el equipo aguante.
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(1280, 720)
            .build()
        exo.volume = if (active) 1f else 0f
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
        players[index] = exo
    }

    fun remove(index: Int) {
        players.remove(index)?.release()
    }

    fun setActive(active: Int) {
        players.entries.toList().forEach { (i, p) -> p.volume = if (i == active) 1f else 0f }
    }

    fun releaseAll() {
        val all = players.values.toList()
        players.clear()
        all.forEach { it.release() }
    }
}

@Composable
fun MultiScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val host = remember { MultiPlayerHost(context) }
    var chooser by remember { mutableStateOf(vm.multiLayout == null) }
    var pickIndex by remember { mutableStateOf<Int?>(null) }
    var optionsIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { vm.live.ensureLoaded() }

    fun restart() {
        host.releaseAll()
        vm.multiChannels.forEachIndexed { i, channel ->
            if (channel != null) host.assign(i, vm.liveUrl(channel), i == vm.multiActive)
        }
    }

    // Al salir de la pantalla (o abrir otra app) se cortan todos los canales; al volver se reanudan.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> restart()
                Lifecycle.Event.ON_STOP -> host.releaseAll()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            host.releaseAll()
        }
    }

    val layout = vm.multiLayout?.let { MultiLayouts[it] }
    val filled = vm.multiChannels.count { it != null }
    val maxConnections = vm.account?.maxConnections

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = "Pantalla múltiple",
                onBack = { vm.back() },
                trailing = {
                    TvButton(text = "Cambiar diseño", onClick = { chooser = true }, compact = true)
                    Spacer(Modifier.width(6.dp))
                }
            )
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                if (layout == null) {
                    CenterMessage("Elige un diseño para empezar")
                } else {
                    val gap = 3.dp
                    layout.forEachIndexed { i, rect ->
                        MultiCell(
                            channel = vm.multiChannels.getOrNull(i),
                            player = host.players[i],
                            active = vm.multiActive == i,
                            modifier = Modifier
                                .offset(x = maxWidth * rect.x, y = maxHeight * rect.y)
                                .size(width = maxWidth * rect.w - gap, height = maxHeight * rect.h - gap),
                            onClick = {
                                if (vm.multiChannels.getOrNull(i) == null) pickIndex = i else optionsIndex = i
                            }
                        )
                    }
                }
            }
            if (maxConnections != null && filled > maxConnections) {
                Text(
                    text = "Tu cuenta permite $maxConnections conexión(es) a la vez. Con $filled canales, es posible que algunos no abran.",
                    color = Color(0xFFFFC93C),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }

    if (chooser) {
        LayoutChooserDialog(
            onChoose = { index ->
                chooser = false
                vm.chooseMultiLayout(index)
                restart()
            },
            onDismiss = {
                chooser = false
                if (vm.multiLayout == null) vm.back()
            }
        )
    }

    val picking = pickIndex
    if (picking != null) {
        ChannelPickerDialog(
            vm = vm,
            onPick = { channel ->
                vm.multiChannels[picking] = channel
                if (vm.multiChannels.count { it != null } == 1) vm.multiActive = picking
                host.assign(picking, vm.liveUrl(channel), vm.multiActive == picking)
                vm.markWatched(channel)
            },
            onDismiss = { pickIndex = null }
        )
    }

    val options = optionsIndex
    val optionChannel = options?.let { vm.multiChannels.getOrNull(it) }
    if (options != null && optionChannel != null) {
        OptionsDialog(
            title = optionChannel.name,
            options = listOf(
                Opt("Activar audio de este canal") {
                    vm.multiActive = options
                    host.setActive(options)
                },
                Opt("Cambiar canal") { pickIndex = options },
                Opt("Quitar canal") {
                    vm.multiChannels[options] = null
                    host.remove(options)
                },
                Opt("Ver en pantalla completa") {
                    host.releaseAll()
                    val list = vm.multiChannels.filterNotNull()
                    val start = list.indexOfFirst { it.id == optionChannel.id }.coerceAtLeast(0)
                    vm.playLive(context, list, start)
                },
                Opt("Cancelar") { }
            ),
            onDismiss = { optionsIndex = null }
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MultiCell(
    channel: LiveChannel?,
    player: ExoPlayer?,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        scaleOnFocus = 1f,
        selected = active && channel != null,
        background = Color(0xFF0B0B14)
    ) {
        if (channel == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(Glyph.PLUS, Modifier.size(26.dp))
                }
            }
        } else {
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
            } else {
                CenterLoading()
            }
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color(0x99000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            if (active) {
                Text(
                    text = "AUDIO",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentColor)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/** "Elija diseño de pantalla múltiple": los seis diseños posibles. */
@Composable
private fun LayoutChooserDialog(onChoose: (Int) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        first.tryRequestFocus()
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Elija diseño de pantalla múltiple", color = Color.White, fontSize = 18.sp)
            for (rowStart in listOf(0, 3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (index in rowStart until rowStart + 3) {
                        FocusCard(
                            onClick = { onChoose(index) },
                            modifier = Modifier
                                .size(width = 110.dp, height = 64.dp)
                                .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                            shape = RoundedCornerShape(2.dp),
                            scaleOnFocus = 1.08f,
                            background = Color(0xFFC9C9C9),
                            focusedBackground = Color(0xFFC9C9C9)
                        ) {
                            LayoutPreview(MultiLayouts[index], Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

/** Lista de canales con buscador para llenar una celda de la pantalla múltiple. */
@Composable
fun ChannelPickerDialog(vm: AppViewModel, onPick: (LiveChannel) -> Unit, onDismiss: () -> Unit) {
    val state = vm.live
    var query by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(ROW_ALL) }
    LaunchedEffect(Unit) { state.ensureLoaded() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF12153A))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Elige un canal",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                IconAction(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = TextPrimary)
                }
            }
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Buscar canal",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            val catalog = state.catalog
            if (catalog == null) {
                CenterLoading(text = "Cargando canales…")
            } else {
                val words = searchKey(query).trim().split(' ').filter { it.isNotEmpty() }
                val list = remember(words, categoryId, catalog) {
                    if (words.isNotEmpty() && words.joinToString("").length >= 2) {
                        catalog.items.filter { channel -> words.all { channel.key.contains(it) } }.take(200)
                    } else {
                        state.itemsFor(categoryId)
                    }
                }
                Row(Modifier.weight(1f)) {
                    LazyColumn(
                        Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .background(SidebarColor)
                    ) {
                        items(state.rows) { row ->
                            SidebarRow(
                                row = row,
                                selected = row.id == categoryId,
                                onClick = {
                                    categoryId = row.id
                                    query = ""
                                }
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    LazyColumn(
                        Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                    ) {
                        items(list) { channel ->
                            FocusCard(
                                onClick = {
                                    onDismiss()
                                    onPick(channel)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(0.dp),
                                scaleOnFocus = 1f,
                                background = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ChannelLogo(icon = channel.icon, name = channel.name, size = 36.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        channel.name,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
