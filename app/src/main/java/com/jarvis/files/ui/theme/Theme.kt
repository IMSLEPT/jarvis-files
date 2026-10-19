package com.jarvis.files.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palette "Blu elettrico": sfondo notte, accenti azzurro reattore. */
object J {
    val Bg = Color(0xFF050B18)
    val Bg2 = Color(0xFF0A1630)
    val Card = Color(0xFF0E1C3A)
    val CardHi = Color(0xFF13264D)
    val Stroke = Color(0x332E8BFF)
    val Blue = Color(0xFF2E8BFF)
    val Cyan = Color(0xFF6FD3FF)
    val Electric = Color(0xFF00E5FF)
    val Text = Color(0xFFE8F1FF)
    val Muted = Color(0xFF8DA2C8)
    val Green = Color(0xFF3DDC97)
    val Amber = Color(0xFFFFC857)
    val Red = Color(0xFFFF5C7A)

    val accent = Brush.linearGradient(listOf(Blue, Electric))
    val background = Brush.verticalGradient(listOf(Bg2, Bg, Bg))
}

private val scheme = darkColorScheme(
    primary = J.Blue,
    onPrimary = Color.White,
    primaryContainer = J.CardHi,
    onPrimaryContainer = J.Text,
    secondary = J.Cyan,
    onSecondary = J.Bg,
    tertiary = J.Electric,
    background = J.Bg,
    onBackground = J.Text,
    surface = J.Card,
    onSurface = J.Text,
    surfaceVariant = J.CardHi,
    onSurfaceVariant = J.Muted,
    surfaceContainer = J.Card,
    surfaceContainerHigh = J.CardHi,
    surfaceContainerHighest = J.CardHi,
    surfaceContainerLow = J.Bg2,
    outline = J.Stroke,
    outlineVariant = J.Stroke,
    error = J.Red,
)

private val base = Typography()
private val typo = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.2.sp),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typo, content = content)
}
