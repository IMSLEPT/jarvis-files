package com.jarvis.files.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import com.jarvis.files.ui.components.PlanCard
import com.jarvis.files.ui.components.SectionTitle
import com.jarvis.files.ui.components.pressable
import com.jarvis.files.ui.theme.J
import java.util.Calendar

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import com.jarvis.files.ui.components.CategorySymbol
import com.jarvis.files.ui.components.SearchEntry
import com.jarvis.files.ui.components.FeatureRow

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

/** Home approvata: ricerca visibile, memoria compatta, categorie e assistente. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val free = remember(vm.scanning, vm.home, vm.filesRevision) { Storage.freeBytes() }
    val total = remember { Storage.totalBytes() }.coerceAtLeast(1)
    val used = (total - free).coerceIn(0, total)
    val fraction = (used.toDouble() / total).toFloat()
    val sizes = vm.home.byCat.toMap()
    LaunchedEffect(Unit) {
        if (FileIndex.scannedAt == 0L || System.currentTimeMillis() - FileIndex.scannedAt > 5 * 60_000L) vm.rescan() else vm.refreshHome()
    }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..12 -> "Buongiorno"; in 13..17 -> "Buon pomeriggio"; else -> "Buonasera" }
    val largeText = LocalDensity.current.fontScale > 1.2f
    Column(Modifier.fillMaxSize().testTag("home_layout").statusBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Large system fonts get a separate row; narrow screens keep readable compact branding.
            if (largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    JarvisBrand()
                    AssistantEntry(vm)
                }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                JarvisBrand(compact = maxWidth < 330.dp)
                AssistantEntry(vm, compact = maxWidth < 330.dp)
            }
        }
        Text("$greeting,\n${Prefs(ctx).userName}", color = J.Text, style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold)
        SearchEntry({ vm.sub = Sub.FIND })
        GlassCard(Modifier.fillMaxWidth().testTag("storage_card"), padding = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spazio sul telefono", style = MaterialTheme.typography.titleMedium, color = J.Text, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.rescan() }, enabled = !vm.scanning && !vm.operationBusy) {
                    Icon(Icons.Filled.Refresh, "Aggiorna spazio e file", tint = J.Muted)
                }
            }
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Fmt.size(free), color = J.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("liberi", color = J.Muted, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(J.CardHi)
                .semantics { contentDescription = "${(fraction * 100).toInt()} per cento utilizzato" }) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(J.Navy))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text("${Fmt.size(used)} di ${Fmt.size(total)} utilizzati", color = J.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text("${(fraction * 100).toInt()}%", color = J.Text, style = MaterialTheme.typography.labelLarge)
            }
            if (vm.scanning) {
                Spacer(Modifier.height(8.dp))
                Text("Aggiornamento in corso…", color = J.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("I tuoi file", color = J.Text, style = MaterialTheme.typography.titleLarge)
        val columns = if (largeText || LocalConfiguration.current.screenWidthDp < 350) 1 else 2
        CAT_STYLES.take(6).chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cat ->
                    GlassCard(Modifier.weight(1f), onClick = { openCategory(vm, cat.cat) }, padding = PaddingValues(12.dp)) {
                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategorySymbol(cat.cat)
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
        if (largeText) {
            GhostButton("Download", Modifier.fillMaxWidth(), Icons.Filled.Download) { openFolder(vm, "Download") }
            GhostButton("Tutte le cartelle", Modifier.fillMaxWidth(), Icons.Filled.Folder) { vm.tab = Tab.FILES; vm.sub = Sub.NONE }
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Download", Modifier.weight(1f), Icons.Filled.Download) { openFolder(vm, "Download") }
            GhostButton("Tutte le cartelle", Modifier.weight(1f), Icons.Filled.Folder) { vm.tab = Tab.FILES; vm.sub = Sub.NONE }
        }
        vm.pending?.let { (title, reply, plan) ->
            SectionTitle("Piano in attesa")
            Text("$title — $reply", color = J.Text)
            PlanCard(plan, { vm.confirmPending(it) }, { vm.dismissPending() }, enabled = !vm.operationBusy && !vm.busy)
        }
        vm.undoInfo?.let { (source, count) ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(source, color = J.Text, style = MaterialTheme.typography.titleSmall)
                Text("$count modifiche recuperabili", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                GhostButton("Annulla modifiche", Modifier.fillMaxWidth(), Icons.AutoMirrored.Filled.Undo, enabled = !vm.busy && !vm.operationBusy) { vm.undo() }
            }
        }
    }
}

@Composable
private fun AssistantEntry(vm: MainViewModel, compact: Boolean = false) {
    Row(Modifier.width(if (compact) 132.dp else 154.dp).clip(RoundedCornerShape(18.dp)).background(J.Card)
        .border(1.dp, J.Blue.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
        .pressable({ vm.tab = Tab.CHAT; vm.sub = Sub.NONE })
        .semantics(mergeDescendants = true) { contentDescription = "Chiedi a Jarvis" }
        .padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        ArcReactor(if (compact) 44.dp else 50.dp, active = vm.scanning)
        Spacer(Modifier.width(8.dp))
        Text("Chiedi a\nJarvis", style = MaterialTheme.typography.labelLarge, color = J.Text, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ListRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) = FeatureRow(icon, title, sub, onClick)
