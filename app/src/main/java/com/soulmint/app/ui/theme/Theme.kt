package com.soulmint.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgPrimary = Color(0xFF0A0A0F)
val BgSurface = Color(0xFF13131A)
val BgElevated = Color(0xFF1C1C28)
val AccentViolet = Color(0xFF7B5EA7)
val AccentGlow = Color(0xFFA67BDB)
val AccentRose = Color(0xFFE05C8A)
val AccentGold = Color(0xFFD4A843)
val TextPrimary = Color(0xFFF0EEF8)
val TextSecondary = Color(0xFF8B89A0)
val TextMuted = Color(0xFF4A4860)
val BorderSubtle = Color(0xFF2A2840)
val Success = Color(0xFF4CAF82)

private val SoulMintColors = darkColorScheme(
    primary = AccentViolet,
    secondary = AccentRose,
    tertiary = AccentGold,
    background = BgPrimary,
    surface = BgSurface,
    surfaceVariant = BgElevated,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderSubtle
)

@Composable
fun SoulMintTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoulMintColors,
        typography = SoulMintTypography,
        content = content
    )
}
