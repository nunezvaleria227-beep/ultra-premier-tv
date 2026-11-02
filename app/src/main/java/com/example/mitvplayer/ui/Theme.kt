package com.example.mitvplayer.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.runtime.remember
import com.example.mitvplayer.R

val BackgroundColor = Color(0xFF070708)
val BackgroundGlow = Color(0xFF141416)
val SidebarColor = Color(0xFF0B0B0D)
val PanelColor = Color(0xFF17171A)
val PanelFocusedColor = Color(0xFF26262C)
val RowSelectedColor = Color(0xFF1E1E23)
val AccentColor = Color(0xFFE11D27)
val OnAccentColor = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFF4F4F6)
val TextSecondary = Color(0xFFB0B0B8)
val TextTertiary = Color(0xFF8B8B94)
val AccentText = Color(0xFFFF4A50)
val FocusRingColor = Color(0xFFFFFFFF)
val LineColor = Color(0xFF2A2A2F)
val ErrorColor = Color(0xFFFF6B6B)
val StarColor = Color(0xFFFFC93C)

@Composable
fun MiTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentColor,
            onPrimary = OnAccentColor,
            background = BackgroundColor,
            onBackground = TextPrimary,
            surface = PanelColor,
            onSurface = TextPrimary,
            surfaceVariant = PanelFocusedColor,
            onSurfaceVariant = TextSecondary,
            error = ErrorColor
        ),
        content = content
    )
}

private val artCache = HashMap<Int, ImageBitmap>()

/** Imagen de res/drawable decodificada una sola vez (las pantallas cambian seguido y así no se vuelve a leer). */
@Composable
fun rememberArt(@DrawableRes id: Int): ImageBitmap {
    val resources = LocalContext.current.applicationContext.resources
    return remember(id) { artCache.getOrPut(id) { ImageBitmap.imageResource(resources, id) } }
}

/**
 * Fondo común a todas las pantallas: una imagen (por defecto el fondo negro de Red Max Plus)
 * oscurecida un poco, [dim] de 0 a 1, para que el texto se lea bien encima.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    @DrawableRes art: Int = R.drawable.bg_home,
    dim: Float = 0.30f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Image(
            bitmap = rememberArt(art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SidebarColor.copy(alpha = dim * 0.7f),
                            SidebarColor.copy(alpha = dim)
                        )
                    )
                )
        )
        content()
    }
}
