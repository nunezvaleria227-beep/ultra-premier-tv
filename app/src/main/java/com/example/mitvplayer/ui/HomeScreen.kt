package com.example.mitvplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mitvplayer.data.Kind
import com.example.mitvplayer.data.LiveChannel
import com.example.mitvplayer.data.Movie
import com.example.mitvplayer.data.MovieInfo
import com.example.mitvplayer.data.SeriesItem
import kotlinx.coroutines.launch

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Lo que se muestra en grande: una película o una serie. */
private class HeroItem(val movie: Movie? = null, val series: SeriesItem? = null) {
    val key: String get() = movie?.let { "m" + it.id } ?: ("s" + (series?.id ?: ""))
}

private val TileShape = RoundedCornerShape(11.dp)

/**
 * Inicio de Ultra Premier TV ("Mosaico"): lo destacado en grande, al lado el canal en vivo y
 * "Continuar viendo", y abajo filas de estrenos, series nuevas, mejor calificadas y Mi lista.
 * Al recorrer las filas, lo destacado cambia a lo que se está viendo.
 */
@Composable
fun HomeScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val playFR = remember { FocusRequester() }
    var playFocused by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.live.ensureLoaded()
        vm.movies.ensureLoaded()
        vm.series.ensureLoaded()
    }

    val movies = vm.movies.visibleItems
    val seriesItems = vm.series.visibleItems
    val resume = vm.resumeEntries
    val favMovies = vm.movies.favoriteList
    val favSeries = vm.series.favoriteList
    // Ya vienen calculadas al cargar cada lista (sin ordenar decenas de miles de títulos en pantalla).
    val newMovies = vm.movies.newestVisible
    val topMovies = vm.movies.topRatedVisible
    val newSeries = vm.series.newestVisible

    val featured = remember(newMovies, newSeries) {
        newMovies.firstOrNull()?.let { HeroItem(movie = it) } ?: newSeries.firstOrNull()?.let { HeroItem(series = it) }
    }
    var picked by remember { mutableStateOf<HeroItem?>(null) }
    val hero = picked ?: featured

    // Ficha de la película en grande (imagen de fondo, año, género, duración y sinopsis).
    val infos = remember { mutableStateMapOf<String, MovieInfo?>() }
    val heroMovie = hero?.movie
    LaunchedEffect(heroMovie?.id) {
        val movie = heroMovie ?: return@LaunchedEffect
        if (infos.containsKey(movie.id)) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        infos[movie.id] = try {
            vm.loadMovieInfo(movie)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }
    val heroInfo = heroMovie?.let { infos[it.id] }

    // Canal en vivo: el último que se vio o, si no hay, el primero de la lista.
    val liveRecent = vm.live.recentList
    val liveAll = vm.live.visibleItems
    val liveList = if (liveRecent.isNotEmpty()) liveRecent else liveAll.take(300)
    val liveChannel = liveList.firstOrNull()

    // Al entrar, el foco va a "Ver ahora".
    LaunchedEffect(featured != null) {
        withFrameNanos { }
        withFrameNanos { }
        playFR.tryRequestFocus()
    }

    BackHandler {
        if (playFocused) {
            confirmExit = true
        } else {
            scope.launch { scroll.animateScrollTo(0) }
            playFR.tryRequestFocus()
        }
    }

    AppBackground(dim = 0.55f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(BackgroundGlow.copy(alpha = 0.55f), Color.Transparent), radius = 1400f))
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
        ) {
            Spacer(Modifier.height(TopBarHeight + 14.dp))
            Row(
                Modifier
                    .padding(horizontal = 36.dp)
                    .height(281.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroTile(
                    vm = vm,
                    hero = hero,
                    info = heroInfo,
                    isNew = hero?.movie?.id != null && newMovies.firstOrNull()?.id == hero?.movie?.id,
                    playFR = playFR,
                    onPlayFocus = { playFocused = it },
                    modifier = Modifier
                        .weight(1.53f)
                        .fillMaxHeight()
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiveTile(
                        vm = vm,
                        channel = liveChannel,
                        loading = vm.live.loading,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val index = liveList.indexOfFirst { it.id == liveChannel?.id }.coerceAtLeast(0)
                        if (liveChannel != null) vm.playLive(context, liveList, index)
                    }
                    ContinueTile(
                        vm = vm,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        fallback = newSeries.firstOrNull()
                    )
                }
            }

            if (newMovies.isNotEmpty()) {
                PosterRow("Estrenos", "lo más nuevo", newMovies.map { HeroItem(movie = it) }, vm) { picked = it }
            }
            if (newSeries.isNotEmpty()) {
                PosterRow("Series nuevas", null, newSeries.map { HeroItem(series = it) }, vm) { picked = it }
            }
            if (topMovies.isNotEmpty()) {
                PosterRow("Mejor calificadas", null, topMovies.map { HeroItem(movie = it) }, vm) { picked = it }
            }
            val mine = favMovies.map { HeroItem(movie = it) } + favSeries.map { HeroItem(series = it) }
            if (mine.isNotEmpty()) {
                PosterRow("Mi lista", null, mine, vm) { picked = it }
            }
            if (movies.isEmpty() && seriesItems.isEmpty()) {
                Text(
                    text = if (vm.movies.loading || vm.series.loading) "Cargando tu contenido…" else "Aún no hay películas ni series.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 36.dp, top = 20.dp)
                )
            }
            Spacer(Modifier.height(30.dp))
        }
        UltraTopBar(vm = vm, active = UltraTab.HOME, onActive = {
            scope.launch { scroll.animateScrollTo(0) }
            playFR.tryRequestFocus()
        })
    }

    UpdateDialogs(vm)

    if (confirmExit) {
        OptionsDialog(
            title = "¿Qué quieres hacer?",
            options = listOf(
                Opt("Seguir viendo") { },
                Opt("Cerrar sesión") { vm.logout() },
                Opt("Salir de la aplicación") { context.findActivity()?.finish() }
            ),
            onDismiss = { confirmExit = false }
        )
    }
}

// ------------------------------------------------------------------ lo destacado

@Composable
private fun HeroTile(
    vm: AppViewModel,
    hero: HeroItem?,
    info: MovieInfo?,
    isNew: Boolean,
    playFR: FocusRequester,
    onPlayFocus: (Boolean) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    var hasFocus by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { hasFocus = it.hasFocus }
            .shadow(if (hasFocus) 22.dp else 10.dp, TileShape, ambientColor = AccentColor, spotColor = AccentColor)
            .clip(TileShape)
            .background(PanelColor)
            .border(if (hasFocus) 2.dp else 0.5.dp, if (hasFocus) Color.White else GlassBorder, TileShape)
    ) {
        val image = info?.backdrop?.takeIf { it.isNotBlank() }
            ?: hero?.movie?.poster?.takeIf { it.isNotBlank() }
            ?: hero?.series?.cover?.takeIf { it.isNotBlank() }
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(0.3f to BackgroundColor.copy(alpha = 0.94f), 0.78f to BackgroundColor.copy(alpha = 0f)))
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to BackgroundColor.copy(alpha = 0f), 1f to BackgroundColor.copy(alpha = 0.9f)))
        )
        if (hero == null) {
            Text(
                "Cargando lo nuevo…",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
            )
            // Aun sin contenido, el botón existe para que el foco tenga dónde estar.
            SlantButton(
                text = "Actualizar",
                primary = true,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
                    .focusRequester(playFR)
                    .onFocusChanged { onPlayFocus(it.isFocused) }
            ) {
                vm.movies.refresh()
                vm.series.refresh()
            }
        } else Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp, end = 24.dp)
                .width(330.dp)
        ) {
            val badge = when {
                hero.series != null -> "Serie"
                isNew -> "★ Estreno"
                else -> "Película"
            }
            Box(
                Modifier
                    .clip(SlantShape(4.dp))
                    .background(AccentBrush)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(badge.uppercase(), style = sporty(9f), color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = (hero.movie?.name ?: hero.series?.name ?: "").uppercase(),
                style = sporty(32f),
                color = Color.White,
                lineHeight = 31.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = heroMeta(hero, info)
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            val plot = info?.plot?.takeIf { it.isNotBlank() } ?: hero.series?.plot?.takeIf { it.isNotBlank() }
            if (plot != null) {
                Text(
                    plot,
                    color = Color(0xFFD5DEF7),
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlantButton(
                    text = if (hero.series != null) "Ver serie" else "Ver ahora",
                    primary = true,
                    icon = Icons.Filled.PlayArrow,
                    modifier = Modifier
                        .focusRequester(playFR)
                        .onFocusChanged { onPlayFocus(it.isFocused) }
                ) {
                    val movie = hero.movie
                    val series = hero.series
                    if (movie != null) vm.playMovie(context, movie, info = info) else if (series != null) vm.openSeries(series)
                }
                SlantButton(text = "Más info") {
                    val movie = hero.movie
                    val series = hero.series
                    if (movie != null) vm.openMovie(movie) else if (series != null) vm.openSeries(series)
                }
                val favKind = if (hero.series != null) Kind.SERIES else Kind.MOVIES
                val favId = hero.movie?.id ?: hero.series?.id
                if (favId != null) {
                    val fav = vm.isFavorite(favKind, favId)
                    SlantButton(
                        text = if (fav) "En mi lista" else "Mi lista",
                        icon = if (fav) Icons.Filled.Check else Icons.Filled.Add
                    ) { vm.toggleFavorite(favKind, favId) }
                }
            }
        }
    }
}

private fun heroMeta(hero: HeroItem, info: MovieInfo?): String {
    val parts = mutableListOf<String>()
    val movie = hero.movie
    if (movie != null) {
        info?.releaseDate?.take(4)?.takeIf { it.length == 4 }?.let { parts += it }
        info?.genre?.takeIf { it.isNotBlank() }?.let { g ->
            parts += g.split(',', '/').map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", ")
        }
        info?.duration?.takeIf { it.isNotBlank() }?.let { parts += it }
        if (movie.rating10 >= 0.5f) parts += "★ " + String.format(java.util.Locale.US, "%.1f", movie.rating10)
        parts += "HD"
    } else {
        val series = hero.series ?: return ""
        parts += "Serie"
        if (series.rating10 >= 0.5f) parts += "★ " + String.format(java.util.Locale.US, "%.1f", series.rating10)
    }
    return parts.joinToString("  ·  ")
}

// ------------------------------------------------------------------ canal en vivo

@Composable
private fun LiveTile(
    vm: AppViewModel,
    channel: LiveChannel?,
    loading: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val epg by produceState<List<com.example.mitvplayer.data.EpgEntry>?>(null, channel?.id) {
        value = channel?.let {
            try {
                vm.epgFor(it.id)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
    }
    val nowSec = System.currentTimeMillis() / 1000
    val current = epg?.firstOrNull { nowSec >= it.start && nowSec < it.end }
    val progress = current?.let { ((nowSec - it.start).toFloat() / (it.end - it.start).coerceAtLeast(1)).coerceIn(0f, 1f) }
    FocusCard(
        onClick = onClick,
        modifier = modifier,
        shape = TileShape,
        scaleOnFocus = 1.03f,
        background = Color.Transparent,
        focusedBackground = Color.Transparent
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF0B3A5C), Color(0xFF0B1640), BackgroundColor)))
        )
        if (channel != null && !channel.icon.isNullOrBlank()) {
            AsyncImage(
                model = channel.icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .size(110.dp)
                    .graphicsLayer { alpha = 0.18f }
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to BackgroundColor.copy(alpha = 0.92f)))
        )
        LiveBadge(modifier = Modifier.padding(12.dp))
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (channel != null) ChannelLogo(icon = channel.icon, name = channel.name, size = 34.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = (channel?.name ?: if (loading) "Cargando canales…" else "TV en vivo").uppercase(),
                    style = sporty(14f),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = current?.title?.takeIf { it.isNotBlank() } ?: if (channel != null) "En vivo ahora" else "Elige un canal en En vivo",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (progress != null) ProgressBar(progress, Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ continuar viendo

@Composable
private fun ContinueTile(vm: AppViewModel, modifier: Modifier, fallback: SeriesItem?) {
    val context = LocalContext.current
    val entry = vm.resumeEntries.firstOrNull()
    val image: String?
    val kicker: String
    val title: String
    val subtitle: String
    val fraction: Float?
    val action: () -> Unit
    when {
        entry != null -> {
            image = entry.image
            kicker = "CONTINUAR VIENDO"
            title = entry.title
            subtitle = entry.subtitle?.takeIf { it.isNotBlank() } ?: ("Te quedaste en " + formatDuration(entry.positionMs / 1000))
            fraction = if (entry.durationMs > 0) entry.fraction else null
            action = { vm.playResume(context, entry) }
        }
        fallback != null -> {
            image = fallback.cover
            kicker = "SERIE NUEVA"
            title = fallback.name
            subtitle = "Temporadas y episodios"
            fraction = null
            action = { vm.openSeries(fallback) }
        }
        else -> {
            image = null
            kicker = "CONTINUAR VIENDO"
            title = "Aquí aparece lo que dejes a medias"
            subtitle = "Películas y episodios"
            fraction = null
            action = { vm.open(Screen.Continue) }
        }
    }
    FocusCard(
        onClick = action,
        modifier = modifier,
        shape = TileShape,
        scaleOnFocus = 1.03f,
        background = Color(0x14FFFFFF),
        focusedBackground = Color(0x22FFFFFF)
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(140.dp)
                    .fillMaxHeight()
                    .background(PanelColor)
            ) {
                if (!image.isNullOrBlank()) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(0.55f to Color.Transparent, 1f to BackgroundColor.copy(alpha = 0.9f)))
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(kicker, color = AccentLight, fontSize = 7.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                Text(
                    title.uppercase(),
                    style = sporty(14f),
                    color = Color.White,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(subtitle, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                if (fraction != null) ProgressBar(fraction, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ filas

@Composable
private fun PosterRow(title: String, subtitle: String?, list: List<HeroItem>, vm: AppViewModel, onFocus: (HeroItem) -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        SectionTitle(title, subtitle, Modifier.padding(start = 36.dp, bottom = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(list.distinctBy { it.key }, key = { it.key }) { item ->
                val movie = item.movie
                val series = item.series
                UltraPosterCard(
                    title = movie?.name ?: series?.name ?: "",
                    imageUrl = movie?.poster ?: series?.cover,
                    rating10 = movie?.rating10 ?: series?.rating10 ?: 0f,
                    favorite = false,
                    width = 92.dp,
                    onFocus = { onFocus(item) },
                    onClick = {
                        if (movie != null) vm.openMovie(movie) else if (series != null) vm.openSeries(series)
                    }
                )
            }
        }
    }
}
