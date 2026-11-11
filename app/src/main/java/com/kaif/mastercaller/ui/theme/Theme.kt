package com.kaif.mastercaller.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Warm Night" palette.
 * - Neutrals are slightly warm (a hint of red/blue) instead of pure grey, so the dark UI feels soft, not "terminal".
 * - One warm primary (coral) for actions, plus three supporting hues used sparingly for meaning
 *   (lavender = voice, mint = success/ready, honey = attention). Saturation is kept moderate (no neon).
 * - Text is warm off-white with clearly separated secondary/muted steps.
 */
object McColors {
    // Neutrals (warm ink)
    val Ink = Color(0xFF0F1016)
    val Surface = Color(0xFF17181F)
    val Surface2 = Color(0xFF1F2029)
    val Surface3 = Color(0xFF292A35)
    val Outline = Color(0xFF33343F)

    // Primary: coral
    val Accent = Color(0xFFFF8F70)
    val AccentSoft = Color(0xFFFFC2B0)
    val AccentDeep = Color(0xFFC85E48)
    val AccentDark = Color(0xFF3A1F1A)

    // Supporting hues
    val Lavender = Color(0xFFB4A7FF)
    val Mint = Color(0xFF7FDCC0)
    val Honey = Color(0xFFF6C772)
    val Sky = Color(0xFF8CC8FF)

    // Text
    val TextPrimary = Color(0xFFF5F1EC)
    val TextSecondary = Color(0xFFADA9B4)
    val TextMuted = Color(0xFF75727F)

    // Semantics
    val Answer = Color(0xFF3CCB7F)
    val Reject = Color(0xFFF2545B)
    val Warning = Honey
}

private val scheme = darkColorScheme(
    primary = McColors.Accent,
    onPrimary = McColors.Ink,
    primaryContainer = McColors.AccentDark,
    onPrimaryContainer = McColors.AccentSoft,
    secondary = McColors.Lavender,
    onSecondary = McColors.Ink,
    secondaryContainer = McColors.Surface3,
    onSecondaryContainer = McColors.TextPrimary,
    tertiary = McColors.Mint,
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
    onError = Color.White,
)

private val base = Typography()

private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = base.labelMedium.copy(letterSpacing = 0.3.sp),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MasterCallerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
