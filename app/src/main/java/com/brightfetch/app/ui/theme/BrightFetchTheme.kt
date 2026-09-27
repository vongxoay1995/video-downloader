package com.brightfetch.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Violet = Color(0xFF6153E8)
val Canvas = Color(0xFFF8F9FC)
val Ink = Color(0xFF192136)
val Muted = Color(0xFF626D82)
val Hairline = Color(0xFFE7EAF2)
val SoftViolet = Color(0xFFEEEBFF)
val Success = Color(0xFF167D66)
val SoftSuccess = Color(0xFFE0F5EE)

private val BrightFetchColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = SoftViolet,
    onPrimaryContainer = Ink,
    secondary = Muted,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = SoftViolet,
    onSurfaceVariant = Muted,
    outlineVariant = Hairline,
    error = Color(0xFFBC4247),
)

@Composable
fun BrightFetchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrightFetchColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
