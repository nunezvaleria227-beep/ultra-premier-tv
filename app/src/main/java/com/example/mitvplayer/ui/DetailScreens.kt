package com.example.mitvplayer.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mitvplayer.data.Episode
import com.example.mitvplayer.data.Kind
import com.example.mitvplayer.data.Movie
import com.example.mitvplayer.data.MovieInfo
import com.example.mitvplayer.data.ResumeEntry
import com.example.mitvplayer.data.SeriesDetail
import com.example.mitvplayer.data.SeriesItem
import com.example.mitvplayer.data.userMessage
import kotlinx.coroutines.CancellationException

// ------------------------------------------------------------------- comunes

/** Imagen grande de fondo con degradados para que se lea el texto. */
@Composable
private fun DetailBackdrop(url: String?) {
    if (url == null) return
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xF5040A1E), Color(0xD9040A1E), Color(0x55040A1E))
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color(0x00040A1E), 1f to Color(0xF2040A1E)))
        )
    }
}

private fun resumeText(entry: ResumeEntry): String = formatDuration(entry.positionMs / 1000)

/** "✦ PELÍCULA · ACCIÓN" */
@Composable
private fun Kicker(text: String) {
    Text(
        text = "✦  " + text.uppercase(),
        color = AccentLight,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        maxLines = 1
    )
}

@Composable
private fun BigTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = sporty(28f),
        color = TextPrimary,
        lineHeight = 29.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun MetaRow(parts: List<String>, hd: Boolean = true) {
    Row(
        modifier = Modifier.padding(top = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = parts.joinToString("  ·  "),
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (hd) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "HD",
                color = TextSecondary,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .border(0.5.dp, Color(0xFF3A4E8C), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun ProgressLine(fraction: Float, label: String) {
    Row(
        modifier = Modifier.padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(150.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x33FFFFFF))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .background(AccentBrush)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = TextTertiary, fontSize = 8.sp)
    }
}

/** Logo arriba a la izquierda y reloj a la derecha (fichas). */
@Composable
private fun DetailTop(vm: AppViewModel, tab: UltraTab) {
    UltraTopBar(vm = vm, active = tab)
}

// ------------------------------------------------------------------- película

@Composable
fun MovieDetailScreen(vm: AppViewModel, movie: Movie) {
    val context = LocalContext.current
    var info by remember(movie.id) { mutableStateOf<MovieInfo?>(null) }
    var loaded by remember(movie.id) { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(movie.id) {
        info = vm.loadMovieInfo(movie)
        loaded = true
    }
    LaunchedEffect(movie.id) { playFocus.tryRequestFocus() }

    val details = info
    val saved = vm.savedMovie(movie)
    val genre = details?.genre?.split(',', '/')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(2)?.joinToString(", ")
    val meta = listOfNotNull(
        details?.releaseDate?.take(4)?.takeIf { it.length == 4 },
        details?.duration?.takeIf { it.isNotBlank() },
        genre?.takeIf { it.isNotBlank() },
        movie.rating10.takeIf { it >= 0.5f }?.let { "★ " + String.format(java.util.Locale.US, "%.1f", it) }
    )
    val similar = remember(movie.id, vm.movies.visibleItems) {
        vm.movies.visibleItems.filter { it.categoryId == movie.categoryId && it.id != movie.id }
            .sortedByDescending { it.added }
            .take(16)
    }

    AppBackground(dim = 0.5f) {
        DetailBackdrop(details?.backdrop?.takeIf { it.isNotBlank() })
        DetailTop(vm, UltraTab.MOVIES)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 36.dp, end = 36.dp, top = 62.dp)
        ) {
            PosterImage(
                url = movie.poster,
                title = movie.name,
                modifier = Modifier
                    .width(150.dp)
                    .aspectRatio(2f / 3f)
                    .shadow(22.dp, RoundedCornerShape(12.dp), ambientColor = AccentColor, spotColor = AccentColor)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(26.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Kicker(listOfNotNull("Película", genre?.substringBefore(',')).joinToString("  ·  "))
                BigTitle(movie.name)
                if (meta.isNotEmpty()) MetaRow(meta)
                Text(
                    text = details?.plot?.takeIf { it.isNotBlank() }
                        ?: if (loaded) "Sin descripción disponible." else "Cargando información…",
                    color = Color(0xFFD5DEF7),
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth(0.85f)
                )
                val credits = listOfNotNull(
                    details?.cast?.takeIf { it.isNotBlank() }?.let { "Reparto: $it" },
                    details?.director?.takeIf { it.isNotBlank() }?.let { "Dirección: $it" }
                ).joinToString("  ·  ")
                if (credits.isNotEmpty()) {
                    Text(
                        text = credits,
                        color = TextTertiary,
                        fontSize = 8.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth(0.85f)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SlantButton(
                        text = if (saved != null) "Continuar ${resumeText(saved)}" else "Ver ahora",
                        primary = true,
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.focusRequester(playFocus)
                    ) { vm.playMovie(context, movie, info = details) }
                    if (saved != null) {
                        SlantButton(text = "Desde el inicio", icon = Icons.Filled.Refresh) {
                            vm.playMovie(context, movie, fromStart = true, info = details)
                        }
                    }
                    val trailer = details?.trailer
                    if (!trailer.isNullOrBlank()) {
                        SlantButton(text = "Tráiler") { vm.openTrailer(context, trailer) }
                    }
                    val favorite = vm.isFavorite(Kind.MOVIES, movie.id)
                    SlantButton(
                        text = if (favorite) "En mi lista" else "Mi lista",
                        icon = if (favorite) Icons.Filled.Check else Icons.Filled.Add
                    ) { vm.toggleFavorite(Kind.MOVIES, movie.id) }
                }
                if (saved != null && saved.fraction > 0f) {
                    ProgressLine(saved.fraction, "Te quedaste en ${resumeText(saved)}")
                }
                if (similar.isNotEmpty()) {
                    Text(
                        text = "Más como esta",
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp, end = 8.dp)
                    ) {
                        items(similar, key = { it.id }) { other ->
                            FocusCard(
                                onClick = { vm.openMovie(other, restoreFocus = false) },
                                modifier = Modifier.width(66.dp),
                                shape = RoundedCornerShape(8.dp),
                                scaleOnFocus = 1.08f
                            ) {
                                PosterImage(
                                    url = other.poster,
                                    title = other.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------- serie

@Composable
fun SeriesDetailScreen(vm: AppViewModel, show: SeriesItem) {
    val context = LocalContext.current
    var detail by remember(show.id) { mutableStateOf<SeriesDetail?>(null) }
    var error by remember(show.id) { mutableStateOf<String?>(null) }
    var attempt by remember(show.id) { mutableStateOf(0) }
    var selectedSeason by remember(show.id) { mutableStateOf<Int?>(null) }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(show.id, attempt) {
        error = null
        try {
            val loaded = vm.loadSeriesDetail(show)
            detail = loaded
            selectedSeason = loaded.seasons.keys.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage()
        }
    }
    LaunchedEffect(detail != null) {
        if (detail != null) playFocus.tryRequestFocus()
    }

    // El último episodio que se dejó a medias.
    val resume: Pair<Episode, ResumeEntry>? = detail?.seasons?.values?.flatten()
        ?.mapNotNull { episode -> vm.savedEpisode(episode.id)?.let { episode to it } }
        ?.maxByOrNull { it.second.updatedAt }
    val seasonsCount = detail?.seasons?.keys?.count { it > 0 } ?: 0
    val genre = detail?.genre?.split(',', '/')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(2)?.joinToString(", ")
    val meta = listOfNotNull(
        detail?.releaseDate?.take(4)?.takeIf { it.length == 4 },
        genre?.takeIf { it.isNotBlank() },
        show.rating10.takeIf { it >= 0.5f }?.let { "★ " + String.format(java.util.Locale.US, "%.1f", it) }
    )

    AppBackground(dim = 0.5f) {
        DetailBackdrop((detail?.backdrop ?: detail?.cover ?: show.cover)?.takeIf { it.isNotBlank() })
        DetailTop(vm, UltraTab.SERIES)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 36.dp, top = 62.dp)
        ) {
            Kicker(
                listOfNotNull(
                    "Serie",
                    if (seasonsCount > 0) (if (seasonsCount == 1) "1 temporada" else "$seasonsCount temporadas") else null
                ).joinToString("  ·  ")
            )
            BigTitle(show.name)
            if (meta.isNotEmpty()) MetaRow(meta, hd = false)
            val plot = detail?.plot ?: show.plot
            if (!plot.isNullOrBlank()) {
                Text(
                    text = plot,
                    color = Color(0xFFD5DEF7),
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 8.dp, end = 36.dp)
                        .fillMaxWidth(0.62f)
                )
            }
            Spacer(Modifier.height(12.dp))

            val current = detail
            val message = error
            when {
                message != null -> Box(Modifier.fillMaxSize()) {
                    ErrorWithRetry(message = message, onRetry = { attempt += 1 })
                }
                current == null -> Box(Modifier.fillMaxSize()) { CenterLoading() }
                current.seasons.isEmpty() -> Box(Modifier.fillMaxSize()) {
                    CenterMessage("Esta serie no tiene episodios disponibles")
                }
                else -> {
                    val seasonNumbers = current.seasons.keys.toList()
                    val season = selectedSeason ?: seasonNumbers.first()
                    val episodes = current.seasons[season].orEmpty()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (resume != null) {
                            val (episode, entry) = resume
                            SlantButton(
                                text = "Seguir con T${episode.season} E${episode.number}",
                                primary = true,
                                icon = Icons.Filled.PlayArrow,
                                modifier = Modifier.focusRequester(playFocus)
                            ) { vm.playEpisode(context, show, current, episode) }
                        } else {
                            val first = current.seasons[seasonNumbers.firstOrNull { it > 0 } ?: seasonNumbers.first()].orEmpty().firstOrNull()
                            if (first != null) {
                                SlantButton(
                                    text = "Ver T${first.season} E${first.number}",
                                    primary = true,
                                    icon = Icons.Filled.PlayArrow,
                                    modifier = Modifier.focusRequester(playFocus)
                                ) { vm.playEpisode(context, show, current, first) }
                            }
                        }
                        val favorite = vm.isFavorite(Kind.SERIES, show.id)
                        SlantButton(
                            text = if (favorite) "En mi lista" else "Mi lista",
                            icon = if (favorite) Icons.Filled.Check else Icons.Filled.Add
                        ) { vm.toggleFavorite(Kind.SERIES, show.id) }
                        val trailer = current.trailer
                        if (!trailer.isNullOrBlank()) {
                            SlantButton(text = "Tráiler") { vm.openTrailer(context, trailer) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(end = 36.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        items(seasonNumbers) { number ->
                            SlantChip(
                                text = if (number == 0) "Especiales" else "Temporada $number",
                                selected = number == season,
                                onClick = { selectedSeason = number }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 36.dp, top = 6.dp, bottom = 12.dp)
                    ) {
                        items(episodes, key = { it.id }) { episode ->
                            EpisodeCard(
                                episode = episode,
                                saved = vm.savedEpisode(episode.id),
                                onClick = { vm.playEpisode(context, show, current, episode) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: Episode, saved: ResumeEntry?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val minutes = episode.durationSecs?.takeIf { it > 0 }?.let { it / 60 }
    val status = when {
        saved != null && saved.durationMs > 0 && saved.fraction >= 0.92f -> "Visto"
        saved != null && saved.durationMs > 0 ->
            "Te quedan ${((saved.durationMs - saved.positionMs) / 60_000).coerceAtLeast(1)} min"
        saved != null -> "Vas por ${resumeText(saved)}"
        else -> null
    }
    Column(Modifier.width(158.dp)) {
        FocusCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            shape = RoundedCornerShape(10.dp),
            scaleOnFocus = 1.06f
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                PosterImage(url = episode.image, title = "E", modifier = Modifier.fillMaxSize())
                Text(
                    text = "E${episode.number}",
                    color = Color.White,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(Color(0xB3040A1E), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
                if (saved != null && status != "Visto") {
                    Text(
                        text = "VIENDO",
                        color = Color.White,
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .background(AccentColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                if (saved != null && saved.durationMs > 0) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color(0x55FFFFFF))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(saved.fraction.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .background(AccentBrush)
                        )
                    }
                }
            }
        }
        Text(
            text = if (episode.number > 0) "Episodio ${episode.number}: ${episode.title}" else episode.title,
            color = if (focused) Color.White else TextPrimary,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        val sub = listOfNotNull(minutes?.let { "$it min" }, status ?: "Nuevo").joinToString("  ·  ")
        Text(sub, color = TextTertiary, fontSize = 7.5.sp, maxLines = 1)
    }
}
