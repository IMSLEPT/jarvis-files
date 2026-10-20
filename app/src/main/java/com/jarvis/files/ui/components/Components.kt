package com.jarvis.files.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jarvis.files.core.FileCategory
import com.jarvis.files.ui.theme.J
import java.io.File

/** Leggero rimpicciolimento alla pressione: dà un feedback "fisico" a tutto ciò che si tocca. */
fun Modifier.pressable(onClick: (() -> Unit)?, enabled: Boolean = true): Modifier = composed {
    if (onClick == null) return@composed this
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "press")
    this.scale(s).clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Scheda: superficie piena, angoli morbidi, nessun bordo. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .pressable(onClick)
            .shadow(if (J.p.isDark) 0.dp else 10.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x22000000), spotColor = Color(0x1A1E2A4A))
            .clip(RoundedCornerShape(24.dp))
            .background(if (highlight) J.AccentSoft else J.Card)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = J.Muted,
        modifier = modifier.padding(start = 4.dp, top = 26.dp, bottom = 10.dp),
    )
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier
            .pressable(onClick, enabled)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) J.brand else Brush.linearGradient(listOf(J.CardHi, J.CardHi)))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Color.White else J.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (enabled) Color.White else J.Muted, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = J.Blue, onClick: () -> Unit) {
    Row(
        modifier
            .pressable(onClick)
            .clip(RoundedCornerShape(50))
            .background(J.CardHi)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.White else J.Text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .pressable(onClick)
            .clip(RoundedCornerShape(50))
            .background(if (selected) J.Blue else J.Card)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/**
 * Jarvis: sfera metallica con pannelli che ruotano, anello arancione luminoso, arco blu,
 * stella centrale e particelle in orbita. Sempre in movimento; più veloce quando [active].
 * [level] (0-1) fa "pulsare" la sfera con la voce durante l'ascolto.
 */
@Composable
fun ArcReactor(size: Dp, active: Boolean = true, modifier: Modifier = Modifier, level: Float = 0f) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 3600 else 9000, easing = LinearEasing)), label = "spin")
    val slow by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 9000 else 24000, easing = LinearEasing)), label = "slow")
    val orbit by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 2600 else 6000, easing = LinearEasing)), label = "orbit")
    val pulse by t.animateFloat(0.82f, 1f, infiniteRepeatable(tween(if (active) 550 else 1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val lvl by animateFloatAsState(level.coerceIn(0f, 1f), tween(120), label = "lvl")
    val dark = J.p.isDark
    val detailed = size >= 56.dp
    val orange = Color(0xFFFF6A2B)
    val gold = Color(0xFFFFB347)
    val blue = Color(0xFF3B82F6)
    val metalHi = if (dark) Color(0xFF4A505E) else Color(0xFF3A3F4B)
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = center
        val sr = r * (if (detailed) 0.56f else 0.78f) * (1f + lvl * 0.08f)
        // aloni
        drawCircle(Brush.radialGradient(listOf(orange.copy(alpha = 0.28f * pulse + lvl * 0.3f), Color.Transparent), center = c, radius = r), r, c)
        if (detailed) drawCircle(Brush.radialGradient(listOf(blue.copy(alpha = 0.16f), Color.Transparent), center = Offset(c.x - r * 0.3f, c.y + r * 0.2f), radius = r * 0.8f), r * 0.8f, Offset(c.x - r * 0.3f, c.y + r * 0.2f))

        fun particle(i: Int, front: Boolean) {
            val a = Math.toRadians((orbit * (1f + i * 0.17f) + i * 72f).toDouble())
            val depth = kotlin.math.sin(a).toFloat()
            if ((depth >= 0f) != front) return
            val ex = kotlin.math.cos(a).toFloat() * r * 0.95f
            val ey = depth * r * 0.32f
            // ellisse inclinata di -20°
            val ang = Math.toRadians(-20.0)
            val x = c.x + (ex * kotlin.math.cos(ang) - ey * kotlin.math.sin(ang)).toFloat()
            val y = c.y + (ex * kotlin.math.sin(ang) + ey * kotlin.math.cos(ang)).toFloat()
            val col = if (i % 2 == 0) blue else orange
            val pr = r * (0.035f + 0.025f * (depth + 1f) / 2f)
            drawCircle(col.copy(alpha = 0.35f), pr * 2.2f, Offset(x, y))
            drawCircle(col, pr, Offset(x, y))
        }
        if (detailed) {
            withTransform({ rotate(-20f, c) }) {
                drawOval(
                    (if (dark) Color.White else Color(0xFF9AA3B5)).copy(alpha = 0.45f),
                    topLeft = Offset(c.x - r * 0.95f, c.y - r * 0.32f),
                    size = androidx.compose.ui.geometry.Size(r * 1.9f, r * 0.64f),
                    style = Stroke(r * 0.012f),
                )
            }
            for (i in 0 until 5) particle(i, front = false)
        }

        // corpo della sfera
        drawCircle(
            Brush.radialGradient(listOf(metalHi, Color(0xFF14161C)), center = Offset(c.x - sr * 0.35f, c.y - sr * 0.35f), radius = sr * 1.5f),
            sr, c,
        )
        // pannelli metallici che ruotano (due fasce in senso opposto)
        val ring1 = sr * 0.84f
        withTransform({ rotate(spin, c) }) {
            for (i in 0 until 12) drawArc(
                Color(0xFF6B7282).copy(alpha = 0.75f), i * 30f + 3f, 22f, false,
                Offset(c.x - ring1, c.y - ring1), androidx.compose.ui.geometry.Size(ring1 * 2, ring1 * 2), style = Stroke(sr * 0.13f),
            )
        }
        val ring2 = sr * 0.62f
        withTransform({ rotate(-spin * 0.7f, c) }) {
            for (i in 0 until 8) drawArc(
                Color(0xFF2C313C), i * 45f + 4f, 34f, false,
                Offset(c.x - ring2, c.y - ring2), androidx.compose.ui.geometry.Size(ring2 * 2, ring2 * 2), style = Stroke(sr * 0.1f),
            )
        }
        // anello arancione luminoso
        drawCircle(orange.copy(alpha = 0.30f * pulse), sr * 1.02f, c, style = Stroke(sr * 0.2f))
        withTransform({ rotate(slow, c) }) {
            drawCircle(Brush.sweepGradient(listOf(orange, gold, orange, Color(0xFFFF3D2E), orange), center = c), sr * 1.0f, c, style = Stroke(sr * 0.07f))
        }
        // arco blu sul lato sinistro
        val rb = sr * 1.16f
        withTransform({ rotate(-slow * 0.5f, c) }) {
            drawArc(blue.copy(alpha = 0.9f), 110f, 120f, false, Offset(c.x - rb, c.y - rb), androidx.compose.ui.geometry.Size(rb * 2, rb * 2),
                style = Stroke(sr * 0.05f, cap = StrokeCap.Round))
        }
        // anello interno e stella a tre punte
        drawCircle(orange.copy(alpha = 0.35f * pulse), sr * 0.42f, c, style = Stroke(sr * 0.14f))
        drawCircle(gold, sr * 0.42f, c, style = Stroke(sr * 0.04f))
        withTransform({ rotate(spin * 0.35f, c) }) {
            for (k in 0 until 3) {
                val ang = Math.toRadians((k * 120 - 90).toDouble())
                val dx = kotlin.math.cos(ang).toFloat(); val dy = kotlin.math.sin(ang).toFloat()
                val tip = Offset(c.x + dx * sr * 0.40f, c.y + dy * sr * 0.40f)
                val w = sr * 0.07f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x - dy * w, c.y + dx * w); lineTo(tip.x, tip.y); lineTo(c.x + dy * w, c.y - dx * w); close()
                }
                drawPath(path, Brush.linearGradient(listOf(Color.White, gold, orange), start = c, end = tip))
            }
        }
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center = c, radius = sr * 0.18f * (0.9f + 0.3f * pulse)), sr * 0.18f, c)
        // riflesso: effetto vetro
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.30f), Color.Transparent), center = Offset(c.x - sr * 0.4f, c.y - sr * 0.45f), radius = sr * 0.55f),
            sr * 0.55f, Offset(c.x - sr * 0.4f, c.y - sr * 0.45f),
        )
        if (detailed) for (i in 0 until 5) particle(i, front = true)
    }
}

/** Anello sottile con riempimento animato. */
@Composable
fun UsageRing(fraction: Float, size: Dp, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1200, easing = FastOutSlowInEasing), label = "ring")
    val track = J.CardHi
    val col = if (fraction > 0.9f) J.Red else J.Blue
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.minDimension * 0.07f
            drawArc(track, -90f, 360f, false, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(col, -90f, 360f * anim, false, style = Stroke(sw, cap = StrokeCap.Round))
        }
        center()
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = J.Text) {
    GlassCard(modifier, padding = PaddingValues(16.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = J.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun iconFor(cat: FileCategory, isDir: Boolean = false): ImageVector = when {
    isDir -> Icons.Filled.Folder
    cat == FileCategory.IMAGE -> Icons.Filled.Image
    cat == FileCategory.VIDEO -> Icons.Filled.Movie
    cat == FileCategory.AUDIO -> Icons.Filled.AudioFile
    cat == FileCategory.DOCUMENT -> Icons.Filled.Description
    cat == FileCategory.ARCHIVE -> Icons.Filled.FolderZip
    cat == FileCategory.APK -> Icons.Filled.Android
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/** Miniatura: anteprima reale per foto/video, icona per il resto. */
@Composable
fun FileThumb(file: File, size: Dp, modifier: Modifier = Modifier) {
    val cat = FileCategory.of(file.extension.lowercase())
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(J.CardHi), contentAlignment = Alignment.Center) {
        if (cat == FileCategory.IMAGE || cat == FileCategory.VIDEO) {
            AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(iconFor(cat, file.isDirectory), null, tint = J.Blue, modifier = Modifier.size(size * 0.46f))
        }
    }
}

/** Icona in un cerchio tonale (usata nelle liste). */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = J.Blue, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun LinearMeter(fraction: Float, modifier: Modifier = Modifier, color: Color = J.Blue) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "meter")
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(J.CardHi)) {
        Box(Modifier.fillMaxWidth(anim).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

/** Barra a segmenti (es. spazio per categoria), animata. */
@Composable
fun StackedBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    var started by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val grow by animateFloatAsState(if (started) 1f else 0f, tween(1100, easing = FastOutSlowInEasing), label = "bar")
    val track = J.CardHi
    Canvas(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2))) {
        drawRect(track)
        var x = 0f
        parts.forEach { (f, c) ->
            val w = size.width * f * grow
            drawRect(c, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size((w - 2f).coerceAtLeast(0f), size.height))
            x += w
        }
    }
}

/** Numero che "scorre" fino al valore finale. */
@Composable
fun AnimatedNumber(value: Float, format: (Float) -> String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(value) { target = value }
    val v by animateFloatAsState(target, tween(1000, easing = FastOutSlowInEasing), label = "num")
    Text(format(v), style = style, color = color, modifier = modifier)
}

/** Tre puntini che saltellano: Jarvis sta pensando. */
@Composable
fun TypingDots(color: Color = J.Muted) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, delayMillis = i * 160), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(7.dp).scale(0.7f + a * 0.3f).clip(CircleShape).background(color.copy(alpha = a)))
        }
    }
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ArcReactor(64.dp, active = false)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = J.Text)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = J.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
