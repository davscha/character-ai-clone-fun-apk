package de.davscha.characterfun.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Dunkles Design wie bei character.ai */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9B7BFF),
    onPrimary = Color.White,
    secondary = Color(0xFFFFD54F),
    background = Color(0xFF18181B),
    onBackground = Color(0xFFECECEE),
    surface = Color(0xFF202024),
    onSurface = Color(0xFFECECEE),
    surfaceVariant = Color(0xFF2A2A30),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF3F3F46),
    error = Color(0xFFFF6B6B),
)

/** Helles Design mit denselben Akzentfarben */
private val LightColors = lightColorScheme(
    primary = Color(0xFF6A4BD8),
    onPrimary = Color.White,
    secondary = Color(0xFFB7860B),
    background = Color(0xFFF6F5F9),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFECEAF2),
    onSurfaceVariant = Color(0xFF5C5A66),
    outline = Color(0xFFD3D0DC),
    error = Color(0xFFC62828),
)

/** [mode]: "dunkel", "hell" oder "system"; [fontScale]: Schriftgröße (1 = normal) */
@Composable
fun CharacterFunTheme(mode: String = "dunkel", fontScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (mode) { "hell" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * fontScale)) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
