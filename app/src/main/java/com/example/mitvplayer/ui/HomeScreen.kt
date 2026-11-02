package com.example.mitvplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mitvplayer.R
import com.example.mitvplayer.data.AccountInfo
import com.example.mitvplayer.data.Kind
import com.example.mitvplayer.data.Movie
import com.example.mitvplayer.data.MovieInfo
import com.example.mitvplayer.data.ResumeEntry
import kotlinx.coroutines.delay

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Un botón del menú lateral. */
private class RailItem(
    val label: String,
    val active: Boolean = false,
    val requester: FocusRequester? = null,
    val onClick: () -> Unit,
    val icon: @Composable (Color) -> Unit
)

/** Una tarjeta de una fila de pósters. */
private class Tile(
    val key: String,
    val title: String,
    val image: String?,
    val rating10: Float,
    val onClick: () -> Unit
)

/**
 * Inicio de Red Max Plus: menú lateral fijo, la película más nueva en grande ("Ver ahora")
 * y abajo filas para seguir viendo y descubrir lo nuevo.
 */
@Composable
fun HomeScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmExit by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val heroPlay = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        vm.live.ensureLoaded()
        vm.movies.ensureLoaded()
        vm.series.ensureLoaded()
    }
    // El reloj se actualiza solo.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }

    val movies = vm.movies.visibleItems
    val seriesItems = vm.series.visibleItems
    val newMovies = remember(movies) { movies.sortedByDescending { it.added }.take(24) }
    val topMovies = remember(movies) {
        movies.filter { it.rating10 >= 7f }.sortedByDescending { it.rating10 }.take(24)
    }
    val newSeries = remember(seriesItems) { seriesItems.sortedByDescending { it.added }.take(24) }
    val featured = newMovies.firstOrNull()
    var featuredInfo by remember(featured?.id) { mutableStateOf<MovieInfo?>(null) }
    var featuredFav by remember(featured?.id) {
        mutableStateOf(featured?.let { vm.isFavorite(Kind.MOVIES, it.id) } ?: false)
    }
    LaunchedEffect(featured?.id) {
        val movie = featured ?: return@LaunchedEffect
        featuredInfo = try {
            vm.loadMovieInfo(movie)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }
    val resume = vm.resumeEntries

    // Al entrar, el foco va al botón grande de "Ver ahora".
    LaunchedEffect(featured != null) {
        withFrameNanos { }
        withFrameNanos { }
        heroPlay.tryRequestFocus()
    }

    val rail = listOf(
        RailItem("Buscar", onClick = { vm.open(Screen.Search(SearchScope.ALL)) }) { tint ->
            Icon(Icons.Filled.Search, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        },
        RailItem("Inicio", active = true, onClick = { heroPlay.tryRequestFocus() }) { tint ->
            Icon(Icons.Filled.Home, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        },
        RailItem("En vivo", onClick = { vm.open(Screen.Live) }) { tint ->
            GlyphIcon(Glyph.TV, Modifier.size(20.dp), tint)
        },
        RailItem("Películas", onClick = { vm.open(Screen.Movies) }) { tint ->
            GlyphIcon(Glyph.CLAPPER, Modifier.size(20.dp), tint)
        },
        RailItem("Series", onClick = { vm.open(Screen.SeriesList) }) { tint ->
            GlyphIcon(Glyph.SERIES, Modifier.size(20.dp), tint)
        },
        RailItem("Guía", onClick = { vm.open(Screen.Epg) }) { tint ->
            GlyphIcon(Glyph.EPG, Modifier.size(20.dp), tint)
        },
        RailItem("Más", onClick = { showMore = true }) { tint ->
            GlyphIcon(Glyph.GRID, Modifier.size(20.dp), tint)
        }
    )
    val railBottom = listOf(
        RailItem("Ajustes", onClick = { vm.open(Screen.Settings) }) { tint ->
            Icon(Icons.Filled.Settings, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        },
        RailItem("Salir", onClick = { confirmExit = true }) { tint ->
            GlyphIcon(Glyph.POWER, Modifier.size(20.dp), tint)
        }
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Row(Modifier.fillMaxSize()) {
            HomeRail(rail, railBottom)
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1E1E22))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                Hero(
                    vm = vm,
                    featured = featured,
                    info = featuredInfo,
                    now = now,
                    playRequester = heroPlay,
                    favorite = featuredFav,
                    onPlay = { featured?.let { vm.playMovie(context, it, info = featuredInfo) } },
                    onInfo = { featured?.let { vm.openMovie(it) } },
                    onToggleFav = {
                        featured?.let {
                            vm.toggleFavorite(Kind.MOVIES, it.id)
                            featuredFav = vm.isFavorite(Kind.MOVIES, it.id)
                        }
                    },
                    onLive = { vm.open(Screen.Live) }
                )
                if (resume.isNotEmpty()) {
                    ContinueRow(resume) { vm.playResume(context, it) }
                }
                if (newMovies.isNotEmpty()) {
                    PosterRow(
                        "Películas nuevas",
                        newMovies.map { m -> Tile("m" + m.id, m.name, m.poster, m.rating10) { vm.openMovie(m) } }
                    )
                }
                if (newSeries.isNotEmpty()) {
                    PosterRow(
                        "Series nuevas",
                        newSeries.map { s -> Tile("s" + s.id, s.name, s.cover, s.rating10) { vm.openSeries(s) } }
                    )
                }
                if (topMovies.isNotEmpty()) {
                    PosterRow(
                        "Las mejor calificadas",
                        topMovies.map { m -> Tile("t" + m.id, m.name, m.poster, m.rating10) { vm.openMovie(m) } }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    UpdateDialogs(vm)

    if (showMore) {
        OptionsDialog(
            title = "Más opciones",
            options = listOf(
                Opt("Continuar viendo") { vm.open(Screen.Continue) },
                Opt("Pantalla múltiple") { vm.open(Screen.Multi) },
                Opt("Catch up (programas que ya pasaron)") { vm.open(Screen.CatchUp) },
                Opt("Radio") { vm.open(Screen.Radio) },
                Opt("Grabaciones") { vm.open(Screen.Recordings) },
                Opt("Avisos") { vm.open(Screen.Notifications) },
                Opt("Mi cuenta") { vm.open(Screen.Account) },
                Opt("Cancelar") { }
            ),
            onDismiss = { showMore = false }
        )
    }

    if (confirmExit) {
        OptionsDialog(
            title = "¿Qué quieres hacer?",
            options = listOf(
                Opt("Cerrar sesión") { vm.logout() },
                Opt("Salir de la aplicación") { context.findActivity()?.finish() },
                Opt("Cancelar") { }
            ),
            onDismiss = { confirmExit = false }
        )
    }
}

// ------------------------------------------------------------------ menú lateral

@Composable
private fun HomeRail(top: List<RailItem>, bottom: List<RailItem>) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .fillMaxHeight()
            .background(SidebarColor)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.logo_mark),
            contentDescription = "Red Max Plus",
            modifier = Modifier
                .width(40.dp)
                .padding(bottom = 14.dp)
        )
        top.forEach { RailButton(it) }
        Spacer(Modifier.weight(1f))
        bottom.forEach { RailButton(it) }
    }
}

@Composable
private fun RailButton(item: RailItem) {
    FocusCard(
        onClick = item.onClick,
        modifier = Modifier
            .width(56.dp)
            .height(44.dp)
            .then(if (item.requester != null) Modifier.focusRequester(item.requester) else Modifier),
        shape = RoundedCornerShape(10.dp),
        scaleOnFocus = 1.06f,
        background = if (item.active) AccentColor else Color.Transparent,
        focusedBackground = if (item.active) AccentColor else PanelFocusedColor
    ) { focused ->
        val tint = if (item.active || focused) Color.White else TextTertiary
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { item.icon(tint) }
            Text(
                text = item.label,
                color = tint,
                fontSize = 8.sp,
                maxLines = 1,
                fontWeight = if (item.active) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

// ------------------------------------------------------------------ destacado

@Composable
private fun Hero(
    vm: AppViewModel,
    featured: Movie?,
    info: MovieInfo?,
    now: Long,
    playRequester: FocusRequester,
    favorite: Boolean,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    onToggleFav: () -> Unit,
    onLive: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(330.dp)
    ) {
        // Imagen de fondo a la derecha, que se funde con el negro hacia la izquierda y hacia abajo.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.68f)
                .fillMaxHeight()
        ) {
            val art = info?.backdrop?.takeIf { it.isNotBlank() } ?: featured?.poster?.takeIf { it.isNotBlank() }
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    bitmap = rememberArt(R.drawable.bg_home),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to BackgroundColor,
                            0.45f to BackgroundColor.copy(alpha = 0.55f),
                            1f to Color.Transparent
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to BackgroundColor
                        )
                    )
            )
        }

        // Cuenta y reloj arriba a la derecha.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountChip(vm.account, now)
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatClock(now), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(formatDateLong(now), color = TextTertiary, fontSize = 10.sp)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 40.dp, bottom = 8.dp)
                .width(440.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (featured != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "NUEVO",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .background(AccentColor, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("PELÍCULA", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                }
                Text(
                    text = featured.name,
                    color = TextPrimary,
                    fontSize = 38.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    info?.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
                    info?.genre?.takeIf { it.isNotBlank() },
                    info?.duration?.takeIf { it.isNotBlank() }
                ).joinToString("  •  ")
                if (meta.isNotEmpty()) {
                    Text(meta, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val plot = info?.plot
                if (!plot.isNullOrBlank()) {
                    Text(
                        text = plot,
                        color = Color(0xFFC8C8CF),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroPrimaryButton("Ver ahora", Icons.Filled.PlayArrow, onPlay, Modifier.focusRequester(playRequester))
                    HeroButton("Más información", Icons.Filled.Info, onInfo)
                    HeroIconButton(
                        icon = if (favorite) Icons.Filled.Check else Icons.Filled.Add,
                        description = if (favorite) "Quitar de Mi lista" else "Agregar a Mi lista",
                        onClick = onToggleFav
                    )
                }
            } else {
                Text("Bienvenido a", color = TextSecondary, fontSize = 14.sp)
                AppLogo(Modifier.height(64.dp))
                Text(
                    text = if (vm.movies.loading) "Preparando tu lista… (solo tarda la primera vez)"
                    else "TV en vivo, películas y series en un solo lugar.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Row(Modifier.padding(top = 6.dp)) {
                    HeroPrimaryButton("Ver TV en vivo", Icons.Filled.PlayArrow, onLive, Modifier.focusRequester(playRequester))
                }
            }
        }
    }
}

@Composable
private fun AccountChip(account: AccountInfo?, now: Long) {
    val expiresAt = account?.expiresAt
    val daysLeft = expiresAt?.let { (it * 1000 - now) / 86_400_000L }
    val warn = daysLeft != null && daysLeft <= 7
    val text = when {
        expiresAt == null || daysLeft == null -> "Cuenta activa"
        daysLeft <= 0 -> "Tu cuenta venció"
        warn -> "Vence en $daysLeft días"
        else -> "Cuenta activa · vence ${formatDateLong(expiresAt * 1000)}"
    }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Color(0xCC141416))
            .border(1.dp, LineColor, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (warn) AccentColor else Color(0xFF3DDC84))
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
    }
}

/** Botón blanco principal; al enfocarlo crece y se rodea de rojo. */
@Composable
private fun HeroPrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "heroScale")
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(scale)
            .clip(shape)
            .background(Color.White, shape)
            .border(if (focused) 3.dp else 0.dp, if (focused) AccentColor else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BackgroundColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = BackgroundColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun HeroButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    FocusCard(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        scaleOnFocus = 1.05f,
        background = Color(0x1FFFFFFF),
        focusedBackground = Color(0x40FFFFFF)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun HeroIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FocusCard(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(10.dp),
        scaleOnFocus = 1.08f,
        background = Color(0x1FFFFFFF),
        focusedBackground = Color(0x40FFFFFF)
    ) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

// ------------------------------------------------------------------ filas

@Composable
private fun RowTitle(title: String, subtitle: String? = null) {
    Row(
        modifier = Modifier.padding(start = 40.dp, top = 14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.width(10.dp))
            Text(subtitle, color = TextTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ContinueRow(entries: List<ResumeEntry>, onPlay: (ResumeEntry) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        RowTitle("Continuar viendo", "Retoma donde te quedaste")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { it.key }) { entry ->
                FocusCard(
                    onClick = { onPlay(entry) },
                    modifier = Modifier.width(176.dp),
                    shape = RoundedCornerShape(10.dp),
                    scaleOnFocus = 1.05f
                ) {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        ) {
                            PosterImage(url = entry.image, title = entry.title, modifier = Modifier.fillMaxSize())
                            Text(
                                text = "Continuar ${formatDuration(entry.positionMs / 1000)}",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 6.dp, bottom = 8.dp)
                                    .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color(0x33FFFFFF))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(entry.fraction.coerceIn(0.02f, 1f))
                                        .fillMaxHeight()
                                        .background(AccentColor)
                                )
                            }
                        }
                        Text(
                            text = entry.title,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterRow(title: String, tiles: List<Tile>) {
    Column(Modifier.fillMaxWidth()) {
        RowTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tiles, key = { it.key }) { tile ->
                PosterCard(
                    title = tile.title,
                    imageUrl = tile.image,
                    rating10 = tile.rating10,
                    favorite = false,
                    onClick = tile.onClick,
                    modifier = Modifier.width(96.dp)
                )
            }
        }
    }
}
