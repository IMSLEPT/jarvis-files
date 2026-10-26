package com.jarvis.files.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.files.R
import com.jarvis.files.core.FileCategory
import com.jarvis.files.core.FileIndex
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.Prefs
import com.jarvis.files.core.Storage
import com.jarvis.files.ui.HomeTip
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.Tab
import com.jarvis.files.ui.components.ArcReactor
import com.jarvis.files.ui.components.JarvisBrand
import com.jarvis.files.ui.components.GhostButton
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.IconBadge
import com.jarvis.files.ui.components.Mk
import com.jarvis.files.ui.components.MkColor
import com.jarvis.files.ui.components.PlanCard
import com.jarvis.files.ui.components.SectionTitle
import com.jarvis.files.ui.components.T
import com.jarvis.files.ui.components.pressable
import com.jarvis.files.ui.components.rememberMk
import com.jarvis.files.ui.components.style
import com.jarvis.files.ui.theme.J
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Aspetto di ogni categoria: icona e colori (come nel design di riferimento). */
data class CatStyle(val cat: FileCategory, val title: String, val icon: ImageVector, val colors: List<Color>)

val CAT_STYLES = listOf(
    CatStyle(FileCategory.VIDEO, "Video", Icons.Filled.VideoLibrary, listOf(Color(0xFFFF8A3D), Color(0xFFFF3D2E))),
    CatStyle(FileCategory.IMAGE, "Immagini", Icons.Filled.Image, listOf(Color(0xFF4F8BFF), Color(0xFF2F5BEA))),
    CatStyle(FileCategory.AUDIO, "Audio", Icons.Filled.MusicNote, listOf(Color(0xFFB07CFF), Color(0xFF7C3AED))),
    CatStyle(FileCategory.ARCHIVE, "Archivi", Icons.Filled.Folder, listOf(Color(0xFF4ADE80), Color(0xFF16A34A))),
    CatStyle(FileCategory.DOCUMENT, "Documenti", Icons.Filled.Description, listOf(Color(0xFF38BDF8), Color(0xFF1E3A8A))),
    CatStyle(FileCategory.APK, "App (APK)", Icons.Filled.Android, listOf(Color(0xFF2DD4BF), Color(0xFF0F766E))),
    CatStyle(FileCategory.OTHER, "Altro", Icons.AutoMirrored.Filled.InsertDriveFile, listOf(Color(0xFF94A3B8), Color(0xFF475569))),
)

/** Apre la categoria: le immagini vanno nella galleria intelligente, il resto nell'elenco file. */
fun openCategory(vm: MainViewModel, cat: FileCategory) {
    vm.searchResults = null
    if (cat == FileCategory.IMAGE) { vm.photoResults = null; vm.tab = Tab.PHOTOS; vm.sub = Sub.NONE }
    else { vm.category = cat; vm.folder = null; vm.tab = Tab.FILES; vm.sub = Sub.CATEGORY }
}
fun openFolder(vm: MainViewModel, path: String) {
    vm.searchResults = null
    vm.category = null; vm.folder = path; vm.tab = Tab.FILES; vm.sub = Sub.CATEGORY
}
fun tipAction(vm: MainViewModel, t: HomeTip) {
    when (t.kind) {
        "junk" -> { vm.cleanSection = "JUNK"; vm.tab = Tab.CLEAN; vm.sub = Sub.NONE }
        "whatsapp" -> { vm.cleanSection = "WA"; vm.tab = Tab.CLEAN; vm.sub = Sub.NONE }
        "download" -> vm.openChatWith("Metti in ordine la cartella Download")
        "trash" -> { vm.tab = Tab.MORE; vm.sub = Sub.TRASH }
        else -> { vm.tab = Tab.CHAT; vm.sub = Sub.NONE }
    }
}

/** Home a flusso: mantiene illustrazioni e grafico del brand, senza sovrapporre il testo. */
@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val mk = rememberMk()
    val free = remember(vm.scanning, vm.home, vm.filesRevision) { Storage.freeBytes() }
    val total = remember { Storage.totalBytes() }
    val used = (total - free).coerceAtLeast(0)
    val data = vm.home
    val sizes = data.byCat.toMap()
    LaunchedEffect(Unit) {
        if (FileIndex.scannedAt == 0L || System.currentTimeMillis() - FileIndex.scannedAt > 5 * 60_000L) vm.rescan() else vm.refreshHome()
    }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..12 -> "Buongiorno"; in 13..17 -> "Buon pomeriggio"; else -> "Buonasera" }
    val today = SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(Date())
    Column(Modifier.fillMaxSize().testTag("home_layout").statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
        val largeText = LocalDensity.current.fontScale > 1.2f
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                JarvisBrand()
                Spacer(Modifier.height(4.dp))
                Text(today, style = MaterialTheme.typography.bodySmall, color = J.Muted)
                if (!largeText) {
                    Spacer(Modifier.height(16.dp))
                    Text("$greeting,\n${Prefs(ctx).userName}", style = MaterialTheme.typography.headlineSmall, color = J.Text)
                }
            }
            Column(Modifier.width(88.dp).pressable({ vm.tab = Tab.CHAT; vm.sub = Sub.NONE })
                .semantics(mergeDescendants = true) { contentDescription = "Chiedi a Jarvis" }, horizontalAlignment = Alignment.CenterHorizontally) {
                ArcReactor(76.dp, active = vm.scanning)
                Spacer(Modifier.height(4.dp))
                Text("Chiedi a Jarvis", color = J.Blue, style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center)
            }
        }
        if (largeText) Text("$greeting,\n${Prefs(ctx).userName}", style = MaterialTheme.typography.headlineSmall, color = J.Text,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Il tuo spazio", style = MaterialTheme.typography.titleMedium, color = J.Text, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = { vm.rescan() }, enabled = !vm.scanning && !vm.operationBusy) {
                Text(if (vm.scanning) "Aggiorno…" else "Aggiorna")
            }
        }
        with(mk) {
            Box(Modifier.fillMaxWidth().height(156f.d).semantics(mergeDescendants = true) {
                contentDescription = "${Fmt.size(free)} disponibili su ${Fmt.size(total)}. ${used * 100 / total.coerceAtLeast(1)} per cento utilizzato."
            }) {
                Box(Modifier.offset(y = (-218f).d)) { StorageBlock(vm, free, total, used, sizes) }
            }
        }
        Text(if (vm.scanning) "Scansione dei file in corso…" else "${vm.fileCount} file indicizzati", color = J.Muted,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("I tuoi file", style = MaterialTheme.typography.titleLarge, color = J.Text, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = { vm.sub = Sub.FIND }) { Text("Cerca") }
        }
        val categories = CAT_STYLES.take(6)
        val cellWidth = (LocalConfiguration.current.screenWidthDp - 42) / 2f
        val columns = if (cellWidth < 70 + 60 * LocalDensity.current.fontScale) 1 else 2
        categories.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cat ->
                    GlassCard(Modifier.weight(1f), onClick = { openCategory(vm, cat.cat) }, padding = PaddingValues(12.dp)) {
                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            val asset = when (cat.cat) {
                                FileCategory.VIDEO -> R.drawable.cat_video; FileCategory.IMAGE -> R.drawable.cat_image
                                FileCategory.AUDIO -> R.drawable.cat_audio; FileCategory.ARCHIVE -> R.drawable.cat_archive; else -> null
                            }
                            if (asset != null) Image(painterResource(asset), null, Modifier.size(40.dp))
                            else IconBadge(cat.icon, cat.colors.last(), 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cat.title, color = J.Text, style = MaterialTheme.typography.labelLarge)
                                Text(Fmt.size(sizes[cat.cat] ?: 0L), color = J.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Download", Modifier.weight(1f), Icons.Filled.Download) { openFolder(vm, "Download") }
            GhostButton("Tutte le cartelle", Modifier.weight(1f), Icons.Filled.Folder) { vm.tab = Tab.FILES; vm.sub = Sub.NONE }
        }
        val tip = data.tips.firstOrNull() ?: HomeTip("chat", "in ordine", "I tuoi file", "Chiedi a Jarvis di cercare un file o riordinare una cartella.")
        GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), highlight = true) {
            Text("SUGGERIMENTO DI JARVIS", color = J.Text, style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    Text("${tip.big} ${tip.title}", color = J.Text, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(tip.sub, color = J.Muted, style = MaterialTheme.typography.bodySmall)
                }
                Image(painterResource(R.drawable.jarvis_illus), null, Modifier.size(86.dp))
            }
            com.jarvis.files.ui.components.GradientButton(when (tip.kind) {
                "download" -> "Vedi il riordino"; "trash" -> "Apri il cestino"; "chat" -> "Parla con Jarvis"; else -> "Controlla i file"
            }, Modifier.fillMaxWidth()) { tipAction(vm, tip) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            vm.pending?.let { (title, reply, plan) ->
                SectionTitle("Piano in attesa")
                Text("$title — $reply", color = J.Text, modifier = Modifier.padding(bottom = 10.dp))
                PlanCard(plan, { vm.confirmPending(it) }, { vm.dismissPending() }, enabled = !vm.operationBusy && !vm.busy)
            }
            vm.undoInfo?.let { (source, count) ->
                GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                    Text(source, color = J.Text, style = MaterialTheme.typography.titleSmall)
                    Text("$count modifiche recuperabili", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Annulla modifiche", Modifier.fillMaxWidth(), Icons.AutoMirrored.Filled.Undo, enabled = !vm.busy && !vm.operationBusy) { vm.undo() }
                }
            }
        }
    }
}

/** Ciambella della memoria, scheda bianca e didascalie con le linee curve. */
@Composable
private fun Mk.StorageBlock(vm: MainViewModel, free: Long, total: Long, used: Long, sizes: Map<FileCategory, Long>) {
    val gib = 1_073_741_824.0
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
    // Composizione dello spazio usato: video (arancio), immagini (azzurro), documenti/audio/archivi/app (blu), resto (grigio)
    val video = sizes[FileCategory.VIDEO] ?: 0L
    val img = sizes[FileCategory.IMAGE] ?: 0L
    val navy = listOf(FileCategory.DOCUMENT, FileCategory.AUDIO, FileCategory.ARCHIVE, FileCategory.APK).sumOf { sizes[it] ?: 0L }
    val rest = (used - video - img - navy).coerceAtLeast(0)
    val parts = listOf(
        video to listOf(Color(0xFFF0436F), Color(0xFFE8701F), Color(0xFFFF9A5C)),
        img to listOf(Color(0xFF7CC8FF), Color(0xFF4FA8FF)),
        navy to listOf(Color(0xFF2E55B8), Color(0xFF1C3A85)),
        rest to listOf(Color(0xFFC5CBD4), Color(0xFFD7DCE3)),
    ).filter { it.first > 0 }
    val sum = parts.sumOf { it.first }.coerceAtLeast(1)

    // Scheda bianca dietro (la ciambella sporge sopra)
    Box(
        Modifier.offset(17.5f.d, 228.7f.d).size(377.5f.d, 136.3f.d)
            .shadow(10f.d, RoundedCornerShape(26f.d), ambientColor = Color(0x10000000), spotColor = Color(0x141E2A4A))
            .clip(RoundedCornerShape(26f.d)).background(Color(0xFFFDFEFF))
            .border(1f.d, Color(0xFFF0F2F6), RoundedCornerShape(26f.d))
            .pressable({ vm.tab = Tab.FILES; vm.sub = Sub.NONE }),
    )
    Canvas(Modifier.fillMaxWidth().height(380f.d)) {
        val k = size.width / 412f
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        val c = p(205f, 285f)
        // alone chiaro
        drawCircle(Brush.radialGradient(listOf(Color(0x141E2A4A), Color.Transparent), c, 112f * k), 112f * k, c)
        drawCircle(Color(0xF2FFFFFF), 99f * k, c)
        drawCircle(Color(0xFFE8ECF2), 99f * k, c, style = Stroke(1f * k))
        // binario
        drawCircle(Color(0xFFEEF1F6), 73.5f * k, c, style = Stroke(21f * k))
        // segmenti a tessere
        var start = 140f
        val total360 = 360f * sweep.value
        parts.forEach { (bytes, cols) ->
            val span = (bytes.toFloat() / sum) * total360
            val tiles = kotlin.math.ceil(span / 42f).toInt().coerceAtLeast(1)
            val tileSpan = span / tiles
            repeat(tiles) { t ->
                val f = if (tiles == 1) 0.5f else t / (tiles - 1f)
                val col = gradAt(cols, f)
                val s = start + t * tileSpan + 1.2f
                val sw = (tileSpan - 2.4f).coerceAtLeast(0.5f)
                drawArc(col, s, sw, false, topLeft = Offset(c.x - 73.5f * k, c.y - 73.5f * k),
                    size = androidx.compose.ui.geometry.Size(147f * k, 147f * k), style = Stroke(20f * k))
                // riflesso interno
                drawArc(Color.White.copy(alpha = 0.22f), s, sw, false, topLeft = Offset(c.x - 81f * k, c.y - 81f * k),
                    size = androidx.compose.ui.geometry.Size(162f * k, 162f * k), style = Stroke(3f * k))
            }
            start += span
        }
        // disco centrale
        drawCircle(Brush.radialGradient(listOf(Color(0x221E2A4A), Color.Transparent), c, 60f * k), 60f * k, c)
        drawCircle(Color.White, 52f * k, c)
        drawCircle(Color(0xFFEDF0F5), 52f * k, c, style = Stroke(1f * k))
        // icona disco
        val dl = p(198.2f, 330.9f)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFA3AEBF), Color(0xFF7D8899)), dl.y, dl.y + 11f * k), dl,
            androidx.compose.ui.geometry.Size(17f * k, 11f * k), androidx.compose.ui.geometry.CornerRadius(2.5f * k))
        drawLine(Color.White.copy(alpha = 0.7f), p(198.6f, 337f), p(214.8f, 337f), 0.8f * k)
        drawCircle(Color.White, 0.9f * k, p(209.5f, 339.2f)); drawCircle(Color.White, 0.9f * k, p(212f, 339.2f))
        // linea arancio a sinistra
        callout(Color(0xFFFF8A3D), p(31.7f, 292.9f), p(92f, 292.9f), p(103f, 292.9f), p(106f, 276f), p(115f, 276f), k)
        drawCircle(Color(0xFFFF8A3D), 1.5f * k, p(117f, 325f))
        // linea blu a destra
        val blue = Color(0xFF1E7BFF)
        val path = Path().apply { moveTo(307.8f * k, 282.3f * k); cubicTo(318f * k, 282.3f * k, 322f * k, 270f * k, 337.7f * k, 270f * k) }
        drawPath(path, blue, style = Stroke(1f * k))
        drawCircle(blue, 2.4f * k, p(337.7f, 270f))
        drawCircle(blue, 1.4f * k, p(307.4f, 264f))
    }
    val usedTxt = String.format(Locale.ITALY, "%.1f GB", used / gib)
    T("${(used * 100 / total.coerceAtLeast(1))}%", 35.2f, 248.5f, 17.2f, 800, MkColor.Ink, letterSpacing = -0.3f)
    T("utilizzato", 37.8f, 270.4f, 12.1f, 400, MkColor.Slate)
    T(usedTxt, 37.4f, 305.2f, 12.3f, 600, MkColor.Ink)
    T("in uso", 38.3f, 319.7f, 9.5f, 400, MkColor.Slate)
    T(String.format(Locale.ITALY, "%.1f", free / gib), 145f, 259.9f, 29.5f, 800, MkColor.Ink, width = 120f, align = TextAlign.Center, letterSpacing = -0.8f)
    T("GB liberi", 145f, 292f, 14.4f, 700, MkColor.Ink, width = 120f, align = TextAlign.Center, letterSpacing = -0.2f)
    T("su ${String.format(Locale.ITALY, "%.0f", total / gib)} GB", 145f, 310.9f, 9.4f, 400, MkColor.Slate, width = 120f, align = TextAlign.Center)
    T(String.format(Locale.ITALY, "%.0f GB", total / gib), 348.3f, 263.9f, 11.2f, 600, MkColor.Ink)
    T("totale", 348.3f, 278.4f, 9.6f, 400, MkColor.Slate)
}

private fun DrawScope.callout(col: Color, dot: Offset, a: Offset, c1: Offset, c2: Offset, end: Offset, k: Float) {
    drawCircle(col, 2.3f * k, dot)
    val path = Path().apply { moveTo(dot.x, dot.y); lineTo(a.x, a.y); cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y) }
    drawPath(path, Brush.horizontalGradient(listOf(col, col.copy(alpha = 0.55f)), dot.x, end.x), style = Stroke(1f * k))
}

private fun gradAt(cols: List<Color>, f: Float): Color {
    if (cols.size == 1) return cols[0]
    val pos = f * (cols.size - 1)
    val i = pos.toInt().coerceAtMost(cols.size - 2)
    return lerp(cols[i], cols[i + 1], pos - i)
}

/** Riga di lista: icona tonale, titolo, sottotitolo, freccia. */
@Composable
fun ListRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = J.Text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = J.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = J.Muted)
    }
}
