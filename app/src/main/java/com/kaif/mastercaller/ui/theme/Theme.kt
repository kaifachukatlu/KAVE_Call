package com.kaif.mastercaller.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaif.mastercaller.R

/**
 * KAVÉ design system. "Beyond the ordinary."
 * Black and graphite dominate; champagne is reserved for selected states, key icons and primary actions;
 * warm ivory carries text. No neon, no purple/blue gradients.
 */
object McColors {
    // Foundation
    val Ink = Color(0xFF090A0B) // Obsidian Black
    val Surface = Color(0xFF151718) // Deep Graphite
    val Surface2 = Color(0xFF1D2020) // Elevated Surface
    val Surface3 = Color(0xFF262928) // Pressed / control wells
    val Outline = Color(0xFF343330) // Subtle Border

    // Champagne
    val Accent = Color(0xFFB9A27B) // Champagne Gold
    val AccentSoft = Color(0xFFD3C2A2) // Soft Champagne
    val AccentDeep = Color(0xFF8C7856)
    val AccentDark = Color(0xFF2A251D)

    // Text
    val TextPrimary = Color(0xFFF1EDE3) // Warm Ivory
    val TextSecondary = Color(0xFFB9B6AD)
    val TextMuted = Color(0xFF96958E) // Muted Text

    // Semantics
    val Success = Color(0xFF9EBE9C)
    val Warning = Color(0xFFD3C2A2)
    val Reject = Color(0xFFC77B73) // Danger Red
    val Answer = Accent // Accept is champagne
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
/** Manrope (variable): clean, modern interface text. Bundled, works offline. */
val Manrope = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { w ->
        Font(R.font.manrope, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
/** Cormorant Garamond (variable): refined display serif for brand moments and large names only. */
val Cormorant = FontFamily(
    listOf(300, 400, 500, 600, 700).map { w ->
        Font(R.font.cormorant_garamond, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

private val scheme = darkColorScheme(
    primary = McColors.Accent,
    onPrimary = McColors.Ink,
    primaryContainer = McColors.AccentDark,
    onPrimaryContainer = McColors.AccentSoft,
    secondary = McColors.AccentSoft,
    onSecondary = McColors.Ink,
    secondaryContainer = McColors.Surface3,
    onSecondaryContainer = McColors.TextPrimary,
    tertiary = McColors.Success,
    onTertiary = McColors.Ink,
    background = McColors.Ink,
    onBackground = McColors.TextPrimary,
    surface = McColors.Surface,
    onSurface = McColors.TextPrimary,
    surfaceVariant = McColors.Surface2,
    onSurfaceVariant = McColors.TextSecondary,
    surfaceContainerLowest = McColors.Ink,
    surfaceContainerLow = McColors.Surface,
    surfaceContainer = McColors.Surface2,
    surfaceContainerHigh = McColors.Surface3,
    surfaceContainerHighest = McColors.Surface3,
    outline = McColors.Outline,
    outlineVariant = McColors.Outline,
    error = McColors.Reject,
    onError = McColors.Ink,
)

private fun ui(size: Int, weight: Int, line: Int, spacing: Float = 0f) = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

private fun display(size: Int, weight: Int, line: Int, spacing: Float = 0f) = TextStyle(
    fontFamily = Cormorant,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

private val typography = Typography(
    // Display serif: brand moments, caller names, screen titles.
    displayLarge = display(56, 500, 60, -0.5f),
    displayMedium = display(44, 500, 48, -0.3f),
    displaySmall = display(36, 500, 40),
    headlineLarge = display(34, 500, 40),
    headlineMedium = display(30, 500, 36),
    // Interface sans: everything functional.
    headlineSmall = ui(20, 600, 28),
    titleLarge = ui(18, 600, 26),
    titleMedium = ui(16, 600, 24),
    titleSmall = ui(15, 600, 22),
    bodyLarge = ui(16, 400, 24),
    bodyMedium = ui(14, 400, 21),
    bodySmall = ui(13, 400, 19, 0.1f),
    labelLarge = ui(14, 600, 20, 0.6f),
    labelMedium = ui(12, 500, 16, 0.4f),
    // Refined uppercase labels (callers pass uppercase text).
    labelSmall = ui(11, 600, 16, 2.2f),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MasterCallerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
