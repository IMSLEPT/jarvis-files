package com.jarvis.files.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palette "Minimal premium": neutri morbidi + un solo colore d'accento. Segue tema chiaro/scuro. */
data class Palette(
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val hairline: Color,
    val accent: Color,
    val accentSoft: Color,
    val text: Color,
    val muted: Color,
    val green: Color,
    val amber: Color,
    val red: Color,
    val isDark: Boolean,
)

private val LightPalette = Palette(
    bg = Color(0xFFF5F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceHi = Color(0xFFEDEDF0),
    hairline = Color(0x14000000),
    accent = Color(0xFF0A6CFF),
    accentSoft = Color(0x1A0A6CFF),
    text = Color(0xFF111114),
    muted = Color(0xFF6E6E76),
    green = Color(0xFF1FA463),
    amber = Color(0xFFD98A00),
    red = Color(0xFFE5484D),
    isDark = false,
)

private val DarkPalette = Palette(
    bg = Color(0xFF000000),
    surface = Color(0xFF141416),
    surfaceHi = Color(0xFF1F1F23),
    hairline = Color(0x1FFFFFFF),
    accent = Color(0xFF3B8BFF),
    accentSoft = Color(0x293B8BFF),
    text = Color(0xFFF5F5F7),
    muted = Color(0xFF9A9AA2),
    green = Color(0xFF3CCB7F),
    amber = Color(0xFFF5B342),
    red = Color(0xFFFF6369),
    isDark = true,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/**
 * Accesso rapido ai colori dentro le composable (es. `J.Text`).
 * I nomi storici sono mantenuti: Card = superficie, CardHi = superficie tonale, Blue/Cyan/Electric = accento.
 */
object J {
    val p: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current
    val Bg: Color @Composable @ReadOnlyComposable get() = p.bg
    val Bg2: Color @Composable @ReadOnlyComposable get() = p.bg
    val Card: Color @Composable @ReadOnlyComposable get() = p.surface
    val CardHi: Color @Composable @ReadOnlyComposable get() = p.surfaceHi
    val Stroke: Color @Composable @ReadOnlyComposable get() = p.hairline
    val Blue: Color @Composable @ReadOnlyComposable get() = p.accent
    val Cyan: Color @Composable @ReadOnlyComposable get() = p.accent
    val Electric: Color @Composable @ReadOnlyComposable get() = p.accent
    val AccentSoft: Color @Composable @ReadOnlyComposable get() = p.accentSoft
    val Text: Color @Composable @ReadOnlyComposable get() = p.text
    val Muted: Color @Composable @ReadOnlyComposable get() = p.muted
    val Green: Color @Composable @ReadOnlyComposable get() = p.green
    val Amber: Color @Composable @ReadOnlyComposable get() = p.amber
    val Red: Color @Composable @ReadOnlyComposable get() = p.red
    val accent: Brush @Composable @ReadOnlyComposable get() = Brush.linearGradient(listOf(p.accent, p.accent))
    val background: Brush @Composable @ReadOnlyComposable get() = Brush.linearGradient(listOf(p.bg, p.bg))
}

private val base = Typography()
private val typo = Typography(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    val pal = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    val scheme = if (pal.isDark) darkColorScheme(
        primary = pal.accent, onPrimary = Color.White, background = pal.bg, onBackground = pal.text,
        surface = pal.surface, onSurface = pal.text, surfaceVariant = pal.surfaceHi, onSurfaceVariant = pal.muted,
        surfaceContainer = pal.surface, surfaceContainerHigh = pal.surfaceHi, surfaceContainerHighest = pal.surfaceHi,
        surfaceContainerLow = pal.surface, outline = pal.hairline, outlineVariant = pal.hairline, error = pal.red,
        secondaryContainer = pal.accentSoft, onSecondaryContainer = pal.text,
    ) else lightColorScheme(
        primary = pal.accent, onPrimary = Color.White, background = pal.bg, onBackground = pal.text,
        surface = pal.surface, onSurface = pal.text, surfaceVariant = pal.surfaceHi, onSurfaceVariant = pal.muted,
        surfaceContainer = pal.surface, surfaceContainerHigh = pal.surfaceHi, surfaceContainerHighest = pal.surfaceHi,
        surfaceContainerLow = pal.surface, outline = pal.hairline, outlineVariant = pal.hairline, error = pal.red,
        secondaryContainer = pal.accentSoft, onSecondaryContainer = pal.text,
    )
    CompositionLocalProvider(LocalPalette provides pal) {
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}
