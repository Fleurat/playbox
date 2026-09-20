package com.example.playbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** One flat palette — the app is dark whatever the system says. */
object Playbox {
    val Background = Color(0xFF0A0A0D)
    val Surface = Color(0xFF1C1C1F)
    val SurfaceHigh = Color(0xFF2B2B30)
    val Accent = Color(0xFFE53030)
    val PrimaryText = Color(0xFFFFFFFF)
    val SecondaryText = Color(0xFFA1A1A6)
    val Divider = Color(0x14FFFFFF)

    private val avatarPalette = listOf(
        Color(0xFFE64A3D), Color(0xFF3399DB), Color(0xFF9B59B6),
        Color(0xFF2EB887), Color(0xFFF39C12), Color(0xFFE84D8A),
        Color(0xFF4D66CC),
    )

    /** Deterministic avatar tint, so a video always gets the same colour. */
    fun avatarColor(seed: String): Color {
        var hash = 0
        for (ch in seed) hash = (hash * 31 + ch.code) and 0xFFFFFF
        return avatarPalette[hash % avatarPalette.size]
    }
}

private val PlayboxColors = darkColorScheme(
    primary = Playbox.Accent,
    onPrimary = Color.White,
    background = Playbox.Background,
    onBackground = Playbox.PrimaryText,
    surface = Playbox.Surface,
    onSurface = Playbox.PrimaryText,
    surfaceVariant = Playbox.SurfaceHigh,
    onSurfaceVariant = Playbox.SecondaryText,
)

@Composable
fun PlayboxTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = PlayboxColors, content = content)
}
