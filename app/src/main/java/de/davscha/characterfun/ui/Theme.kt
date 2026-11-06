package de.davscha.characterfun.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dunkles Design wie bei character.ai */
private val Colors = darkColorScheme(
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

@Composable
fun CharacterFunTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
