package com.redcoal.videoplayback.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors = darkColorScheme(
    primary = Color(0xFF38D8C0),
    onPrimary = Color(0xFF05201C),
    secondary = Color(0xFFFF7A59),
    onSecondary = Color(0xFF2A0C05),
    background = Color(0xFF12151B),
    onBackground = Color(0xFFF4F7F8),
    surface = Color(0xFF1B2028),
    onSurface = Color(0xFFF4F7F8),
    surfaceVariant = Color(0xFF252C35),
    onSurfaceVariant = Color(0xFFBCC6CD),
    outline = Color(0xFF46515C),
    error = Color(0xFFFF6B72),
)

@Composable
fun VideoPlaybackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
