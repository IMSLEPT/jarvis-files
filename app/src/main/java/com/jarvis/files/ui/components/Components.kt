package com.jarvis.files.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jarvis.files.core.FileCategory
import com.jarvis.files.ui.theme.J
import java.io.File

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    if (highlight) listOf(Color(0xFF16336B), Color(0xFF0E1C3A)) else listOf(J.Card, Color(0xFF0B1730))
                )
            )
            .border(1.dp, if (highlight) J.Blue.copy(alpha = 0.6f) else J.Stroke, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = J.Cyan,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) J.accent else Brush.linearGradient(listOf(J.CardHi, J.CardHi)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Color.White else J.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (enabled) Color.White else J.Muted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = J.Cyan, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.White else J.Text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) J.accent else Brush.linearGradient(listOf(J.CardHi, J.Card)))
            .border(1.dp, if (selected) Color.Transparent else J.Stroke, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** Il "reattore": anelli animati, usato come logo e indicatore di attività. */
@Composable
fun ArcReactor(size: Dp, active: Boolean = true, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "reactor")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 2400 else 12000, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(J.Electric.copy(alpha = 0.35f * pulse), Color.Transparent), radius = r), r)
        rotate(rot) {
            drawArc(J.Blue, 0f, 250f, false, style = Stroke(r * 0.07f, cap = StrokeCap.Round),
                topLeft = Offset(r * 0.08f, r * 0.08f), size = androidx.compose.ui.geometry.Size(r * 1.84f, r * 1.84f))
        }
        rotate(-rot * 1.6f) {
            drawArc(J.Cyan, 40f, 200f, false, style = Stroke(r * 0.05f, cap = StrokeCap.Round),
                topLeft = Offset(r * 0.32f, r * 0.32f), size = androidx.compose.ui.geometry.Size(r * 1.36f, r * 1.36f))
        }
        drawCircle(J.Electric.copy(alpha = pulse), r * 0.22f)
        drawCircle(Color.White, r * 0.1f)
    }
}

/** Anello di occupazione memoria. */
@Composable
fun UsageRing(fraction: Float, size: Dp, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.minDimension * 0.08f
            drawArc(J.CardHi, 135f, 270f, false, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(listOf(J.Electric, J.Blue, if (fraction > 0.9f) J.Red else J.Blue)),
                135f, 270f * fraction.coerceIn(0f, 1f), false, style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        center()
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = J.Cyan) {
    GlassCard(modifier, padding = PaddingValues(14.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1)
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
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.size(size).clip(shape).background(J.CardHi), contentAlignment = Alignment.Center) {
        if (cat == FileCategory.IMAGE || cat == FileCategory.VIDEO) {
            AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(iconFor(cat, file.isDirectory), null, tint = J.Cyan, modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun LinearMeter(fraction: Float, modifier: Modifier = Modifier, color: Color = J.Blue) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(J.CardHi)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp))
            .background(Brush.horizontalGradient(listOf(color, J.Electric))))
    }
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ArcReactor(72.dp, active = false)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = J.Text)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = J.Muted)
    }
}
