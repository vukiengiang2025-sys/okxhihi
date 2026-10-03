package com.example.okxkelly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = LimeAccent,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF223000),
    onPrimaryContainer = LimeAccent,
    secondary = LimeAccent,
    onSecondary = Color(0xFF000000),
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark,
    error = LossRed,
    onError = Color(0xFF000000)
)

@Composable
fun OKXKellyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
