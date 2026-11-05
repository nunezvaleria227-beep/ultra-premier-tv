package com.example.mitvplayer.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Forma inclinada (paralelogramo), como los gráficos de las transmisiones deportivas. */
class SlantShape(private val skew: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { skew.toPx() }.coerceAtMost(size.width / 3)
        val path = Path().apply {
            moveTo(s, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - s, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Pestañas de la barra superior. */
enum class UltraTab { HOME, LIVE, MOVIES, SERIES, SEARCH, MORE }

/** Espacio que ocupa la barra superior. */
val TopBarHeight = 58.dp

/**
 * Barra superior de Ultra Premier TV: logo, pestañas inclinadas (Inicio, En vivo, Películas, Series, Buscar, Más)
 * y el reloj. La pestaña de la pantalla actual va en azul.
 */
@Composable
fun UltraTopBar(
    vm: AppViewModel,
    active: UltraTab,
    modifier: Modifier = Modifier,
    onActive: () -> Unit = {}
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showMore by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val tabs = listOf(
        UltraTab.HOME to "Inicio",
        UltraTab.LIVE to "En vivo",
        UltraTab.MOVIES to "Películas",
        UltraTab.SERIES to "Series",
        UltraTab.SEARCH to "Buscar",
        UltraTab.MORE to "Más"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .background(Brush.verticalGradient(listOf(BackgroundColor.copy(alpha = 0.95f), BackgroundColor.copy(alpha = 0f))))
            .padding(horizontal = 36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLogo(Modifier.height(36.dp))
        Spacer(Modifier.width(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            tabs.forEach { (tab, label) ->
                TopTab(label = label, active = tab == active) {
                    when {
                        tab == UltraTab.MORE -> showMore = true
                        tab == active -> onActive()
                        tab == UltraTab.HOME -> vm.switchTo(null)
                        tab == UltraTab.LIVE -> vm.switchTo(Screen.Live)
                        tab == UltraTab.MOVIES -> vm.switchTo(Screen.Movies)
                        tab == UltraTab.SERIES -> vm.switchTo(Screen.SeriesList)
                        tab == UltraTab.SEARCH -> vm.switchTo(Screen.Search(SearchScope.ALL))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatClock(now), style = sporty(17f), color = TextPrimary)
            Text(longDate(now).uppercase(), color = TextTertiary, fontSize = 8.sp, fontWeight = FontWeight.Medium)
        }
    }

    if (showMore) {
        OptionsDialog(
            title = "Más opciones",
            options = listOf(
                Opt("Guía de TV") { vm.switchTo(Screen.Epg) },
                Opt("Continuar viendo") { vm.switchTo(Screen.Continue) },
                Opt("Pantalla múltiple") { vm.switchTo(Screen.Multi) },
                Opt("Catch up (programas que ya pasaron)") { vm.switchTo(Screen.CatchUp) },
                Opt("Radio") { vm.switchTo(Screen.Radio) },
                Opt("Grabaciones") { vm.switchTo(Screen.Recordings) },
                Opt("Avisos") { vm.switchTo(Screen.Notifications) },
                Opt("Mi cuenta") { vm.switchTo(Screen.Account) },
                Opt("Ajustes") { vm.switchTo(Screen.Settings) },
                Opt("Cancelar") { }
            ),
            onDismiss = { showMore = false }
        )
    }
}

@Composable
private fun TopTab(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = SlantShape(6.dp)
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .shadow(if (active || focused) 10.dp else 0.dp, shape, ambientColor = AccentColor, spotColor = AccentColor)
            .clip(shape)
            .then(
                when {
                    focused -> Modifier.background(Color.White)
                    active -> Modifier.background(AccentBrush)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 6.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = sporty(11f),
            color = when {
                focused -> AccentDeep
                active -> Color.White
                else -> TextTertiary
            }
        )
    }
}

/** "MIÉRCOLES 4 NOV" */
fun longDate(millis: Long): String =
    java.text.SimpleDateFormat("EEEE d MMM", java.util.Locale.forLanguageTag("es-MX")).format(java.util.Date(millis))
        .replace(".", "")

/** Botón inclinado: el principal es blanco con letras azules; el otro, solo con borde. */
@Composable
fun SlantButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "slant")
    val shape = SlantShape(7.dp)
    val filled = primary || focused
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(scale)
            .shadow(if (focused) 16.dp else if (primary) 8.dp else 0.dp, shape, ambientColor = AccentColor, spotColor = if (focused) AccentLight else AccentColor)
            .clip(shape)
            .then(if (filled) Modifier.background(Color.White) else Modifier.background(Color(0x14FFFFFF)))
            .border(if (filled) 0.dp else 1.5.dp, if (filled) Color.Transparent else Color(0x99FFFFFF), shape)
            .clickable(onClick = onClick)
            .height(32.dp)
            .padding(start = if (icon != null) 16.dp else 18.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (filled) AccentDeep else Color.White
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), style = sporty(11.5f), color = tint, maxLines = 1)
    }
}

/** Pastilla inclinada (categorías, temporadas y filtros). */
@Composable
fun SlantChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "chip")
    val shape = SlantShape(5.dp)
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(scale)
            .shadow(if (focused || selected) 8.dp else 0.dp, shape, ambientColor = AccentColor, spotColor = AccentColor)
            .clip(shape)
            .then(
                when {
                    focused -> Modifier.background(Color.White)
                    selected -> Modifier.background(AccentBrush)
                    else -> Modifier.background(Color(0x12FFFFFF))
                }
            )
            .border(if (focused || selected) 0.dp else 0.5.dp, if (focused || selected) Color.Transparent else GlassBorder, shape)
            .clickable(onClick = onClick)
            .height(24.dp)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = sporty(9f, FontWeight.ExtraBold),
            color = when {
                focused -> AccentDeep
                selected -> Color.White
                else -> TextSecondary
            },
            maxLines = 1
        )
    }
}

/** Categorías como pastillas inclinadas; la elegida va en azul y al entrar el foco va a ella. */
@Composable
fun CategoryChips(
    rows: List<CategoryRow>,
    selectedId: String,
    onSelect: (String) -> Unit,
    selectedRequester: FocusRequester,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val selectedIndex = rows.indexOfFirst { it.id == selectedId }
    LaunchedEffect(rows.size) {
        if (selectedIndex > 2) listState.scrollToItem((selectedIndex - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
            SlantChip(
                text = if (row.id == ROW_FAV) "★ ${row.name}" else row.name,
                selected = row.id == selectedId,
                onClick = { onSelect(row.id) },
                modifier = if (index == selectedIndex) Modifier.focusRequester(selectedRequester) else Modifier
            )
        }
    }
}

/** Título de sección con la barrita azul inclinada. */
@Composable
fun SectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, size: Float = 15f) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(5.dp, (size * 0.95f).dp)
                .clip(SlantShape(2.dp))
                .background(AccentBrush)
        )
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), style = sporty(size), color = TextPrimary, maxLines = 1)
        if (subtitle != null) {
            Spacer(Modifier.width(8.dp))
            Text("· $subtitle", color = TextTertiary, fontSize = (size * 0.6f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Insignia roja de "EN VIVO". */
@Composable
fun LiveBadge(text: String = "EN VIVO", modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(LiveRed)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
        Spacer(Modifier.width(4.dp))
        Text(text, style = sporty(7.5f), color = Color.White)
    }
}

/** Cartel con el título debajo; al enfocarlo crece con borde blanco y brillo azul. */
@Composable
fun UltraPosterCard(
    title: String,
    imageUrl: String?,
    rating10: Float,
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    Column(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()) {
        FocusCard(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                },
            shape = RoundedCornerShape(8.dp),
            scaleOnFocus = 1.07f
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                PosterImage(url = imageUrl, title = title, modifier = Modifier.fillMaxSize())
                if (rating10 >= 0.5f) {
                    RatingBadge(rating10, Modifier.align(Alignment.TopStart).padding(4.dp))
                }
                if (favorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Favorito",
                        tint = StarColor,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(15.dp)
                    )
                }
            }
        }
        Text(
            text = title.uppercase(),
            style = sporty(8f, FontWeight.ExtraBold),
            color = if (focused) Color.White else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp)
        )
    }
}

/** Barra de progreso delgada (azul a cian). */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color(0x33FFFFFF))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(AccentColor, AccentLight)))
        )
    }
}

/** Encabezado de las secciones: la barra de arriba y, debajo, el nombre de la sección con su ícono y sus botones. */
@Composable
fun UltraSectionHeader(
    vm: AppViewModel,
    active: UltraTab,
    title: String,
    icon: (@Composable (Color) -> Unit)? = null,
    onActive: () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    Column(Modifier.fillMaxWidth()) {
        UltraTopBar(vm = vm, active = active, onActive = onActive)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, end = 36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon(TextPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title.uppercase(),
                style = sporty(20f),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions)
        }
    }
}
