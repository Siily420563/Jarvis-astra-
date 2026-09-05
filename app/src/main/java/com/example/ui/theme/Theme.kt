package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color(0xFF001B2E),
    primaryContainer = JarvisSurfaceVariant,
    onPrimaryContainer = JarvisCyan,
    secondary = JarvisCyanDark,
    onSecondary = Color.White,
    secondaryContainer = JarvisSurfaceVariant,
    onSecondaryContainer = JarvisCyan,
    tertiary = JarvisBlue,
    background = JarvisDeepBg,
    onBackground = TextPrimary,
    surface = JarvisSurface,
    onSurface = TextPrimary,
    surfaceVariant = JarvisSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = JarvisCardBorder,
    error = JarvisError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
