package com.jarvis.files.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.files.ui.theme.J

/** Logo testuale: JARVIS con tratto arancione e FILES spaziato. */
@Composable
fun JarvisLogo(modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFFF3D2E), J.Orange))))
            Spacer(Modifier.width(3.dp))
            Text("JARVIS", color = J.Text, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }
        Text("F  I  L  E  S", color = J.Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp,
            modifier = Modifier.padding(start = 30.dp))
    }
}

/** Anello della memoria a segmenti colorati, animato, con i numeri al centro. */
@Composable
fun StorageDonut(
    segments: List<Pair<Float, Brush>>,
    freeGb: String,
    totalLabel: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val grow by animateFloatAsState(if (started) 1f else 0f, tween(1300, easing = FastOutSlowInEasing), label = "donut")
    val track = J.CardHi
    val surface = J.Card
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.minDimension * 0.13f
            val inset = sw / 2 + this.size.minDimension * 0.02f
            val tl = Offset(inset, inset)
            val sz = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(sw))
            var start = -90f
            segments.forEach { (frac, brush) ->
                val sweep = 360f * frac * grow
                if (sweep > 2f) drawArc(brush, start + 1.5f, sweep - 3f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Butt))
                start += 360f * frac * grow
            }
            // disco interno con leggera ombra
            val inner = this.size.minDimension / 2 - sw - this.size.minDimension * 0.05f
            drawCircle(Color(0x10000000), inner + 3f, center)
            drawCircle(surface, inner, center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(freeGb, color = J.Text, fontSize = (size.value * 0.2f).sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, maxLines = 1)
            Text("GB liberi", color = J.Text, fontSize = (size.value * 0.09f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(totalLabel, color = J.Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Icon(Icons.Filled.SdStorage, null, tint = J.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

/** Piccola didascalia con pallino e linea, ai lati dell'anello. */
@Composable
fun Callout(big: String, small: String, color: Color, alignEnd: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(big, color = J.Text, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
        Text(small, color = J.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!alignEnd) Dot(color, 7.dp)
            Box(Modifier.width(30.dp).height(1.5.dp).background(color.copy(alpha = 0.7f)))
            if (alignEnd) Dot(color, 7.dp)
        }
    }
}

/** Card di una categoria (Video, Immagini...) con icona colorata e barra. */
@Composable
fun CategoryCard(
    icon: ImageVector, title: String, size: String, fraction: Float, colors: List<Color>,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val anim by animateFloatAsState(fraction.coerceIn(0.02f, 1f), tween(1000, easing = FastOutSlowInEasing), label = "cat")
    GlassCard(modifier, onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(colors.first().copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = J.Text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(size, color = J.Text, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(J.CardHi)) {
                    Box(Modifier.fillMaxWidth(anim).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(colors)))
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(26.dp).clip(CircleShape).background(colors.first().copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.first(), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Pallini dell'indicatore di pagina. */
@Composable
fun PagerDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 10.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(if (i == current) 9.dp else 8.dp).clip(CircleShape).background(if (i == current) J.Orange else J.Muted.copy(alpha = 0.35f)))
        }
    }
}

/** Card "Suggerimento di Jarvis" con illustrazione di cartelle e scopa. */
@Composable
fun SuggestionCard(big: String, title: String, text: String, button: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dark = J.p.isDark
    Box(
        modifier.fillMaxWidth()
            .shadow(if (dark) 0.dp else 12.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x22FF6A2B), spotColor = Color(0x22FF6A2B))
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (dark) Brush.linearGradient(listOf(Color(0xFF2A1D18), Color(0xFF1B1D25)))
                else Brush.linearGradient(listOf(Color(0xFFFFF4EC), Color(0xFFFFFFFF), Color(0xFFFFEFE6)))
            )
            .pressable(onClick)
            .padding(20.dp),
    ) {
        // illustrazione a destra
        Canvas(Modifier.align(Alignment.CenterEnd).size(120.dp)) {
            val w = size.width; val h = size.height
            val cards = listOf(
                listOf(Color(0xFF8EA2FF), Color(0xFF6C7CF5)),
                listOf(Color(0xFFB79CFF), Color(0xFF8B5CF6)),
                listOf(Color(0xFFFFC08A), Color(0xFFFF8A3D)),
            )
            cards.forEachIndexed { i, col ->
                val x = w * (0.18f + i * 0.1f); val y = h * (0.12f + i * 0.08f)
                drawRoundRect(Brush.linearGradient(col, start = Offset(x, y), end = Offset(x + w * 0.5f, y + h * 0.6f)),
                    topLeft = Offset(x, y), size = Size(w * 0.5f, h * 0.62f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
            }
            drawCircle(Color(0x33FF6A2B), w * 0.25f, Offset(w * 0.72f, h * 0.7f))
            drawCircle(Color.White, w * 0.2f, Offset(w * 0.72f, h * 0.7f))
        }
        Icon(Icons.Filled.CleaningServices, null, tint = J.Text, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp, top = 46.dp).size(26.dp))

        Column(Modifier.fillMaxWidth(0.68f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦", color = J.Orange, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text("Suggerimento di Jarvis", color = J.Orange, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 30.sp, fontWeight = FontWeight.Black)) { append(big) }
                    withStyle(SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold)) { append(" $title") }
                },
                color = J.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(text, color = J.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            GradientButton(button, icon = Icons.Filled.CleaningServices, onClick = onClick)
        }
    }
}
