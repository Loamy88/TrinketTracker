package com.vexiq.trinkettracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = VexRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = VexGold,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFEA82),
    onSecondaryContainer = Color(0xFF241A00),
    background = LightBackground,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = SearchBarBackground,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
)

private val DarkColorScheme = darkColorScheme(
    primary = VexRedLight,
    onPrimary = Color.White,
    primaryContainer = VexRedDark,
    onPrimaryContainer = Color.White,
    secondary = VexGold,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    error = ErrorRed,
)

@Composable
fun TrinketTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
