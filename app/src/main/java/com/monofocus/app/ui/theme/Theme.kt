package com.monofocus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val LightColors = lightColorScheme(
    primary = Color(0xFF246B55),
    onPrimary = Color.White,
    secondary = Color(0xFF6B5D2E),
    background = Color(0xFFF8FAF6),
    onBackground = Color(0xFF1B1F1C),
    surface = Color(0xFFF8FAF6),
    surfaceVariant = Color(0xFFE0E6DF),
    onSurfaceVariant = Color(0xFF424A44),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF86D5B7),
    onPrimary = Color(0xFF003829),
    secondary = Color(0xFFD8C783),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE4E9E3),
    surface = Color(0xFF111411),
    surfaceVariant = Color(0xFF404942),
    onSurfaceVariant = Color(0xFFC0C9C1),
)

@Composable
fun MonoFocusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
