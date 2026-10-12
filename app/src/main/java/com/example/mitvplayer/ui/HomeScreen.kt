package com.example.mitvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mitvplayer.R
import com.example.mitvplayer.data.LiveChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: AppViewModel) {
    val tabRequesters = remember { Tab.values().associateWith { FocusRequester() } }

    // Al entrar (o al volver de un detalle) el foco regresa al elemento que se abrió;
    // si no se puede, queda en la pestaña activa.
    LaunchedEffect(Unit) {
        val browse = vm.activeBrowse()
        var focused = false
        if (browse != null && browse.restoring) {
            withFrameNanos { }
            withFrameNanos { }
            focused = try {
                browse.restoreRequester.requestFocus()
                true
            } catch (e: Exception) {
                false
            }
            browse.restoring = false
        }
        if (!focused) tabRequesters.getValue(vm.tab).tryRequestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(horizontal = 32.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AccentColor
            )
            Spacer(Modifier.width(32.dp))
            Tab.values().forEach { tab ->
                TabChip(
                    label = tab.label,
                    selected = vm.tab == tab,
                    requester = tabRequesters.getValue(tab),
                    onSelect = { vm.tab = tab }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (vm.tab) {
                Tab.LIVE -> LiveTab(vm)
                Tab.MOVIES -> MoviesTab(vm)
                Tab.SERIES -> SeriesTab(vm)
                Tab.ACCOUNT -> AccountTab(vm)
            }
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val background = when {
        focused -> AccentColor
        selected -> PanelFocusedColor
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .clip(shape)
            .background(background, shape)
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (focused) OnAccentColor else TextPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** Categorías a la izquierda y el contenido de la categoría elegida a la derecha. */
@Composable
private fun <T> BrowseTab(
    state: BrowseState<T>,
    emptyText: String,
    content: @Composable (items: List<T>) -> Unit
) {
    LaunchedEffect(Unit) { state.ensureLoaded() }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state.categoryListState,
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        ) {
            items(state.categories) { category ->
                val isSelected = state.selected?.id == category.id
                FocusCard(
                    onClick = { state.select(category) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    scaleOnFocus = 1.03f,
                    selected = isSelected
                ) {
                    Text(
                        text = category.name,
                        color = if (isSelected) AccentColor else TextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(20.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val error = state.error
            when {
                error != null -> ErrorWithRetry(message = error, onRetry = { state.retry() })
                state.loading -> CenterLoading()
                state.items.isEmpty() -> CenterMessage(emptyText)
                else -> content(state.items)
            }
        }
    }
}

@Composable
private fun LiveTab(vm: AppViewModel) {
    val context = LocalContext.current
    BrowseTab(state = vm.live, emptyText = "No hay canales en esta categoría") { channels ->
        LazyColumn(
            state = vm.live.itemsListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        ) {
            itemsIndexed(channels) { index, channel ->
                FocusCard(
                    onClick = { vm.playLive(context, channels, index) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    scaleOnFocus = 1.02f
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelLogo(channel)
                        Spacer(Modifier.width(14.dp))
                        if (channel.number > 0) {
                            Text(
                                text = channel.number.toString(),
                                color = TextSecondary,
                                modifier = Modifier.width(44.dp)
                            )
                        }
                        Text(
                            text = channel.name,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelLogo(channel: LiveChannel) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1B2332)),
        contentAlignment = Alignment.Center
    ) {
        val icon = channel.icon
        if (icon == null) {
            Text(text = channel.name.take(1).uppercase(), color = TextSecondary)
        } else {
            AsyncImage(
                model = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun MoviesTab(vm: AppViewModel) {
    val state = vm.movies
    BrowseTab(state = state, emptyText = "No hay películas en esta categoría") { movies ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            state = state.gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(movies) { index, movie ->
                PosterTile(
                    title = movie.name,
                    imageUrl = movie.poster,
                    onClick = { vm.openMovie(movie) },
                    modifier = Modifier
                        .focusIf(state.restoring && index == state.lastFocusedIndex, state.restoreRequester)
                        .onFocusChanged { if (it.isFocused) state.lastFocusedIndex = index }
                )
            }
        }
    }
}

@Composable
private fun SeriesTab(vm: AppViewModel) {
    val state = vm.series
    BrowseTab(state = state, emptyText = "No hay series en esta categoría") { shows ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            state = state.gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(shows) { index, show ->
                PosterTile(
                    title = show.name,
                    imageUrl = show.cover,
                    onClick = { vm.openSeries(show) },
                    modifier = Modifier
                        .focusIf(state.restoring && index == state.lastFocusedIndex, state.restoreRequester)
                        .onFocusChanged { if (it.isFocused) state.lastFocusedIndex = index }
                )
            }
        }
    }
}

@Composable
private fun AccountTab(vm: AppViewModel) {
    val account = vm.account
    val expires = account?.expiresAt?.let { seconds ->
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(seconds * 1000))
    } ?: "Sin vencimiento"
    val connections = "${account?.activeConnections ?: "—"} de ${account?.maxConnections ?: "—"}"
    val formatName = if (vm.liveFormat == "m3u8") "HLS (m3u8)" else "MPEG-TS"

    Column(
        modifier = Modifier
            .width(560.dp)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Mi cuenta",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        InfoRow("Usuario", account?.username ?: "—")
        InfoRow("Estado", account?.status ?: "—")
        InfoRow("Vence", expires)
        InfoRow("Conexiones", connections)

        Spacer(Modifier.height(8.dp))
        TvButton(
            text = "Formato de TV en vivo: $formatName (cambiar)",
            onClick = { vm.toggleLiveFormat() },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Si un canal se corta o no abre, prueba cambiando el formato.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(Modifier.height(8.dp))
        TvButton(
            text = "Cerrar sesión",
            onClick = { vm.logout() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary)
        Text(text = value, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}
