package com.example.mitvplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BackgroundColor = Color(0xFF0B0F17)
val PanelColor = Color(0xFF151B27)
val PanelFocusedColor = Color(0xFF212B3F)
val AccentColor = Color(0xFF2F7BFF)
val OnAccentColor = Color(0xFF04132B)
val TextPrimary = Color(0xFFF2F5FA)
val TextSecondary = Color(0xFF9AA6BC)
val ErrorColor = Color(0xFFFF6B6B)

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
