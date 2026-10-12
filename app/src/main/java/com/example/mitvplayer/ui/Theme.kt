package com.example.mitvplayer.ui

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

val BackgroundColor = Color(0xFF0A0C22)
val BackgroundGlow = Color(0xFF1B2056)
val SidebarColor = Color(0xFF05061A)
val PanelColor = Color(0xFF232A63)
val PanelFocusedColor = Color(0xFF3040A0)
val RowSelectedColor = Color(0xFF2E3470)
val AccentColor = Color(0xFF2F7BFF)
val OnAccentColor = Color(0xFF04132B)
val TextPrimary = Color(0xFFF2F5FA)
val TextSecondary = Color(0xFFA3AECC)
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

/** Fondo azul marino con un resplandor suave al centro, común a todas las pantallas. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(BackgroundGlow, BackgroundColor))),
        content = content
    )
}
