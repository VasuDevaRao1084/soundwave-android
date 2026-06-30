package com.soundwave.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Palette: "Frequency" ──────────────────────────────────────────────────
// A near-black warm base (not pure #000, which reads flat on OLED) paired
// with an electric violet that's blue-shifted away from the generic
// purple-gradient-on-black look most AI-built music apps default to.
val SwBg = Color(0xFF0B0B10)
val SwSurface = Color(0xFF1A1820)
val SwSurfaceLight = Color(0xFF2A2735)
val SwPurple = Color(0xFF7C5CFF)       // electric violet — primary
val SwPink = Color(0xFFFF6B9D)         // warm coral-pink — secondary, used sparingly
val SwTextSecondary = Color(0xFF6B6478)
val SwTextTertiary = Color(0xFF4A4555)

private val SwColors = darkColorScheme(
    primary = SwPurple,
    secondary = SwPink,
    background = SwBg,
    surface = SwSurface,
    onBackground = Color.White,
    onSurface = Color.White
)

// ── Typography ────────────────────────────────────────────────────────────
// A tight, confident type scale: bold display weights with slightly
// negative letter-spacing for headlines (a more editorial, less "default
// system font" feel), and a calmer scale for body/metadata text.
private val SwTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.5.sp)
)

@Composable
fun SoundWaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SwColors, typography = SwTypography, content = content)
}
