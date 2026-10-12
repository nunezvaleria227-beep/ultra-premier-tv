package com.example.mitvplayer.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mitvplayer.data.EpgEntry
import com.example.mitvplayer.data.LiveChannel
import com.example.mitvplayer.data.userMessage
import kotlinx.coroutines.CancellationException

// ----------------------------------------------------------------------- EPG

/** "Categorías de EPG": cuadrícula de dos columnas con las categorías de En vivo y su cantidad. */
@Composable
fun EpgCategoriesScreen(vm: AppViewModel) {
    val state = vm.live
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { state.ensureLoaded() }
    LaunchedEffect(state.catalog) {
        if (state.catalog != null) {
            withFrameNanos { }
            withFrameNanos { }
            first.tryRequestFocus()
        }
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = "Categorías de EPG",
                centerTitle = true,
                onBack = { vm.back() },
                onSearch = { vm.open(Screen.Search(SearchScope.LIVE)) }
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SectionStatus(state, "No hay categorías") {
                    val rows = state.rows.filter { it.id != ROW_RECENT }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rows) { row ->
                            CategoryTile(
                                name = row.name,
                                count = row.count,
                                onClick = { vm.open(Screen.EpgChannels(row.id, row.name)) },
                                modifier = if (row.id == rows.first().id) Modifier.focusRequester(first) else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(name: String, count: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(4.dp),
        scaleOnFocus = 1.02f
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(Glyph.TV, Modifier.size(34.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                text = name,
                color = TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (count != null) {
                Text(text = count.toString(), color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
            }
            GlyphIcon(Glyph.CHEVRON, Modifier.size(26.dp))
        }
    }
}

/** Canales de una categoría con lo que dan ahora y lo que sigue. */
@Composable
fun EpgChannelsScreen(vm: AppViewModel, categoryId: String, title: String) {
    val context = LocalContext.current
    val state = vm.live
    val channels = remember(categoryId, state.catalog) { state.itemsFor(categoryId) }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = title, centerTitle = true, onBack = { vm.back() })
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (channels.isEmpty()) {
                    CenterMessage("No hay canales en esta categoría")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(channels) { index, channel ->
                            EpgRow(vm, channel) { vm.playLive(context, channels, index) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgRow(vm: AppViewModel, channel: LiveChannel, onClick: () -> Unit) {
    val epg by produceState<List<EpgEntry>?>(null, channel.id) { value = vm.epgFor(channel.id) }
    val nowSec = System.currentTimeMillis() / 1000
    val upcoming = epg.orEmpty().filter { it.end > nowSec }.take(3)

    FocusCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        scaleOnFocus = 1.01f
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelLogo(icon = channel.icon, name = channel.name, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (upcoming.isEmpty()) {
                    Text(
                        text = if (epg == null) "" else "No hemos encontrado ningún programa",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    upcoming.forEachIndexed { i, entry ->
                        val live = nowSec >= entry.start
                        Text(
                            text = "${formatHm(entry.start)}  ${entry.title}",
                            color = if (live) TextPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (i == 0 && live) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Catch up

/** Canales de En vivo que el servidor guarda (archivo). */
private fun archiveChannels(vm: AppViewModel): List<LiveChannel> =
    vm.live.catalog?.items?.filter { it.archiveDays > 0 }.orEmpty()

@Composable
fun CatchUpScreen(vm: AppViewModel) {
    val state = vm.live
    val catalog = state.catalog
    val archive = remember(catalog) { archiveChannels(vm) }
    val rows = remember(catalog, archive) {
        val out = ArrayList<CategoryRow>()
        out += CategoryRow(ROW_ALL, "TODO", archive.size)
        val counts = archive.groupingBy { it.categoryId }.eachCount()
        catalog?.categories?.forEach { category ->
            val count = counts[category.id]
            if (count != null) out += CategoryRow(category.id, category.name.trim(), count)
        }
        out
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { state.ensureLoaded() }
    LaunchedEffect(catalog) {
        if (catalog != null) {
            withFrameNanos { }
            withFrameNanos { }
            first.tryRequestFocus()
        }
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = "CATCH UP",
                centerTitle = true,
                onBack = { vm.back() },
                onSearch = { vm.open(Screen.Search(SearchScope.LIVE)) }
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    catalog == null && state.error != null -> ErrorWithRetry(state.error ?: "", onRetry = { state.retry() })
                    catalog == null -> CenterLoading(text = "Cargando canales…")
                    archive.isEmpty() -> CenterMessage(
                        "Tu servidor no tiene canales con Catch up (programas ya emitidos).\n" +
                            "Es un servicio que debe incluir tu proveedor."
                    )
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(rows) { index, row ->
                            CategoryTile(
                                name = if (row.id == ROW_ALL) "TODO" else row.name,
                                count = row.count,
                                onClick = { vm.open(Screen.CatchUpChannels(row.id, row.name)) },
                                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CatchUpChannelsScreen(vm: AppViewModel, categoryId: String, title: String) {
    val catalog = vm.live.catalog
    val channels = remember(catalog, categoryId) {
        archiveChannels(vm).filter { categoryId == ROW_ALL || it.categoryId == categoryId }
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "CATCH UP · $title", centerTitle = true, onBack = { vm.back() })
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (channels.isEmpty()) {
                    CenterMessage("No hay canales con Catch up en esta categoría")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(channels) { _, channel ->
                            FocusCard(
                                onClick = { vm.open(Screen.CatchUpPrograms(channel)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                scaleOnFocus = 1.01f
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ChannelLogo(icon = channel.icon, name = channel.name, size = 48.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            channel.name,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "Guarda ${channel.archiveDays} día(s)",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    GlyphIcon(Glyph.CHEVRON, Modifier.size(26.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CatchUpProgramsScreen(vm: AppViewModel, channel: LiveChannel) {
    val context = LocalContext.current
    var entries by remember(channel.id) { mutableStateOf<List<EpgEntry>?>(null) }
    var error by remember(channel.id) { mutableStateOf<String?>(null) }
    var attempt by remember(channel.id) { mutableStateOf(0) }
    var selectedDay by remember(channel.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(channel.id, attempt) {
        error = null
        try {
            entries = vm.epgTable(channel.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage()
        }
    }

    val loaded = entries
    val nowSec = System.currentTimeMillis() / 1000
    val windowStart = nowSec - channel.archiveDays * 86_400L
    val all = loaded.orEmpty()
    // Si el servidor marca cuáles programas tiene grabados, se usan esos; si no, los de los últimos días.
    val flagged = all.any { it.hasArchive }
    val playable = all
        .filter { it.start < nowSec && (if (flagged) it.hasArchive else it.start >= windowStart) }
        .sortedBy { it.start }
    val days = playable.map { dayKey(it.start) }.distinct().sortedDescending()
    val day = selectedDay ?: days.firstOrNull()
    val dayEntries = playable.filter { dayKey(it.start) == day }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = channel.name, centerTitle = true, onBack = { vm.back() })
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val message = error
                when {
                    message != null -> ErrorWithRetry(message, onRetry = { attempt += 1 })
                    loaded == null -> CenterLoading(text = "Cargando programas…")
                    playable.isEmpty() -> CenterMessage("No hay programas guardados para este canal.")
                    else -> Column(Modifier.fillMaxSize()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            itemsIndexed(days) { _, key ->
                                val sample = playable.first { dayKey(it.start) == key }
                                TvButton(
                                    text = formatDayShort(sample.start * 1000),
                                    onClick = { selectedDay = key },
                                    selected = key == day,
                                    compact = true
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(dayEntries) { _, entry ->
                                FocusCard(
                                    onClick = { vm.playArchive(context, channel, entry) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp),
                                    scaleOnFocus = 1.01f
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${formatHm(entry.start)} – ${formatHm(entry.end)}",
                                            color = AccentColor,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.width(110.dp)
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                entry.title.ifBlank { "Sin título" },
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (entry.description.isNotBlank()) {
                                                Text(
                                                    entry.description,
                                                    color = TextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 2,
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
        }
    }
}
