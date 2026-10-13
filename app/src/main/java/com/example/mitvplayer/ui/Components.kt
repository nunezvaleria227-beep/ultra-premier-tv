package com.example.mitvplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.mitvplayer.R

/**
 * Tarjeta enfocable con el control remoto: al enfocarla crece un poco y muestra un borde de color.
 * OK / Enter del control (o un toque) dispara onClick. Un toque largo, o la tecla Menú del control,
 * dispara onLongClick.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    scaleOnFocus: Float = 1.06f,
    selected: Boolean = false,
    background: Color = PanelColor,
    focusedBackground: Color = PanelFocusedColor,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) scaleOnFocus else 1f, label = "focusScale")
    val borderColor = when {
        focused -> AccentColor
        selected -> AccentColor.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val borderWidth = when {
        focused -> 3.dp
        selected -> 2.dp
        else -> 0.dp
    }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(scale)
            .clip(shape)
            .background(if (focused) focusedBackground else background, shape)
            .border(borderWidth, borderColor, shape)
            .onKeyEvent { event ->
                if (onLongClick != null && event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                    onLongClick()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        content(focused)
    }
}

/** Botón con foco muy visible (acento al enfocar). */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val background = when {
        focused -> AccentColor
        selected -> AccentColor.copy(alpha = 0.30f)
        else -> PanelFocusedColor
    }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background, shape)
            .then(if (selected && !focused) Modifier.border(2.dp, AccentColor, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 24.dp, vertical = if (compact) 8.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) OnAccentColor else TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 12.sp else 16.sp,
            maxLines = 1
        )
    }
}

/** Botón redondo con un ícono (barras superiores). */
@Composable
fun IconAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable BoxScope.() -> Unit
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        scaleOnFocus = 1.1f,
        background = Color.Transparent
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center, content = content)
    }
}

/** Proporción del logo vectorial (res/drawable/logo_ultra.xml): se ve nítido a cualquier tamaño, también en 4K. */
const val LOGO_RATIO = 2906f / 1593f

@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo_ultra),
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Fit,
        modifier = modifier.aspectRatio(LOGO_RATIO)
    )
}

class MenuEntry(val label: String, val checked: Boolean = false, val onClick: () -> Unit)

/** Botón de tres puntos con su menú desplegable. */
@Composable
fun MoreMenu(entries: List<MenuEntry>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconAction(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones", tint = TextPrimary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(if (entry.checked) "✓  ${entry.label}" else entry.label) },
                    onClick = {
                        open = false
                        entry.onClick()
                    }
                )
            }
        }
    }
}

/** Barra superior: atrás, logo, título al centro, búsqueda y menú de tres puntos. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showLogo: Boolean = true,
    centerTitle: Boolean = false,
    onSearch: (() -> Unit)? = null,
    menu: List<MenuEntry> = emptyList(),
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconAction(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
        }
        leading()
        if (showLogo) {
            AppLogo(Modifier.height(34.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(
            Modifier.weight(1f),
            contentAlignment = if (centerTitle) Alignment.Center else Alignment.CenterStart
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
        if (menu.isNotEmpty()) {
            MoreMenu(menu)
            Spacer(Modifier.width(4.dp))
        }
        // La lupa va siempre en la esquina superior derecha.
        if (onSearch != null) {
            IconAction(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = TextPrimary)
            }
        }
    }
}

/** Campo de texto de una línea con lupa, como "Buscar en categorías". */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color(0x33FFFFFF), shape)
            .border(2.dp, if (focused) AccentColor else Color.Transparent, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = TextSecondary, fontSize = 15.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(AccentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
            )
        }
    }
}

class Opt(val label: String, val action: () -> Unit)

/** Ventana con una lista de opciones (confirmaciones, elegir duración, etc.). */
@Composable
fun OptionsDialog(
    title: String,
    options: List<Opt>,
    onDismiss: () -> Unit,
    message: String? = null
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        first.tryRequestFocus()
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B2050))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (message != null) {
                Text(message, color = TextSecondary)
            }
            options.forEachIndexed { index, option ->
                TvButton(
                    text = option.label,
                    onClick = {
                        onDismiss()
                        option.action()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- imágenes

@Composable
fun PosterImage(url: String?, title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFF1B2332)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.take(1).uppercase(),
            style = MaterialTheme.typography.displaySmall,
            color = TextSecondary
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Cuadrito azul con la calificación, arriba a la izquierda del póster. */
@Composable
fun RatingBadge(rating10: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(AccentColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = Math.round(rating10).toString(),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Póster con el título encima, la calificación y la estrella de favorito. */
@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    rating10: Float,
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        scaleOnFocus = 1.05f
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            PosterImage(url = imageUrl, title = title, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (rating10 >= 0.5f) {
                RatingBadge(rating10, Modifier.align(Alignment.TopStart).padding(5.dp))
            }
            if (favorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Favorito",
                    tint = StarColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ChannelLogo(icon: String?, name: String, size: Dp = 48.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1B2332)),
        contentAlignment = Alignment.Center
    ) {
        if (icon == null) {
            Text(text = name.take(1).uppercase(), color = TextSecondary)
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

// ----------------------------------------------------------------- estados

@Composable
fun CenterLoading(modifier: Modifier = Modifier, text: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = AccentColor, modifier = Modifier.size(40.dp))
            if (text != null) {
                Text(text, color = TextSecondary, textAlign = TextAlign.Center, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
fun ErrorWithRetry(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = message, color = ErrorColor, textAlign = TextAlign.Center)
            TvButton(text = "Reintentar", onClick = onRetry)
        }
    }
}

/** Pide el foco sin romper la app si el elemento todavía no está en pantalla. */
fun FocusRequester.tryRequestFocus() {
    try {
        requestFocus()
    } catch (e: Exception) {
        // El elemento aún no existe en la composición: se ignora.
    }
}

fun Modifier.focusIf(condition: Boolean, requester: FocusRequester): Modifier =
    if (condition) this.focusRequester(requester) else this
