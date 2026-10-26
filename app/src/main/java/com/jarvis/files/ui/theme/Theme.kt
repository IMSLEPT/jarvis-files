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

/** Carattere Inter (variabile): il più vicino a quello del design di riferimento. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(w: Int) = androidx.compose.ui.text.font.Font(
    com.jarvis.files.R.font.inter, FontWeight(w),
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w)),
)

val Inter = androidx.compose.ui.text.font.FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800), inter(900))

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
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceHi = Color(0xFFF0F2F7),
    hairline = Color(0x12000000),
    accent = Color(0xFFC44716),
    accentSoft = Color(0x1FFF6A2B),
    text = Color(0xFF12141C),
    muted = Color(0xFF6B7080),
    green = Color(0xFF16784C),
    amber = Color(0xFF986200),
    red = Color(0xFFBE3030),
    isDark = false,
)

private val DarkPalette = Palette(
    bg = Color(0xFF0B0C10),
    surface = Color(0xFF16181F),
    surfaceHi = Color(0xFF20232C),
    hairline = Color(0x1FFFFFFF),
    accent = Color(0xFFFF7A3D),
    accentSoft = Color(0x29FF7A3D),
    text = Color(0xFFF4F5F8),
    muted = Color(0xFF9A9FAD),
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

    // Colori delle categorie e del brand (come nel design di riferimento)
    val Info = Color(0xFF2F6BFF)
    val Navy = Color(0xFF1E3A8A)
    val Purple = Color(0xFF8B5CF6)
    val Pink = Color(0xFFFF4D8D)
    val Orange = Color(0xFFFF6A2B)
    val Peach = Color(0xFFFFF1E8)
    /** Pulsanti principali: blu → viola → arancio. */
    val brand = Brush.horizontalGradient(listOf(Color(0xFF1759C4), Color(0xFF6926B9), Color(0xFFB84413)))
    val background: Brush @Composable @ReadOnlyComposable get() = Brush.linearGradient(listOf(p.bg, p.bg))
}

private val base = Typography().let { t ->
    fun TextStyle.i() = copy(fontFamily = Inter)
    Typography(
        t.displayLarge.i(), t.displayMedium.i(), t.displaySmall.i(), t.headlineLarge.i(), t.headlineMedium.i(), t.headlineSmall.i(),
        t.titleLarge.i(), t.titleMedium.i(), t.titleSmall.i(), t.bodyLarge.i(), t.bodyMedium.i(), t.bodySmall.i(),
        t.labelLarge.i(), t.labelMedium.i(), t.labelSmall.i(),
    )
}
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
    labelSmall = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    // Tema chiaro fisso, identico al design di riferimento
    val pal = LightPalette
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

