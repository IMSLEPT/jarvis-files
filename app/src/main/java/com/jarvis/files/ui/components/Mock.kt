package com.jarvis.files.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.jarvis.files.ui.theme.Inter

/**
 * Misure del design di riferimento (largo 412 dp). Ogni valore è scritto in "dp del mockup"
 * e viene scalato sulla larghezza reale del telefono, così le proporzioni restano identiche.
 * I testi ignorano la scala caratteri di sistema per non rompere l'impaginazione fissa.
 */
class Mk(val u: Float, val density: Density) {
    val Float.d: Dp get() = (this * u).dp
    val Int.d: Dp get() = (this * u).dp
    fun sp(v: Float): TextUnit = with(density) { (v * u).dp.toSp() }
}

@Composable
fun rememberMk(): Mk {
    val w = LocalConfiguration.current.screenWidthDp
    val d = LocalDensity.current
    return remember(w, d) { Mk(w / 412f, d) }
}

object MkColor {
    val Bg = Color(0xFFF7F9FC)
    val Ink = Color(0xFF0A0D18)
    val Slate = Color(0xFF3A4560)
    val SlateLight = Color(0xFF5B6680)
    val NavInk = Color(0xFF1B2A6B)
    val Orange = Color(0xFFFF4A14)
    val Peach = Color(0xFFFEE7E2)
}

/** Distanza tra il bordo alto della riga di testo e il bordo alto delle maiuscole (Inter). */
private const val CAP_OFFSET = 0.2417f

/**
 * Testo posizionato come nel mockup: [x] = bordo sinistro, [capTop] = bordo alto delle maiuscole.
 * Con [width] e [align] si può centrare o allineare a destra.
 */
@Composable
fun Mk.T(
    text: String, x: Float, capTop: Float, size: Float, weight: Int, color: Color,
    width: Float? = null, align: TextAlign = TextAlign.Start, lineHeight: Float? = null, maxLines: Int = 1,
    letterSpacing: Float = 0f, fit: Float? = null, modifier: Modifier = Modifier,
) {
    // Testi più lunghi del mockup (es. "Buon pomeriggio,") si rimpiccioliscono per non finire sotto Jarvis
    val measurer = rememberTextMeasurer()
    val sz = if (fit == null) size else remember(text, size, fit, u) {
        val wPx = measurer.measure(text, style(size, weight, color)).size.width
        val maxPx = with(density) { (fit * u).dp.toPx() }
        if (wPx > maxPx) size * maxPx / wPx else size
    }
    var m = modifier.offset(x.d, (capTop - CAP_OFFSET * sz).d)
    if (width != null) m = m.width(width.d)
    Text(
        text, m,
        style = TextStyle(
            fontFamily = Inter, fontSize = sp(sz), fontWeight = FontWeight(weight), color = color,
            textAlign = align, letterSpacing = sp(letterSpacing),
            lineHeight = lineHeight?.let { sp(it) } ?: TextUnit.Unspecified,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.Both),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        maxLines = maxLines, softWrap = maxLines > 1, overflow = TextOverflow.Ellipsis,
    )
}

/** Stile di testo con le stesse regole di [T], per i testi dentro righe/colonne. */
fun Mk.style(size: Float, weight: Int, color: Color, lineHeight: Float? = null) = TextStyle(
    fontFamily = Inter, fontSize = sp(size), fontWeight = FontWeight(weight), color = color,
    lineHeight = lineHeight?.let { sp(it) } ?: TextUnit.Unspecified,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.Both),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
