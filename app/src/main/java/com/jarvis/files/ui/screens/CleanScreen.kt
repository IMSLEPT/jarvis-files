package com.jarvis.files.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.jarvis.files.core.AppCache
import com.jarvis.files.core.AppCacheInfo
import com.jarvis.files.core.CacheSafety
import com.jarvis.files.core.Cleaner
import com.jarvis.files.core.DupGroup
import com.jarvis.files.core.FileEntry
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.JunkItem
import com.jarvis.files.core.Op
import com.jarvis.files.core.Plan
import com.jarvis.files.core.Storage
import com.jarvis.files.core.WaCategory
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.components.ArcReactor
import com.jarvis.files.ui.components.Chip
import com.jarvis.files.ui.components.Dot
import com.jarvis.files.ui.components.EmptyState
import com.jarvis.files.ui.components.FileThumb
import com.jarvis.files.ui.components.GhostButton
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.GradientButton
import com.jarvis.files.ui.components.SectionTitle
import com.jarvis.files.ui.theme.J
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CleanTab(val title: String) {
    CACHE("Cache app"), JUNK("File inutili"), DUP("Doppioni"), WA("WhatsApp"), BIG("Grandi e vecchi"), SHOTS("Screenshot e foto")
}

@Composable
fun CleanScreen(vm: MainViewModel) {
    var tab by remember { mutableStateOf(CleanTab.CACHE) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text("PULIZIA", style = MaterialTheme.typography.labelSmall, color = J.Cyan)
            Text("Libera spazio in sicurezza", style = MaterialTheme.typography.headlineSmall, color = J.Text)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CleanTab.entries.forEach { t -> Chip(t.title, selected = t == tab) { tab = t } }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                CleanTab.CACHE -> CacheTab(vm)
                CleanTab.JUNK -> JunkTab(vm)
                CleanTab.DUP -> DupTab(vm)
                CleanTab.WA -> WaTab(vm)
                CleanTab.BIG -> BigTab(vm)
                CleanTab.SHOTS -> ShotsTab(vm)
            }
        }
    }
}

@Composable
private fun Loading(text: String) {
    Row(Modifier.fillMaxWidth().padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
        ArcReactor(40.dp); Spacer(Modifier.width(14.dp)); Text(text, color = J.Cyan)
    }
}

/** Barra in basso per spostare nel cestino la selezione. */
@Composable
private fun TrashBar(vm: MainViewModel, selected: List<Pair<String, Long>>, source: String, onDone: () -> Unit) {
    if (selected.isEmpty()) return
    GlassCard(Modifier.fillMaxWidth().padding(12.dp), highlight = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${selected.size} selezionati", color = J.Text, style = MaterialTheme.typography.titleSmall)
                Text(Fmt.size(selected.sumOf { it.second }), color = J.Electric, style = MaterialTheme.typography.bodySmall)
            }
            GradientButton("Nel cestino", icon = Icons.Filled.DeleteSweep) {
                vm.runPlan(Plan(selected.map { Op.Trash(it.first, it.second) }, emptyList()), source, onDone)
            }
        }
    }
}

@Composable
private fun SelectRow(selected: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (selected) J.Electric else J.Muted)
        Spacer(Modifier.width(10.dp))
        content()
    }
}

private fun LazyListScope.fileRows(files: List<FileEntry>, sel: SnapshotStateList<String>) {
    items(files, key = { it.rel }) { e ->
        SelectRow(e.rel in sel, { if (e.rel in sel) sel.remove(e.rel) else sel.add(e.rel) }) {
            FileThumb(Storage.file(e.rel), 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, color = J.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text("${Fmt.size(e.size)} · ${Fmt.date(e.modified)} · ${e.folder}", color = J.Muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------------- Cache delle app ----------------

@Composable
private fun CacheTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    var hasAccess by remember { mutableStateOf(AppCache.hasUsageAccess(ctx)) }
    var apps by remember { mutableStateOf<List<AppCacheInfo>?>(null) }
    var reload by remember { mutableStateOf(0) }
    val clearAll = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        vm.toast = if (r.resultCode == Activity.RESULT_OK) "Cache svuotate" else "Operazione annullata"
        reload++
    }
    val usageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasAccess = AppCache.hasUsageAccess(ctx); reload++
    }
    LaunchedEffect(reload, hasAccess) { if (hasAccess) apps = withContext(Dispatchers.IO) { AppCache.list(ctx) } }

    LazyColumn(contentPadding = PaddingValues(14.dp)) {
        if (!hasAccess) {
            item {
                GlassCard(Modifier.fillMaxWidth(), highlight = true) {
                    Text("Serve il permesso \"Accesso ai dati di utilizzo\"", color = J.Text, style = MaterialTheme.typography.titleSmall)
                    Text("Così Jarvis vede quanto pesa la cache di ogni app e quali app usi di più.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    GradientButton("Concedi") { usageLauncher.launch(AppCache.usageAccessIntent()) }
                }
            }
            return@LazyColumn
        }
        val list = apps
        if (list == null) { item { Loading("Misuro le cache…") }; return@LazyColumn }
        val total = list.sumOf { it.cacheBytes }
        val safe = list.filter { it.safety == CacheSafety.SAFE }.sumOf { it.cacheBytes }
        item {
            GlassCard(Modifier.fillMaxWidth(), highlight = true) {
                Text("Cache totale: ${Fmt.size(total)}", color = J.Text, style = MaterialTheme.typography.titleLarge)
                Text("Di cui ${Fmt.size(safe)} sicura da svuotare senza perdere nulla.", color = J.Green, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                GradientButton("Svuota tutte le cache", Modifier.fillMaxWidth()) {
                    runCatching { clearAll.launch(AppCache.clearAllIntent()) }.onFailure { vm.toast = "Il telefono non supporta lo svuotamento globale" }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android chiede conferma e svuota la cache di tutte le app insieme. La cache sono solo file temporanei: " +
                        "non perdi foto, chat o accessi. Le app \"Attenzione\" dovranno solo riscaricare contenuti (mappe, musica, video). " +
                        "Per svuotarne solo alcune, tocca l'app e premi \"Svuota cache\".",
                    color = J.Muted, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { SectionTitle("App per peso della cache") }
        items(list.filter { it.cacheBytes > 1_000_000 }, key = { it.pkg }) { a ->
            Row(
                Modifier.fillMaxWidth().clickable { ctx.startActivity(AppCache.appDetailsIntent(a.pkg)) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = remember(a.pkg) { a.icon?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() } }
                if (icon != null) Image(icon, null, Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = J.Text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (a.safety == CacheSafety.SAFE) J.Green else J.Amber)
                        Spacer(Modifier.width(6.dp))
                        Text(a.safety.label, color = if (a.safety == CacheSafety.SAFE) J.Green else J.Amber, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(a.why, color = J.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(Fmt.size(a.cacheBytes), color = J.Electric, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

// ---------------- File inutili ----------------

@Composable
private fun JunkTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    var junkItems by remember { mutableStateOf<List<JunkItem>?>(null) }
    val sel = remember { mutableStateListOf<String>() }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        junkItems = null
        val r = withContext(Dispatchers.IO) { Cleaner.junk(ctx) }
        junkItems = r; sel.clear(); sel.addAll(r.map { it.rel })
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
            val list = junkItems
            when {
                list == null -> item { Loading("Cerco file inutili…") }
                list.isEmpty() -> item { EmptyState("Tutto pulito", "Nessun file temporaneo, APK superfluo o cartella vuota.") }
                else -> items(list, key = { it.rel }) { j ->
                    SelectRow(j.rel in sel, { if (j.rel in sel) sel.remove(j.rel) else sel.add(j.rel) }) {
                        Column(Modifier.weight(1f)) {
                            Text(j.rel, color = J.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(j.reason, color = J.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(Fmt.size(j.size), color = J.Electric, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        val map = junkItems?.associate { it.rel to it.size } ?: emptyMap()
        TrashBar(vm, sel.mapNotNull { r -> map[r]?.let { r to it } }, "Pulizia file inutili") { reload++ }
    }
}

// ---------------- Doppioni ----------------

@Composable
private fun DupTab(vm: MainViewModel) {
    var groups by remember { mutableStateOf<List<DupGroup>?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    val sel = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    fun start() {
        running = true
        scope.launch {
            val g = withContext(Dispatchers.IO) { Cleaner.findDuplicates { d, t -> progress = "$d / $t file confrontati" } }
            groups = g
            sel.clear()
            // Tiene il più vecchio (l'originale), seleziona le copie
            g.forEach { grp -> sel.addAll(grp.files.drop(1).map { it.rel }) }
            running = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
            val list = groups
            when {
                running -> item { Loading("Confronto i file… $progress") }
                list == null -> item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("Trova file identici", color = J.Text, style = MaterialTheme.typography.titleMedium)
                        Text("Confronta il contenuto dei file (non solo il nome): trova copie esatte di foto, video, documenti.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        GradientButton("Cerca doppioni", icon = Icons.Filled.Search) { start() }
                    }
                }
                list.isEmpty() -> item { EmptyState("Nessun doppione", "Non ci sono file identici.") }
                else -> {
                    item { Text("${list.size} gruppi · puoi liberare ${Fmt.size(list.sumOf { it.wasted })}. Ho selezionato le copie e tenuto l'originale più vecchio.", color = J.Muted, style = MaterialTheme.typography.bodySmall) }
                    list.forEachIndexed { gi, g ->
                        item(key = "g$gi") { SectionTitle("${g.files.size} copie · ${Fmt.size(g.size)} ciascuna") }
                        fileRows(g.files, sel)
                    }
                }
            }
        }
        val sizes = groups?.flatMap { g -> g.files.map { it.rel to it.size } }?.toMap() ?: emptyMap()
        TrashBar(vm, sel.mapNotNull { r -> sizes[r]?.let { r to it } }, "Doppioni") { groups = null; sel.clear() }
    }
}

// ---------------- WhatsApp ----------------

@Composable
private fun WaTab(vm: MainViewModel) {
    var cats by remember { mutableStateOf<List<WaCategory>?>(null) }
    var age by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    val sel = remember { mutableStateListOf<String>() }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) { cats = withContext(Dispatchers.IO) { Cleaner.whatsapp() } }
    val limit = System.currentTimeMillis() - age * 86_400_000L
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(0 to "Tutti", 30 to "Più di 1 mese", 90 to "Più di 3 mesi", 365 to "Più di 1 anno").forEach { (d, l) ->
                        Chip(l, selected = age == d) { age = d; sel.clear() }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            val list = cats
            when {
                list == null -> item { Loading("Analizzo WhatsApp…") }
                list.isEmpty() -> item { EmptyState("Niente da pulire", "Non trovo file multimediali di WhatsApp.") }
                else -> list.forEach { c ->
                    val files = c.files.filter { age == 0 || it.modified < limit }
                    item(key = c.key) {
                        GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { open = if (open == c.key) null else c.key }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.title, color = J.Text, style = MaterialTheme.typography.titleSmall)
                                    Text("${files.size} file · ${Fmt.size(files.sumOf { it.size })}", color = J.Electric, style = MaterialTheme.typography.bodySmall)
                                    Text(c.note, color = J.Muted, style = MaterialTheme.typography.bodySmall)
                                }
                                val all = files.isNotEmpty() && files.all { it.rel in sel }
                                GhostButton(if (all) "Deseleziona" else "Seleziona") {
                                    if (all) sel.removeAll(files.map { it.rel }.toSet()) else sel.addAll(files.map { it.rel }.filter { it !in sel })
                                }
                            }
                        }
                    }
                    if (open == c.key) fileRows(files.take(300), sel)
                }
            }
        }
        val sizes = cats?.flatMap { it.files }?.associate { it.rel to it.size } ?: emptyMap()
        TrashBar(vm, sel.mapNotNull { r -> sizes[r]?.let { r to it } }, "Pulizia WhatsApp") { sel.clear(); reload++ }
    }
}

// ---------------- Grandi e vecchi ----------------

@Composable
private fun BigTab(vm: MainViewModel) {
    var mode by remember { mutableStateOf(0) }
    var files by remember { mutableStateOf<List<FileEntry>?>(null) }
    val sel = remember { mutableStateListOf<String>() }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(mode, reload) {
        files = null
        files = withContext(Dispatchers.IO) { if (mode == 0) Cleaner.largest() else Cleaner.oldUntouched() }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("I più grandi", mode == 0) { mode = 0; sel.clear() }
                    Chip("Non modificati da 1 anno", mode == 1) { mode = 1; sel.clear() }
                }
                Spacer(Modifier.height(8.dp))
            }
            val list = files
            if (list == null) item { Loading("Cerco…") } else fileRows(list, sel)
        }
        val sizes = files?.associate { it.rel to it.size } ?: emptyMap()
        TrashBar(vm, sel.mapNotNull { r -> sizes[r]?.let { r to it } }, "File grandi e vecchi") { sel.clear(); reload++ }
    }
}

// ---------------- Screenshot e foto ----------------

@Composable
private fun ShotsTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(0) }
    var paths by remember { mutableStateOf<List<FileEntry>?>(null) }
    val sel = remember { mutableStateListOf<String>() }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(mode, reload) {
        paths = null
        paths = withContext(Dispatchers.IO) {
            when (mode) {
                0 -> Cleaner.oldScreenshots(30)
                else -> {
                    val (dark, blurry) = Cleaner.questionablePhotos(ctx)
                    (if (mode == 1) blurry else dark).mapNotNull { p ->
                        val f = Storage.file(p); if (f.exists()) FileEntry(p, f.length(), f.lastModified()) else null
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Chip("Screenshot > 30 giorni", mode == 0) { mode = 0; sel.clear() }
                    Chip("Foto sfocate", mode == 1) { mode = 1; sel.clear() }
                    Chip("Foto troppo scure", mode == 2) { mode = 2; sel.clear() }
                }
                if (mode != 0) Text(
                    "Si basa sull'analisi foto (scheda Foto). Controlla sempre prima di eliminare: il riconoscimento non è perfetto.",
                    color = J.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            val list = paths
            when {
                list == null -> item { Loading("Cerco…") }
                list.isEmpty() -> item { EmptyState("Niente da rivedere", "Nessuna foto in questa categoria.") }
                else -> {
                    item { GhostButton("Seleziona tutte (${list.size})") { sel.clear(); sel.addAll(list.map { it.rel }) } }
                    fileRows(list, sel)
                }
            }
        }
        val sizes = paths?.associate { it.rel to it.size } ?: emptyMap()
        TrashBar(vm, sel.mapNotNull { r -> sizes[r]?.let { r to it } }, "Screenshot e foto") { sel.clear(); reload++ }
    }
}
