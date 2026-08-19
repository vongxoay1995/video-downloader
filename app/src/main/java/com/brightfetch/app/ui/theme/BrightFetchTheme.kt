package com.brightfetch.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SunnyYellow = Color(0xFFFFD84D)
val WarmCream = Color(0xFFFFFDF5)
val Mint = Color(0xFFD7F1E2)
val Ink = Color(0xFF17231B)
val SoftSurface = Color(0xFFF6F4EE)

private val BrightFetchColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF5D6F63),
    background = WarmCream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = SoftSurface,
    onSurfaceVariant = Color(0xFF526056),
    error = Color(0xFFBA1A1A),
)

@Composable
fun BrightFetchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrightFetchColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
