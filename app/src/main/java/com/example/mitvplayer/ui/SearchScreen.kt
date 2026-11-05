package com.example.mitvplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mitvplayer.data.Item
import com.example.mitvplayer.data.LiveChannel
import com.example.mitvplayer.data.Movie
import com.example.mitvplayer.data.SeriesItem
import com.example.mitvplayer.data.searchKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private class SearchResults(
    val live: List<LiveChannel>,
    val movies: List<Movie>,
    val series: List<SeriesItem>
) {
    val isEmpty: Boolean get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()

    companion object {
        val EMPTY = SearchResults(emptyList(), emptyList(), emptyList())
    }
}

private const val MAX_RESULTS = 60

private fun <T : Item> findMatches(items: List<T>?, words: List<String>): List<T> {
    if (items == null) return emptyList()
    val hits = ArrayList<T>()
    for (item in items) {
        if (words.all { item.key.contains(it) }) {
            hits.add(item)
            if (hits.size >= 400) break
        }
    }
    // Primero los que empiezan con lo escrito.
    val first = words.first()
    return hits.sortedBy { if (it.key.startsWith(first)) 0 else 1 }.take(MAX_RESULTS)
}

/** Búsqueda maestra: canales, películas y series a la vez, sin acentos ni mayúsculas. */
@Composable
fun SearchScreen(vm: AppViewModel, initialScope: SearchScope) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(initialScope) }
    val field = remember { FocusRequester() }
    val firstKey = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        vm.live.ensureLoaded()
        vm.movies.ensureLoaded()
        vm.series.ensureLoaded()
        withFrameNanos { }
        withFrameNanos { }
        firstKey.tryRequestFocus()
    }

    // Sin lo que esconde el control parental.
    val liveItems = vm.live.visibleItems
    val movieItems = vm.movies.visibleItems
    val seriesItems = vm.series.visibleItems

    val results by produceState(SearchResults.EMPTY, query, liveItems, movieItems, seriesItems) {
        val words = searchKey(query).trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || words.joinToString("").length < 2) {
            value = SearchResults.EMPTY
            return@produceState
        }
        delay(250)
        value = withContext(Dispatchers.Default) {
            SearchResults(
                live = findMatches(liveItems, words),
                movies = findMatches(movieItems, words),
                series = findMatches(seriesItems, words)
            )
        }
    }
    val total = results.live.size + results.movies.size + results.series.size
    val shown: List<Any> = when (scope) {
        SearchScope.ALL -> results.movies + results.series + results.live
        SearchScope.MOVIES -> results.movies
        SearchScope.SERIES -> results.series
        SearchScope.LIVE -> results.live
    }

    val stillLoading = vm.live.loading || vm.movies.loading || vm.series.loading

    AppBackground(dim = 0.6f) {
        Column(Modifier.fillMaxSize()) {
            UltraSectionHeader(
                vm = vm,
                active = UltraTab.SEARCH,
                onActive = { firstKey.tryRequestFocus() },
                title = "Buscar",
                icon = { t -> Icon(Icons.Filled.Search, null, tint = t, modifier = Modifier.size(18.dp)) }
            )
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 36.dp, end = 36.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ------------------------------------ campo y teclado en pantalla
                Column(Modifier.width(214.dp)) {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Canal, película o serie",
                        modifier = Modifier.fillMaxWidth(),
                        requester = field
                    )
                    Spacer(Modifier.height(8.dp))
                    ScreenKeyboard(
                        firstKey = firstKey,
                        onKey = { query += it },
                        onSpace = { if (query.isNotEmpty() && !query.endsWith(" ")) query += " " },
                        onDelete = { query = query.dropLast(1) }
                    )
                }
                // ------------------------------------ filtros y resultados
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            SearchScope.ALL to "Todo ($total)",
                            SearchScope.MOVIES to "Películas (${results.movies.size})",
                            SearchScope.SERIES to "Series (${results.series.size})",
                            SearchScope.LIVE to "Canales (${results.live.size})"
                        ).forEach { (option, label) ->
                            SlantChip(text = label, selected = option == scope, onClick = { scope = option })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val typed = searchKey(query).trim().length >= 2
                        when {
                            !typed -> CenterMessage(
                                if (stillLoading) "Cargando las listas…\nYa puedes escribir." else "Escribe al menos 2 letras con el teclado."
                            )
                            results.isEmpty && stillLoading -> CenterLoading(text = "Buscando…")
                            shown.isEmpty() -> CenterMessage("Sin resultados para \"${query.trim()}\"")
                            else -> LazyVerticalGrid(
                                columns = GridCells.Fixed(5),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(shown.size) { i ->
                                    when (val hit = shown[i]) {
                                        is Movie -> UltraPosterCard(
                                            title = hit.name,
                                            imageUrl = hit.poster,
                                            rating10 = hit.rating10,
                                            favorite = false,
                                            onClick = { vm.openMovie(hit, restoreFocus = false) }
                                        )
                                        is SeriesItem -> UltraPosterCard(
                                            title = hit.name,
                                            imageUrl = hit.cover,
                                            rating10 = hit.rating10,
                                            favorite = false,
                                            onClick = { vm.openSeries(hit, restoreFocus = false) }
                                        )
                                        is LiveChannel -> ChannelTile(
                                            channel = hit,
                                            onClick = {
                                                vm.playLive(context, results.live, results.live.indexOf(hit).coerceAtLeast(0))
                                            }
                                        )
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private const val KEYS = "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ1234567890"

/** Teclado en pantalla para escribir con las flechas del control (sin abrir el teclado del sistema). */
@Composable
private fun ScreenKeyboard(
    firstKey: FocusRequester,
    onKey: (String) -> Unit,
    onSpace: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KEYS.chunked(6).forEachIndexed { row, chars ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chars.forEachIndexed { col, c ->
                    KeyButton(
                        label = c.toString(),
                        modifier = Modifier
                            .width(32.dp)
                            .then(if (row == 0 && col == 0) Modifier.focusRequester(firstKey) else Modifier)
                    ) { onKey(c.toString().lowercase()) }
                }
                if (row == KEYS.length / 6) {
                    KeyButton("Espacio", Modifier.width(66.dp)) { onSpace() }
                    KeyButton("Borrar", Modifier.width(66.dp)) { onDelete() }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .height(23.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) AccentBrush else androidx.compose.ui.graphics.SolidColor(Color(0x14FFFFFF)))
            .border(if (focused) 1.5.dp else 0.5.dp, if (focused) Color.White else Color(0x1FFFFFFF), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) Color.White else TextPrimary,
            fontSize = if (label.length > 1) 8.sp else 9.5.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** Canal en los resultados: su logo al centro de una tarjeta del mismo tamaño que los pósters. */
@Composable
private fun ChannelTile(channel: LiveChannel, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        FocusCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            shape = RoundedCornerShape(9.dp),
            scaleOnFocus = 1.07f
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
                contentAlignment = Alignment.Center
            ) {
                ChannelLogo(icon = channel.icon, name = channel.name, size = 56.dp)
                Text(
                    text = "EN VIVO",
                    color = Color.White,
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(AccentColor, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            text = channel.name,
            color = if (focused) Color.White else TextSecondary,
            fontSize = 8.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp)
        )
    }
}

private fun ratingText(kind: String, rating10: Float): String =
    if (rating10 >= 0.5f) "$kind  ·  ★ ${"%.1f".format(rating10)}" else kind

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = AccentColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ResultRow(
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    FocusCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        scaleOnFocus = 1.01f
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = subtitle, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
