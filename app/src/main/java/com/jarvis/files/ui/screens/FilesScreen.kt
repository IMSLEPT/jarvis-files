package com.jarvis.files.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.jarvis.files.core.FileEntry
import com.jarvis.files.core.FileIndex
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.Op
import com.jarvis.files.core.Plan
import com.jarvis.files.core.Storage
import com.jarvis.files.core.Vault
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.Tab
import com.jarvis.files.ui.components.Chip
import com.jarvis.files.ui.components.EmptyState
import com.jarvis.files.ui.components.FileThumb
import com.jarvis.files.ui.components.GhostButton
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.SectionTitle
import com.jarvis.files.ui.components.pressable
import com.jarvis.files.ui.theme.J
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class QuickFolder(val title: String, val path: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val QUICK_FOLDERS = listOf(
    QuickFolder("Download", "Download", Icons.Filled.Download),
    QuickFolder("Fotocamera", "DCIM/Camera", Icons.Filled.PhotoCamera),
    QuickFolder("Screenshot", "Pictures/Screenshots", Icons.Filled.Screenshot),
    QuickFolder("Documenti", "Documents", Icons.Filled.Description),
    QuickFolder("WhatsApp", "Android/media/com.whatsapp/WhatsApp/Media", Icons.Filled.Chat),
)

@Composable
fun FilesScreen(vm: MainViewModel) {
    val sizes = vm.home.byCat.toMap()
    LaunchedEffect(Unit) { vm.refreshHome() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("File", style = MaterialTheme.typography.headlineLarge, color = J.Text)
            Text("${FileIndex.entries.size} file · ${Fmt.size(Storage.totalBytes() - Storage.freeBytes())} usati", color = J.Muted)
        }
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().pressable({ vm.sub = Sub.FIND }).clip(RoundedCornerShape(50)).background(J.Card)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = J.Muted)
                    Spacer(Modifier.width(10.dp))
                    Text("Cerca un file o un documento…", color = J.Muted)
                }
                SectionTitle("Categorie")
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                    ListRow(Icons.Filled.PhotoLibrary, "Foto e ricerca intelligente", "Persone, luoghi, oggetti") { vm.photoResults = null; vm.tab = Tab.PHOTOS; vm.sub = Sub.NONE }
                    CAT_STYLES.filter { it.cat != com.jarvis.files.core.FileCategory.IMAGE }.forEach { s ->
                        ListRow(s.icon, s.title, Fmt.size(sizes[s.cat] ?: 0L)) { openCategory(vm, s.cat) }
                    }
                }
                SectionTitle("Cartelle")
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                    QUICK_FOLDERS.filter { Storage.file(it.path).isDirectory }.forEach { f ->
                        ListRow(f.icon, f.title, f.path) { vm.searchResults = null; vm.category = null; vm.folder = f.path; vm.sub = Sub.CATEGORY }
                    }
                    ListRow(Icons.Filled.Lock, "Cartella segreta", "Protetta dall'impronta") { vm.sub = Sub.VAULT }
                    ListRow(Icons.Filled.Delete, "Cestino", "File eliminati negli ultimi 30 giorni") { vm.sub = Sub.TRASH }
                }
            }
        }
    }
}

/** Elenco dei file di una categoria o di una cartella, con selezione multipla. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val cat = vm.category
    val folder = vm.folder
    val search = vm.searchResults
    val searchPaths = remember(search) { search?.map { it.rel }?.toSet() }
    var sort by remember { mutableStateOf(0) }
    var files by remember { mutableStateOf<List<FileEntry>?>(null) }
    val sel = remember(cat, folder, search) { mutableStateListOf<String>() }
    androidx.activity.compose.BackHandler(enabled = sel.isNotEmpty()) { sel.clear() }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(cat, folder, search, sort, reload, vm.filesRevision) {
        files = withContext(Dispatchers.IO) {
            FileIndex.ensure().filter { e ->
                (searchPaths == null || e.rel in searchPaths) && (cat == null || e.category == cat) && (folder == null || e.folder == folder || e.folder.startsWith("$folder/"))
            }.let { l ->
                when (sort) { 1 -> l.sortedByDescending { it.size }; 2 -> l.sortedBy { it.name.lowercase() }; else -> l.sortedByDescending { it.modified } }
            }
        }
        sel.retainAll(files.orEmpty().map { it.rel }.toSet())
    }
    val title = if (search != null) "Risultati ricerca" else cat?.let { c -> CAT_STYLES.firstOrNull { it.cat == c }?.title } ?: folder?.substringAfterLast('/') ?: "File"
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 6.dp)) {
            SubHeader(title, files?.let { "${it.size} file · ${Fmt.size(it.sumOf { f -> f.size })}" }) { vm.sub = Sub.NONE; vm.searchResults = null; sel.clear() }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Recenti", sort == 0) { sort = 0 }
            Chip("Più grandi", sort == 1) { sort = 1 }
            Chip("Nome", sort == 2) { sort = 2 }
            Chip("Chiedi a Jarvis") { vm.openChatWith("Metti in ordine ${folder ?: "i file della categoria $title"}") }
        }
        val list = files
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            when {
                list == null -> item { Text("Carico…", color = J.Muted, modifier = Modifier.padding(20.dp)) }
                list.isEmpty() -> item { EmptyState("Nessun file", "Qui non c'è niente.") }
                else -> items(list, key = { it.rel }) { e ->
                    val selected = e.rel in sel
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (selected) J.AccentSoft else androidx.compose.ui.graphics.Color.Transparent)
                            .combinedClickable(
                                onClick = {
                                    if (sel.isNotEmpty()) { if (selected) sel.remove(e.rel) else sel.add(e.rel) }
                                    else Storage.openFile(ctx, Storage.file(e.rel))
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (selected) sel.remove(e.rel) else sel.add(e.rel)
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column { FileThumb(Storage.file(e.rel), 46.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, color = J.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text("${Fmt.size(e.size)} · ${Fmt.date(e.modified)} · ${e.folder}", color = J.Muted, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        if (selected) Icon(Icons.Filled.CheckCircle, null, tint = J.Orange, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        if (sel.isNotEmpty()) {
            val chosen = list.orEmpty().filter { it.rel in sel }
            GlassCard(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), highlight = true) {
                Text("${chosen.size} selezionati · ${Fmt.size(chosen.sumOf { it.size })}", color = J.Text, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Condividi", icon = Icons.Filled.Share, enabled = !vm.operationBusy && !vm.busy) { Storage.shareFiles(ctx, chosen.map { Storage.file(it.rel) }) }
                    GhostButton("Cassaforte", icon = Icons.Filled.Lock, enabled = !vm.operationBusy && !vm.busy) {
                        vm.importIntoVault(chosen.map { it.rel }) { imported -> sel.removeAll(imported); reload++ }
                    }
                    GhostButton("Cestino", icon = Icons.Filled.Delete, color = J.Red, enabled = !vm.operationBusy && !vm.busy) {
                        vm.runPlan(Plan(chosen.map { Op.Trash(it.rel, it.size) }, emptyList()), "Eliminazione da $title") { sel.clear(); reload++ }
                    }
                    GhostButton("Annulla selezione", color = J.Muted) { sel.clear() }
                }
            }
        }
    }
}

