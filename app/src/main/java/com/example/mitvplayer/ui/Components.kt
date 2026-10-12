package com.example.mitvplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Tarjeta enfocable con el control remoto: al enfocarla crece un poco y muestra un borde de color.
 * OK / Enter del control dispara onClick.
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    scaleOnFocus: Float = 1.06f,
    selected: Boolean = false,
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
            .background(if (focused) PanelFocusedColor else PanelColor, shape)
            .border(borderWidth, borderColor, shape)
            .clickable(onClick = onClick)
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
    enabled: Boolean = true
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
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) OnAccentColor else TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

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

@Composable
fun PosterTile(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusCard(onClick = onClick, modifier = modifier) {
        Column {
            PosterImage(
                url = imageUrl,
                title = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun CenterLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentColor, modifier = Modifier.size(40.dp))
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
