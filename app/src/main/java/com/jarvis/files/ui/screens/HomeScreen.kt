package com.jarvis.files.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.files.core.*
import com.jarvis.files.ui.*
import com.jarvis.files.ui.components.*
import com.jarvis.files.ui.theme.J
import java.text.SimpleDateFormat
import java.util.*

data class CatStyle(val cat: FileCategory, val title: String, val icon: ImageVector, val colors: List<Color>)
val CAT_STYLES = listOf(
    CatStyle(FileCategory.VIDEO, "Video", Icons.Filled.VideoLibrary, listOf(Color(0xFFFF8A3D), Color(0xFFEB542D))),
    CatStyle(FileCategory.IMAGE, "Immagini", Icons.Filled.Image, listOf(Color(0xFF4F8BFF), Color(0xFF2F5BEA))),
    CatStyle(FileCategory.AUDIO, "Audio", Icons.Filled.MusicNote, listOf(Color(0xFFB07CFF), Color(0xFF7C3AED))),
    CatStyle(FileCategory.ARCHIVE, "Archivi", Icons.Filled.Folder, listOf(Color(0xFF4ADE80), Color(0xFF16834A))),
    CatStyle(FileCategory.DOCUMENT, "Documenti", Icons.Filled.Description, listOf(Color(0xFF38BDF8), Color(0xFF1E3A8A))),
    CatStyle(FileCategory.APK, "App (APK)", Icons.Filled.Android, listOf(Color(0xFF2DD4BF), Color(0xFF0F766E))),
    CatStyle(FileCategory.OTHER, "Altro", Icons.AutoMirrored.Filled.InsertDriveFile, listOf(Color(0xFF94A3B8), Color(0xFF475569))),
)

fun openCategory(vm: MainViewModel, cat: FileCategory) {
    if (cat == FileCategory.IMAGE) { vm.photoResults = null; vm.tab = Tab.PHOTOS; vm.sub = Sub.NONE }
    else { vm.category = cat; vm.folder = null; vm.tab = Tab.FILES; vm.sub = Sub.CATEGORY }
}
fun openFolder(vm: MainViewModel, path: String) {
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

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        if (FileIndex.scannedAt == 0L || System.currentTimeMillis() - FileIndex.scannedAt > 5 * 60_000L) vm.rescan()
        else vm.refreshHome()
    }
    val sizes = vm.home.byCat.toMap()
    val total = Storage.totalBytes().coerceAtLeast(1)
    val free = Storage.freeBytes().coerceIn(0, total)
    val used = total - free
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..12 -> "Buongiorno"; in 13..17 -> "Buon pomeriggio"; else -> "Buonasera"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("JARVIS FILES", style = MaterialTheme.typography.labelLarge, color = J.Orange)
                    Spacer(Modifier.height(16.dp))
                    Text("$greeting,", style = MaterialTheme.typography.headlineSmall, color = J.Text)
                    Text(Prefs(ctx).userName, style = MaterialTheme.typography.headlineMedium, color = J.Text)
                    Spacer(Modifier.height(8.dp))
                    Text(SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(Date()), style = MaterialTheme.typography.bodySmall, color = J.Muted)
                }
                IconButton(onClick = { vm.tab = Tab.CHAT; vm.sub = Sub.NONE }, modifier = Modifier.size(100.dp).semantics { contentDescription = "Apri Jarvis" }) {
                    ArcReactor(100.dp, active = vm.scanning)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Il tuo spazio", color = J.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.rescan() }, enabled = !vm.scanning) { Icon(Icons.Filled.Refresh, "Aggiorna file", tint = J.Blue) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    val parts = CAT_STYLES.map { (sizes[it.cat] ?: 0L) to it.colors.last() }
                    val otherColor = J.Navy
                    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
                            val inset = 8.dp.toPx()
                            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                            val origin = androidx.compose.ui.geometry.Offset(inset, inset)
                            drawArc(Color(0xFFF0F2F7), -90f, 360f, false, origin, arcSize, style = stroke)
                            var angle = -90f
                            var remaining = used
                            for ((bytes, color) in parts + (used to otherColor)) {
                                val amount = bytes.coerceIn(0, remaining)
                                val span = (amount.toDouble() / total * 360).toFloat()
                                if (span > 0.8f) drawArc(color, angle + 1f, (span - 2f).coerceAtLeast(0.6f), false, origin, arcSize, style = stroke)
                                angle += span
                                remaining -= amount
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${used * 100 / total}%", style = MaterialTheme.typography.headlineSmall, color = J.Text)
                            Text("in uso", style = MaterialTheme.typography.bodySmall, color = J.Muted)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.size(free), style = MaterialTheme.typography.headlineSmall, color = J.Text)
                        Text("disponibili", color = J.Muted, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("${Fmt.size(used)} usati su ${Fmt.size(total)}", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(if (vm.scanning) "Aggiornamento dei file…" else "${vm.fileCount} file indicizzati", color = J.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Text("I tuoi file", color = J.Text, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CAT_STYLES.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { s ->
                            GlassCard(Modifier.weight(1f), onClick = { openCategory(vm, s.cat) }, padding = PaddingValues(16.dp)) {
                                IconBadge(s.icon, tint = s.colors.last(), size = 42.dp)
                                Spacer(Modifier.height(12.dp))
                                Text(s.title, color = J.Text, style = MaterialTheme.typography.titleSmall)
                                Text(Fmt.size(sizes[s.cat] ?: 0L), color = J.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 4.dp)) {
                ListRow(Icons.Filled.Description, "Documenti e altri file", "PDF, app, download e cartelle") { vm.tab = Tab.FILES; vm.sub = Sub.NONE }
            }
        }
        item {
            val tip = vm.home.tips.firstOrNull()
            GlassCard(Modifier.fillMaxWidth(), highlight = true) {
                Text("SUGGERIMENTO DI JARVIS", color = J.Blue, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                Text(if (vm.scanning) "Controllo i tuoi file" else tip?.let { "${it.big} ${it.title}" } ?: "Il tuo spazio, in ordine", color = J.Text, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(tip?.sub ?: "Apri Jarvis per cercare un documento o organizzare una cartella.", color = J.Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                GradientButton(if (tip == null) "Apri Jarvis" else "Vedi proposta", Modifier.fillMaxWidth(), enabled = !vm.scanning) {
                    tipAction(vm, tip ?: HomeTip("chat", "", "", ""))
                }
            }
        }
        vm.pending?.let { (title, reply, plan) -> item {
            Text("$title · $reply", color = J.Text, modifier = Modifier.padding(bottom = 12.dp))
            PlanCard(plan, onConfirm = { vm.confirmPending(it) }, onCancel = { vm.dismissPending() }, enabled = !vm.operationBusy && !vm.busy)
        } }
        vm.undoInfo?.let { (source, n) -> item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(source, color = J.Text, style = MaterialTheme.typography.titleSmall)
                Text("$n modifiche recuperabili", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                GradientButton("Annulla modifiche", Modifier.fillMaxWidth(), Icons.AutoMirrored.Filled.Undo, enabled = !vm.operationBusy && !vm.busy) { vm.undo() }
            }
        } }
    }
}

@Composable
fun ListRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = J.Text, style = MaterialTheme.typography.titleSmall)
            Text(sub, color = J.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = J.Muted)
    }
}
