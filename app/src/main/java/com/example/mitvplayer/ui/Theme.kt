package com.example.mitvplayer.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.mitvplayer.R

// Paleta de Ultra Premier TV: azul marino de noche, azul eléctrico, cian y blanco (estilo transmisión deportiva).
val BackgroundColor = Color(0xFF040A1E)
val BackgroundGlow = Color(0xFF0E2A7A)
val SidebarColor = Color(0xFF030716)
val PanelColor = Color(0xFF10204F)
val PanelFocusedColor = Color(0xFF1B3480)
val RowSelectedColor = Color(0xFF16295F)
val AccentColor = Color(0xFF2F7BFF)
val AccentDeep = Color(0xFF1446D8)
val AccentLight = Color(0xFF3FD0FF)
val OnAccentColor = Color(0xFF04132B)
val TextPrimary = Color(0xFFF2F6FF)
val TextSecondary = Color(0xFFB8C6EC)
val TextTertiary = Color(0xFF8FA3D6)
val ErrorColor = Color(0xFFFF6B6B)
val StarColor = Color(0xFFFFC93C)
val LiveRed = Color(0xFFFF2D55)
val FocusRingColor = Color(0xFFFFFFFF)

/** Fondo "de vidrio" de tarjetas y botones sin foco. */
val GlassColor = Color(0x10FFFFFF)
val GlassBorder = Color(0x2E8CB4FF)

/** Degradado azul de la marca (pestaña activa, insignias, botones). */
val AccentBrush = Brush.linearGradient(listOf(AccentColor, AccentDeep))

/** Letra de toda la app: Inter Display (en cursiva gruesa para los títulos, como el logo). */
val UltraFont = FontFamily(
    Font(R.font.inter_display_regular, FontWeight.Normal),
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semi_bold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
    Font(R.font.inter_display_black, FontWeight.ExtraBold),
    Font(R.font.inter_display_black, FontWeight.Black),
    Font(R.font.inter_display_extra_bold_italic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.inter_display_black_italic, FontWeight.Black, FontStyle.Italic)
)

private fun TextStyle.ultra(weight: FontWeight? = null) = copy(fontFamily = UltraFont, fontWeight = weight ?: fontWeight)

private val UltraTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.ultra(FontWeight.Black),
        displayMedium = t.displayMedium.ultra(FontWeight.Black),
        displaySmall = t.displaySmall.ultra(FontWeight.Bold),
        headlineLarge = t.headlineLarge.ultra(FontWeight.Black),
        headlineMedium = t.headlineMedium.ultra(FontWeight.Black),
        headlineSmall = t.headlineSmall.ultra(FontWeight.Bold),
        titleLarge = t.titleLarge.ultra(FontWeight.Bold),
        titleMedium = t.titleMedium.ultra(FontWeight.Bold),
        titleSmall = t.titleSmall.ultra(FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.ultra(),
        bodyMedium = t.bodyMedium.ultra(),
        bodySmall = t.bodySmall.ultra(),
        labelLarge = t.labelLarge.ultra(FontWeight.SemiBold),
        labelMedium = t.labelMedium.ultra(FontWeight.SemiBold),
        labelSmall = t.labelSmall.ultra(FontWeight.Medium)
    )
}

/** Estilo de los títulos deportivos: cursiva gruesa en mayúsculas. */
fun sporty(size: Float, weight: FontWeight = FontWeight.Black): TextStyle = TextStyle(
    fontFamily = UltraFont,
    fontWeight = weight,
    fontStyle = FontStyle.Italic,
    fontSize = size.sp,
    letterSpacing = 0.2.sp
)

@Composable
fun MiTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = UltraTypography,
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

private val artCache = java.util.concurrent.ConcurrentHashMap<Int, ImageBitmap>()

/**
 * Deja listos los fondos y logos al abrir la app, en segundo plano: así la primera vez que se entra a
 * cada sección no hay que esperar a que se lean las imágenes.
 */
fun preloadArt(resources: android.content.res.Resources, ids: List<Int>) {
    for (id in ids) {
        try {
            if (!artCache.containsKey(id)) artCache.putIfAbsent(id, ImageBitmap.imageResource(resources, id))
        } catch (e: Throwable) {
            // Si una no se puede leer, se intentará cuando se use.
        }
    }
}

/** Imagen de res/drawable decodificada una sola vez (las pantallas cambian seguido y así no se vuelve a leer). */
@Composable
fun rememberArt(@DrawableRes id: Int): ImageBitmap {
    val resources = LocalContext.current.applicationContext.resources
    return remember(id) { artCache.getOrPut(id) { ImageBitmap.imageResource(resources, id) } }
}

/**
 * Fondo común a todas las pantallas: una imagen (por defecto el fondo azul de Ultra Premier TV)
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
