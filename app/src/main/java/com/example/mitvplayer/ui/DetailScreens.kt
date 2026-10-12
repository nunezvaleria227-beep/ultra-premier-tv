package com.example.mitvplayer.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mitvplayer.data.Episode
import com.example.mitvplayer.data.Kind
import com.example.mitvplayer.data.Movie
import com.example.mitvplayer.data.MovieInfo
import com.example.mitvplayer.data.SeriesDetail
import com.example.mitvplayer.data.SeriesItem
import com.example.mitvplayer.data.userMessage
import kotlinx.coroutines.CancellationException

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
    val meta = listOfNotNull(
        details?.releaseDate?.take(4),
        details?.duration,
        details?.genre,
        movie.rating?.takeIf { (it.toFloatOrNull() ?: 0f) > 0f }?.let { "★ $it" }
    ).joinToString("   •   ")

    AppBackground {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        PosterImage(
            url = movie.poster,
            title = movie.name,
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(40.dp))
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
            Spacer(Modifier.height(16.dp))
            Text(
                text = details?.plot
                    ?: if (loaded) "Sin descripción disponible." else "Cargando información…",
                color = TextSecondary,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            details?.director?.let {
                Spacer(Modifier.height(12.dp))
                Text(text = "Director: $it", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            details?.cast?.let {
                Spacer(Modifier.height(4.dp))
                Text(text = "Reparto: $it", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = "▶  Reproducir",
                    onClick = { vm.playMovie(context, movie) },
                    modifier = Modifier.focusRequester(playFocus)
                )
                val favorite = vm.isFavorite(Kind.MOVIES, movie.id)
                TvButton(
                    text = if (favorite) "★  En favoritos" else "☆  Añadir a favoritos",
                    onClick = { vm.toggleFavorite(Kind.MOVIES, movie.id) },
                    selected = favorite
                )
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

    AppBackground {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp)
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
            Spacer(Modifier.height(8.dp))
            val favorite = vm.isFavorite(Kind.SERIES, show.id)
            TvButton(
                text = if (favorite) "★  En favoritos" else "☆  Añadir a favoritos",
                onClick = { vm.toggleFavorite(Kind.SERIES, show.id) },
                selected = favorite,
                compact = true
            )
            val plot = detail?.plot ?: show.plot
            if (plot != null) {
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(Modifier.width(32.dp))

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
    onSelectSeason: (Int) -> Unit
) {
    val seasonNumbers = detail.seasons.keys.toList()
    val season = selectedSeason ?: seasonNumbers.first()
    val episodes = detail.seasons[season].orEmpty()

    Column(Modifier.fillMaxSize()) {
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
                    onClick = { vm.playEpisode(context, show, detail, episode) }
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
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
                if (duration != null) {
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
