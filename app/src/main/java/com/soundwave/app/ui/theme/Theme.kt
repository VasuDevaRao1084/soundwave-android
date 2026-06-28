package com.soundwave.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SwPurple = Color(0xFF8B5CF6)
val SwPink = Color(0xFFF472B6)
val SwBg = Color(0xFF0A0A0F)
val SwSurface = Color(0xFF15151D)
val SwSurfaceLight = Color(0xFF1F1F29)
val SwTextSecondary = Color(0xFFA0A0AD)

private val SwColors = darkColorScheme(
    primary = SwPurple,
    secondary = SwPink,
    background = SwBg,
    surface = SwSurface,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun SoundWaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SwColors, content = content)
}
