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

    LaunchedEffect(Unit) {
        vm.live.ensureLoaded()
        vm.movies.ensureLoaded()
        vm.series.ensureLoaded()
        withFrameNanos { }
        withFrameNanos { }
        field.tryRequestFocus()
    }

    // Sin lo que esconde el control parental.
    val liveItems = vm.live.visibleItems
    val movieItems = vm.movies.visibleItems
    val seriesItems = vm.series.visibleItems

    val results by produceState(SearchResults.EMPTY, query, scope, liveItems, movieItems, seriesItems) {
        val words = searchKey(query).trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || words.joinToString("").length < 2) {
            value = SearchResults.EMPTY
            return@produceState
        }
        delay(250)
        value = withContext(Dispatchers.Default) {
            SearchResults(
                live = if (scope == SearchScope.ALL || scope == SearchScope.LIVE) findMatches(liveItems, words) else emptyList(),
                movies = if (scope == SearchScope.ALL || scope == SearchScope.MOVIES) findMatches(movieItems, words) else emptyList(),
                series = if (scope == SearchScope.ALL || scope == SearchScope.SERIES) findMatches(seriesItems, words) else emptyList()
            )
        }
    }

    val stillLoading = vm.live.loading || vm.movies.loading || vm.series.loading

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Búsqueda maestra", centerTitle = true, onBack = { vm.back() })
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Escribe el nombre de un canal, película o serie",
                    modifier = Modifier.fillMaxWidth(),
                    requester = field
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchScope.values().forEach { option ->
                        TvButton(
                            text = option.label,
                            onClick = { scope = option },
                            selected = option == scope,
                            compact = true
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val typed = searchKey(query).trim().length >= 2
                when {
                    !typed -> CenterMessage(
                        if (stillLoading) "Cargando las listas…\nYa puedes escribir." else "Escribe al menos 2 letras."
                    )
                    results.isEmpty && stillLoading -> CenterLoading(text = "Buscando…")
                    results.isEmpty -> CenterMessage("Sin resultados para \"${query.trim()}\"")
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (results.live.isNotEmpty()) {
                            item { SectionHeader("EN VIVO  (${results.live.size})") }
                            itemsIndexed(results.live) { index, channel ->
                                ResultRow(
                                    onClick = { vm.playLive(context, results.live, index) },
                                    leading = { ChannelLogo(icon = channel.icon, name = channel.name, size = 44.dp) },
                                    title = channel.name,
                                    subtitle = "Canal en vivo"
                                )
                            }
                        }
                        if (results.movies.isNotEmpty()) {
                            item { SectionHeader("PELÍCULAS  (${results.movies.size})") }
                            itemsIndexed(results.movies) { _, movie ->
                                ResultRow(
                                    onClick = { vm.openMovie(movie, restoreFocus = false) },
                                    leading = {
                                        PosterImage(
                                            url = movie.poster,
                                            title = movie.name,
                                            modifier = Modifier
                                                .width(36.dp)
                                                .height(54.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    },
                                    title = movie.name,
                                    subtitle = ratingText("Película", movie.rating10)
                                )
                            }
                        }
                        if (results.series.isNotEmpty()) {
                            item { SectionHeader("SERIES  (${results.series.size})") }
                            itemsIndexed(results.series) { _, show ->
                                ResultRow(
                                    onClick = { vm.openSeries(show, restoreFocus = false) },
                                    leading = {
                                        PosterImage(
                                            url = show.cover,
                                            title = show.name,
                                            modifier = Modifier
                                                .width(36.dp)
                                                .height(54.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    },
                                    title = show.name,
                                    subtitle = ratingText("Serie", show.rating10)
                                )
                            }
                        }
                    }
                }
            }
        }
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
