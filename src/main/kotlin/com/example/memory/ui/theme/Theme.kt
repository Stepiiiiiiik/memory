package com.example.memory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Terracotta,
    onSecondary = Color.White,
    background = PaperBackground,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = Ink,
    outline = TealDeep,
)

private val DarkColors = darkColorScheme(
    primary = TealSoft,
    onPrimary = Color(0xFF0E2420),
    secondary = TerracottaSoft,
    onSecondary = Color(0xFF2A1407),
    background = NightBackground,
    onBackground = Bone,
    surface = NightSurface,
    onSurface = Bone,
    surfaceVariant = Slate,
    onSurfaceVariant = Bone,
    outline = TealSoftDeep,
)

/**
 * Pair of card-back gradient colours for the current theme, exposed so the
 * card composable doesn't have to branch on the theme itself.
 */
data class CardBackColors(val top: Color, val bottom: Color, val emblem: Color)

val cardBackColorsLight = CardBackColors(top = Teal, bottom = TealDeep, emblem = Color(0x33FFFFFF))
val cardBackColorsDark = CardBackColors(top = TealSoftDeep, bottom = Color(0xFF26403B), emblem = Color(0x33FFFFFF))

@Composable
fun MemoryMatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
