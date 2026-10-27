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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
    this.scale(s).clickable(interactionSource = src, indication = null, enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
}

/** Superficie leggera: bordo sottile, angoli coerenti, senza ombre pesanti. */
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
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, if (highlight) J.Blue.copy(alpha = 0.16f) else J.Stroke, RoundedCornerShape(22.dp))
            .background(if (highlight) J.AccentSoft else J.Card)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = J.Text,
        modifier = modifier.padding(start = 2.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier
            .pressable(onClick, enabled)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) J.brand else Brush.linearGradient(listOf(J.CardHi, J.CardHi)))
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Color.White else J.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (enabled) Color.White else J.Muted, style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = J.Blue, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier
            .pressable(onClick, enabled)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, J.Blue.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .background(J.AccentSoft.copy(alpha = 0.45f))
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) color else J.Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (enabled) color else J.Muted, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) J.Blue else J.Text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .pressable(onClick)
            .clip(RoundedCornerShape(50))
            .background(if (selected) J.AccentSoft else J.Card)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Approved Jarvis identity: quiet at rest; restrained pulse while working or listening. */
@Composable
fun ArcReactor(size: Dp, active: Boolean = true, modifier: Modifier = Modifier, level: Float = 0f, feather: Boolean = true) {
    var pulse = 1f
    if (active) {
        val transition = rememberInfiniteTransition(label = "jarvisPulse")
        val p by transition.animateFloat(1f, 1.035f,
            infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "jarvisBreath")
        pulse = p
    }
    val voice by animateFloatAsState(level.coerceIn(0f, 1f), tween(120), label = "jarvisVoice")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(if (size <= 48.dp) com.jarvis.files.R.drawable.jarvis_symbol_brand else com.jarvis.files.R.drawable.jarvis_sphere_brand),
            null, Modifier.matchParentSize().graphicsLayer {
                scaleX = pulse + voice * 0.06f; scaleY = scaleX
            },
        )
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


