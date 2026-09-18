package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioColorScheme = darkColorScheme(
    primary = StudioPurple,
    onPrimary = Color.White,
    primaryContainer = StudioCardElevated,
    onPrimaryContainer = StudioCyan,
    secondary = StudioPink,
    onSecondary = Color.White,
    secondaryContainer = StudioCard,
    onSecondaryContainer = Color.White,
    tertiary = StudioOrange,
    background = StudioDark,
    onBackground = StudioTextPrimary,
    surface = StudioCard,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioCardElevated,
    onSurfaceVariant = StudioTextSecondary,
    outline = StudioBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = Typography,
        content = content
    )
}
