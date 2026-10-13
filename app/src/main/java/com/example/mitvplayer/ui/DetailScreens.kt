package com.example.mitvplayer.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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

// ------------------------------------------------------------------- película

/** Imagen grande de fondo con degradado para que se lea el texto. */
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
                        listOf(Color(0xF2050818), Color(0xC0050818), Color(0x70050818))
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0xCC050818))))
        )
    }
}

private fun resumeText(entry: ResumeEntry): String = formatDuration(entry.positionMs / 1000)

@OptIn(ExperimentalLayoutApi::class)
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
    val meta = listOfNotNull(
        details?.releaseDate?.take(4),
        details?.duration,
        details?.genre,
        movie.rating?.takeIf { (it.toFloatOrNull() ?: 0f) > 0f }?.let { "★ $it" }
    ).joinToString("   •   ")

    AppBackground {
        DetailBackdrop(details?.backdrop)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 24.dp)
        ) {
            PosterImage(
                url = movie.poster,
                title = movie.name,
                modifier = Modifier
                    .width(200.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(32.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = movie.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = meta, color = AccentColor)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = details?.plot
                        ?: if (loaded) "Sin descripción disponible." else "Cargando información…",
                    color = TextPrimary.copy(alpha = 0.88f),
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis
                )
                details?.director?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(text = "Director: $it", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                details?.cast?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(text = "Reparto: $it", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(22.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (saved != null) {
                        TvButton(
                            text = "▶  Continuar (${resumeText(saved)})",
                            onClick = { vm.playMovie(context, movie) },
                            modifier = Modifier.focusRequester(playFocus)
                        )
                        TvButton(
                            text = "⟲  Desde el inicio",
                            onClick = { vm.playMovie(context, movie, fromStart = true) }
                        )
                    } else {
                        TvButton(
                            text = "▶  Reproducir",
                            onClick = { vm.playMovie(context, movie) },
                            modifier = Modifier.focusRequester(playFocus)
                        )
                    }
                    val trailer = details?.trailer
                    if (trailer != null) {
                        TvButton(text = "🎬  Tráiler", onClick = { vm.openTrailer(context, trailer) })
                    }
                    val favorite = vm.isFavorite(Kind.MOVIES, movie.id)
                    TvButton(
                        text = if (favorite) "★  En favoritos" else "☆  Añadir a favoritos",
                        onClick = { vm.toggleFavorite(Kind.MOVIES, movie.id) },
                        selected = favorite
                    )
                }
                if (saved != null && saved.fraction > 0f) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x33FFFFFF))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(saved.fraction)
                                .fillMaxHeight()
                                .background(AccentColor)
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------- serie

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesDetailScreen(vm: AppViewModel, show: SeriesItem) {
    val context = LocalContext.current
    var detail by remember(show.id) { mutableStateOf<SeriesDetail?>(null) }
    var error by remember(show.id) { mutableStateOf<String?>(null) }
    var attempt by remember(show.id) { mutableStateOf(0) }
    var selectedSeason by remember(show.id) { mutableStateOf<Int?>(null) }

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

    // El último episodio que se dejó a medias.
    val resume: Pair<Episode, ResumeEntry>? = detail?.seasons?.values?.flatten()
        ?.mapNotNull { episode -> vm.savedEpisode(episode.id)?.let { episode to it } }
        ?.maxByOrNull { it.second.updatedAt }

    val meta = listOfNotNull(
        detail?.releaseDate?.take(4),
        detail?.genre,
        show.rating?.takeIf { (it.toFloatOrNull() ?: 0f) > 0f }?.let { "★ $it" }
    ).joinToString("  •  ")

    AppBackground {
        DetailBackdrop(detail?.backdrop)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                PosterImage(
                    url = detail?.cover ?: show.cover,
                    title = show.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = show.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta.isNotEmpty()) {
                    Text(text = meta, color = AccentColor, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val favorite = vm.isFavorite(Kind.SERIES, show.id)
                    TvButton(
                        text = if (favorite) "★  En favoritos" else "☆  Añadir a favoritos",
                        onClick = { vm.toggleFavorite(Kind.SERIES, show.id) },
                        selected = favorite,
                        compact = true
                    )
                    val trailer = detail?.trailer
                    if (trailer != null) {
                        TvButton(text = "🎬  Tráiler", onClick = { vm.openTrailer(context, trailer) }, compact = true)
                    }
                }
                val plot = detail?.plot ?: show.plot
                if (plot != null) {
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary.copy(alpha = 0.85f),
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                detail?.director?.let {
                    Text(
                        text = "Director: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                detail?.cast?.let {
                    Text(
                        text = "Reparto: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(Modifier.width(28.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val current = detail
                val message = error
                when {
                    message != null -> ErrorWithRetry(message = message, onRetry = { attempt += 1 })
                    current == null -> CenterLoading()
                    current.seasons.isEmpty() -> CenterMessage("Esta serie no tiene episodios disponibles")
                    else -> EpisodeBrowser(
                        context = context,
                        vm = vm,
                        show = show,
                        detail = current,
                        selectedSeason = selectedSeason,
                        resume = resume,
                        onSelectSeason = { selectedSeason = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeBrowser(
    context: Context,
    vm: AppViewModel,
    show: SeriesItem,
    detail: SeriesDetail,
    selectedSeason: Int?,
    resume: Pair<Episode, ResumeEntry>?,
    onSelectSeason: (Int) -> Unit
) {
    val seasonNumbers = detail.seasons.keys.toList()
    val season = selectedSeason ?: seasonNumbers.first()
    val episodes = detail.seasons[season].orEmpty()

    Column(Modifier.fillMaxSize()) {
        if (resume != null) {
            val (episode, entry) = resume
            val label = "▶  Continuar T${episode.season} · E${episode.number} (${resumeText(entry)})"
            TvButton(
                text = label,
                onClick = { vm.playEpisode(context, show, detail, episode) },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(seasonNumbers) { number ->
                TvButton(
                    text = if (number == 0) "Especiales" else "Temporada $number",
                    onClick = { onSelectSeason(number) },
                    selected = number == season
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
        ) {
            items(episodes) { episode ->
                EpisodeRow(
                    episode = episode,
                    saved = vm.savedEpisode(episode.id),
                    onClick = { vm.playEpisode(context, show, detail, episode) }
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, saved: ResumeEntry?, onClick: () -> Unit) {
    val title = if (episode.number > 0) "${episode.number}. ${episode.title}" else episode.title
    val duration = episode.durationSecs?.takeIf { it > 0 }?.let { "${it / 60} min" }

    FocusCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        scaleOnFocus = 1.02f
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterImage(
                url = episode.image,
                title = "E",
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (saved != null) {
                    Text(
                        text = "● Vas por ${resumeText(saved)}",
                        color = AccentColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (duration != null) {
                    Text(text = duration, color = AccentColor, style = MaterialTheme.typography.bodySmall)
                }
                val plot = episode.plot
                if (plot != null) {
                    Text(
                        text = plot,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
