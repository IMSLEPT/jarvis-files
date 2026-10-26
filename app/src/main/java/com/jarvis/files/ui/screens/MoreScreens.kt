package com.jarvis.files.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.files.ai.Brain
import com.jarvis.files.automation.AutoType
import com.jarvis.files.automation.Automation
import com.jarvis.files.automation.Automations
import com.jarvis.files.automation.Freq
import com.jarvis.files.core.AppCache
import com.jarvis.files.core.AppUsage
import com.jarvis.files.core.DeviceInfo
import com.jarvis.files.core.FileEntry
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.Prefs
import com.jarvis.files.core.Storage
import com.jarvis.files.core.Trash
import com.jarvis.files.core.TrashItem
import com.jarvis.files.core.Vault
import com.jarvis.files.security.Finding
import com.jarvis.files.security.SecurityScanner
import com.jarvis.files.security.Severity
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.components.ArcReactor
import com.jarvis.files.ui.components.Chip
import com.jarvis.files.ui.components.Dot
import com.jarvis.files.ui.components.EmptyState
import com.jarvis.files.ui.components.FileThumb
import com.jarvis.files.ui.components.GhostButton
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.GradientButton
import com.jarvis.files.ui.components.LinearMeter
import com.jarvis.files.ui.components.SectionTitle
import com.jarvis.files.ui.components.StatTile
import com.jarvis.files.ui.theme.J
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

@Composable
fun MoreScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(18.dp)) {
        Text("Altro", style = MaterialTheme.typography.headlineLarge, color = J.Text)
        Spacer(Modifier.height(10.dp))
        MoreItem(Icons.Filled.Security, "Sicurezza", "App sospette, APK, impostazioni a rischio") { vm.sub = Sub.SECURITY }
        MoreItem(Icons.Filled.Schedule, "Automazioni", "Riordini, scansioni e avvisi programmati da te") { vm.sub = Sub.AUTOMATIONS }
        MoreItem(Icons.Filled.Search, "Trova documenti", "\"la bolletta della luce di marzo\"") { vm.sub = Sub.FIND }
        MoreItem(Icons.Filled.Face, "Persone", "Dai un nome ai volti nelle foto") { vm.sub = Sub.PEOPLE }
        MoreItem(Icons.Filled.Lock, "Cartella segreta", "Documenti privati protetti dall'impronta") { vm.sub = Sub.VAULT }
        MoreItem(Icons.Filled.BatteryChargingFull, "Batteria", "Stato e app più usate") { vm.sub = Sub.BATTERY }
        MoreItem(Icons.Filled.Delete, "Cestino", "Recupera i file eliminati (30 giorni)") { vm.sub = Sub.TRASH }
        MoreItem(Icons.Filled.Settings, "Impostazioni", "AI, blocco, soglie, permessi") { vm.sub = Sub.SETTINGS }
    }
}

@Composable
private fun MoreItem(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.jarvis.files.ui.components.IconBadge(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = J.Text, style = MaterialTheme.typography.titleMedium)
                Text(sub, color = J.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = J.Muted, style = MaterialTheme.typography.titleLarge)
        }
    }
}

// ---------------- Sicurezza ----------------

@Composable
fun SecurityScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var findings by remember { mutableStateOf<List<Finding>?>(null) }
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }
    fun start() {
        running = true
        scope.launch {
            findings = SecurityScanner.scan(ctx) { phase = it }
            running = false
        }
    }
    LaunchedEffect(Unit) { if (findings == null) start() }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(14.dp)) {
        item { SubHeader("Sicurezza", "Controllo del telefono e delle app") { vm.sub = Sub.NONE } }
        val list = findings
        if (running || list == null) {
            item {
                Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArcReactor(110.dp); Spacer(Modifier.height(14.dp)); Text("Scansione in corso… $phase", color = J.Cyan)
                }
            }
            return@LazyColumn
        }
        val high = list.count { it.severity == Severity.HIGH }
        val med = list.count { it.severity == Severity.MEDIUM }
        item {
            GlassCard(Modifier.fillMaxWidth(), highlight = true) {
                Text(
                    when { high > 0 -> "Attenzione, signore"; med > 0 -> "Qualche punto da verificare"; else -> "Tutto in ordine" },
                    color = if (high > 0) J.Red else if (med > 0) J.Amber else J.Green, style = MaterialTheme.typography.headlineSmall,
                )
                Text("$high gravi · $med da verificare · ${list.size - high - med} informazioni", color = J.Muted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Ripeti") { start() }
                    GhostButton("Spiegamelo", icon = Icons.Filled.AutoAwesome) {
                        vm.openChatWith("Ho fatto il controllo sicurezza. Risultati: " +
                            list.take(15).joinToString("; ") { "[${it.severity.label}] ${it.title}: ${it.detail}" } +
                            ". Spiegami cosa devo fare, in ordine di priorità.")
                    }
                }
                Text(
                    "Nota: Android non permette alle app di fare da antivirus in tempo reale. Jarvis controlla i segnali di rischio; " +
                        "per verificare le app con 70 antivirus aggiungi una chiave gratuita VirusTotal nelle Impostazioni. Play Protect resta attivo.",
                    color = J.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        if (list.isEmpty()) item { EmptyState("Nessun problema trovato", "Il telefono sembra in ordine.") }
        items(list) { f ->
            GlassCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(when (f.severity) { Severity.HIGH -> J.Red; Severity.MEDIUM -> J.Amber; Severity.LOW -> J.Cyan; Severity.INFO -> J.Green }, 10.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(f.title, color = J.Text, style = MaterialTheme.typography.titleSmall)
                }
                Text(f.detail, color = J.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                val action = f.action
                val file = f.file
                if (action != null || file != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (action != null) GhostButton(f.actionLabel ?: "Apri") {
                        runCatching { ctx.startActivity(action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    if (file != null) GhostButton("Nel cestino", color = J.Red) {
                        vm.runPlan(com.jarvis.files.core.Plan(listOf(com.jarvis.files.core.Op.Trash(file, Storage.file(file).length())), emptyList()), "Sicurezza") { start() }
                    }
                }
            }
        }
    }
}

// ---------------- Batteria ----------------

@Composable
fun BatteryScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val b = remember { DeviceInfo.battery(ctx) }
    var usage by remember { mutableStateOf<List<AppUsage>?>(null) }
    val hasAccess = remember { AppCache.hasUsageAccess(ctx) }
    LaunchedEffect(Unit) { if (hasAccess) usage = withContext(Dispatchers.IO) { AppCache.usage(ctx) } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(14.dp)) {
        item { SubHeader("Batteria", "Stato e consumi") { vm.sub = Sub.NONE } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${b.level}%", if (b.charging) "In carica" else "Carica", Modifier.weight(1f), if (b.level < 20) J.Red else J.Green)
                StatTile("${b.tempC}°C", "Temperatura", Modifier.weight(1f), if (b.tempC > 40) J.Amber else J.Cyan)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(b.health, "Salute", Modifier.weight(1f))
                StatTile(b.cycles?.toString() ?: "n/d", "Cicli di ricarica", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            GradientButton("Come miglioro la batteria?", Modifier.fillMaxWidth(), Icons.Filled.AutoAwesome) {
                vm.openChatWith(
                    "Analizza la mia batteria e dammi consigli concreti. App più usate oggi: " +
                        (usage?.take(8)?.joinToString { "${it.name} ${it.foregroundMs / 60000} min" } ?: "non disponibili")
                )
            }
            SectionTitle("App più usate nelle ultime 24 ore")
            Text(
                "Android non dà alle app il consumo esatto in %: il tempo di utilizzo è l'indicatore più affidabile. " +
                    "Il dettaglio ufficiale è in Impostazioni → Batteria.",
                color = J.Muted, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            if (!hasAccess) {
                GhostButton("Apri il consumo batteria di Android", icon = Icons.Filled.BatteryChargingFull) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                Spacer(Modifier.height(10.dp))
                com.jarvis.files.ui.components.UsageAccessGuide { ctx.startActivity(AppCache.usageAccessIntent()) }
            }
        }
        val list = usage ?: emptyList()
        val max = list.maxOfOrNull { it.foregroundMs }?.coerceAtLeast(1) ?: 1
        items(list.take(20)) { u ->
            Column(Modifier.fillMaxWidth().clickable { ctx.startActivity(AppCache.appDetailsIntent(u.pkg)) }.padding(vertical = 8.dp)) {
                Row {
                    Text(u.name, color = J.Text, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${u.foregroundMs / 3_600_000}h ${(u.foregroundMs / 60_000) % 60}m", color = J.Electric)
                }
                LinearMeter(u.foregroundMs.toFloat() / max, Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ---------------- Cestino ----------------

@Composable
fun TrashScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var trashItems by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var confirmEmpty by remember { mutableStateOf(false) }
    LaunchedEffect(reload) { trashItems = withContext(Dispatchers.IO) { Trash.items(ctx) } }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(14.dp)) {
        item { SubHeader("Cestino", "${trashItems.size} elementi · ${Fmt.size(trashItems.sumOf { it.size })}") { vm.sub = Sub.NONE } }
        if (trashItems.isEmpty()) item { EmptyState("Cestino vuoto", "I file eliminati da Jarvis restano qui per 30 giorni.") }
        else item {
            GhostButton("Svuota definitivamente", icon = Icons.Filled.DeleteForever, color = J.Red) { confirmEmpty = true }
            Spacer(Modifier.height(8.dp))
        }
        items(trashItems, key = { it.id }) { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                FileThumb(File(Storage.file(Storage.TRASH_DIR), t.id), 44.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.original.substringAfterLast('/'), color = J.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val left = (Prefs(ctx).trashDays - ((System.currentTimeMillis() - t.deletedAt) / 86_400_000L)).coerceAtLeast(0)
                    Text("${Fmt.size(t.size)} · da ${t.original.substringBeforeLast('/', "radice")} · ancora $left giorni", color = J.Muted,
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = {
                    scope.launch { val ok = withContext(Dispatchers.IO) { Trash.restore(ctx, t.id) }; vm.toast = if (ok) "Ripristinato" else "Ripristino non riuscito"; vm.filesChanged(); reload++ }
                }) { Icon(Icons.Filled.Restore, "Ripristina", tint = J.Green) }
            }
        }
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false }, containerColor = J.Card,
        title = { Text("Svuotare il cestino?", color = J.Text) },
        text = { Text("I file verranno eliminati per sempre e non si potranno recuperare.", color = J.Muted) },
        confirmButton = { TextButton(onClick = { confirmEmpty = false; scope.launch { withContext(Dispatchers.IO) { Trash.emptyAll(ctx) }; vm.filesChanged(); reload++ } }) { Text("Svuota", color = J.Red) } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Annulla") } },
    )
}

// ---------------- Cartella segreta ----------------

@Composable
fun VaultScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var authorized by remember { mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) authorized = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!authorized) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            SubHeader("Cartella segreta") { vm.sub = Sub.NONE }
            Spacer(Modifier.height(24.dp))
            Text("I tuoi file privati", color = J.Text, style = MaterialTheme.typography.headlineSmall)
            Text("Usa impronta o PIN per visualizzarli.", color = J.Muted, modifier = Modifier.padding(vertical = 16.dp))
            GradientButton("Sblocca cartella", Modifier.fillMaxWidth(), icon = Icons.Filled.Lock) {
                (ctx as? com.jarvis.files.MainActivity)?.authenticateVault { authorized = true }
            }
        }
        return
    }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) { files = withContext(Dispatchers.IO) { Vault.list(ctx) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            val n = withContext(Dispatchers.IO) { uris.count { Vault.importUri(ctx, it) } }
            vm.toast = "$n file nella cartella segreta"
            vm.filesChanged()
            reload++
        }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(14.dp)) {
        item {
            SubHeader("Cartella segreta", "Invisibile a galleria e altre app") { vm.sub = Sub.NONE }
            GlassCard(Modifier.fillMaxWidth(), highlight = true) {
                Text(
                    "I file vengono spostati nella memoria privata di Jarvis (cifrata da Android) e si aprono solo dopo l'impronta. " +
                        "Attenzione: se disinstalli l'app la cartella segreta viene cancellata. Esporta prima i file.",
                    color = J.Muted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                GradientButton("Aggiungi file", icon = Icons.Filled.Add) { picker.launch(arrayOf("*/*")) }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (files.isEmpty()) item { EmptyState("Vuota", "Aggiungi qui documenti d'identità, contratti, foto private.") }
        items(files, key = { it.name }) { f ->
            Row(Modifier.fillMaxWidth().clickable { Storage.openFile(ctx, f) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                FileThumb(f, 44.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, color = J.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${Fmt.size(f.length())} · ${Fmt.date(f.lastModified())}", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { Storage.shareFiles(ctx, listOf(f)) }) { Icon(Icons.Filled.Share, "Condividi", tint = J.Cyan) }
                IconButton(onClick = {
                    scope.launch { val r = withContext(Dispatchers.IO) { Vault.export(ctx, f) }; vm.toast = if (r != null) "Esportato in Download/Jarvis Esportati" else "Esportazione non riuscita"; vm.filesChanged(); reload++ }
                }) { Icon(Icons.Filled.Upload, "Esporta", tint = J.Cyan) }
            }
        }
    }
}

// ---------------- Trova documenti ----------------

@Composable
fun FindScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<FileEntry>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun search() {
        searchJob?.cancel()
        val text = q.trim()
        if (text.isBlank()) { busy = false; res = null; return }
        busy = true
        searchJob = scope.launch {
            try {
            res = withContext(Dispatchers.IO) {
                // Prima prova con l'AI (capisce "di marzo", "della luce"...), altrimenti ricerca per parole
                val ai = runCatching {
                    val engine = com.jarvis.files.ai.AiRouter.engine(ctx)
                    val sys = "Converti la richiesta in JSON per cercare file per nome. Rispondi SOLO JSON: " +
                        "{\"keywords\":[parole probabili nel nome file, con sinonimi e nomi di aziende, es. enel, luce, bolletta, fattura],\"ext\":[estensioni],\"folder\":\"\",\"from\":\"AAAA-MM-GG\",\"to\":\"AAAA-MM-GG\"}. Oggi è ${Fmt.iso(System.currentTimeMillis())}."
                    com.jarvis.files.ai.extractJson(engine.complete(sys, listOf(com.jarvis.files.ai.ChatMsg("user", text)), 300))
                }.getOrNull()
                val query = ai?.let { Brain.parseFileQuery(it) } ?: Brain.simpleQuery(text)
                Brain.searchFiles(query).ifEmpty { Brain.searchFiles(Brain.simpleQuery(text)) }
            }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { vm.toast = "Ricerca non riuscita: ${e.message}" }
            finally { if (kotlinx.coroutines.currentCoroutineContext().isActive) busy = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(14.dp)) {
        item {
            SubHeader("Trova documenti", "Scrivi come lo diresti a voce") { vm.sub = Sub.NONE }
            OutlinedTextField(
                value = q, onValueChange = { q = it; if (it.isBlank()) search() }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("es. bolletta della luce di marzo", color = J.Muted) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = J.Cyan) },
                trailingIcon = { IconButton(onClick = { search() }) { Icon(Icons.Filled.Search, "Cerca documenti", tint = J.Blue) } },
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = J.Text, unfocusedTextColor = J.Text, focusedBorderColor = J.Blue, unfocusedBorderColor = J.Stroke),
            )
            Spacer(Modifier.height(8.dp))
            Text("Cerca nei nomi, nelle cartelle e nelle date dei file. Per le foto usa la scheda Foto.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        if (busy) item { Row(verticalAlignment = Alignment.CenterVertically) { ArcReactor(32.dp); Spacer(Modifier.width(10.dp)); Text("Cerco…", color = J.Cyan) } }
        res?.let { list ->
            if (list.isEmpty()) item { EmptyState("Nessun risultato", "Prova con altre parole (es. il nome dell'azienda).") }
            item { if (list.isNotEmpty()) SectionTitle("${list.size} risultati") }
            items(list, key = { it.rel }) { e ->
                Row(Modifier.fillMaxWidth().clickable { Storage.openFile(ctx, Storage.file(e.rel)) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FileThumb(Storage.file(e.rel), 44.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, color = J.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${e.folder} · ${Fmt.date(e.modified)} · ${Fmt.size(e.size)}", color = J.Muted, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ---------------- Automazioni ----------------

@Composable
fun AutomationsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(Automations.all(ctx)) }
    var editing by remember { mutableStateOf<Automation?>(null) }
    fun reload() { list = Automations.all(ctx) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(14.dp)) {
        item {
            SubHeader("Automazioni", "Decidi tu cosa fa Jarvis e quando") { vm.sub = Sub.NONE }
            GradientButton("Nuova automazione", Modifier.fillMaxWidth(), Icons.Filled.Add) {
                editing = Automation(System.currentTimeMillis(), AutoType.SORT_DOWNLOAD, Freq.WEEKLY, 9, 0, Calendar.MONDAY, true)
            }
            Text(
                "Android può spostare l'orario di qualche minuto per risparmiare batteria. L'analisi foto parte solo in carica (modificabile nelle Impostazioni).",
                color = J.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 10.dp),
            )
        }
        items(list, key = { it.id }) { a ->
            GlassCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = { editing = a }, highlight = a.enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.type.title, color = J.Text, style = MaterialTheme.typography.titleMedium)
                        if (a.type == AutoType.AI_COMMAND) Text("\"${a.prompt}\"", color = J.Cyan, style = MaterialTheme.typography.bodySmall)
                        Text(a.whenLabel(), color = J.Electric, style = MaterialTheme.typography.bodySmall)
                        if (a.lastRun > 0) Text("Ultima: ${Fmt.date(a.lastRun)} · ${a.lastResult}", color = J.Muted, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Switch(
                        checked = a.enabled,
                        onCheckedChange = { Automations.upsert(ctx, a.copy(enabled = it)); reload() },
                        colors = SwitchDefaults.colors(checkedTrackColor = J.Blue),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    GhostButton("Esegui ora", icon = Icons.Filled.PlayArrow) {
                        if (a.type == AutoType.PHOTO_INDEX) Automations.indexPhotosNow(ctx) else Automations.runNow(ctx, a.id)
                        vm.toast = "Avviata: ${a.type.title}"
                    }
                }
            }
        }
    }
    editing?.let { a -> AutomationDialog(a, isNew = list.none { it.id == a.id }, onDismiss = { editing = null }, onDelete = {
        Automations.delete(ctx, a.id); editing = null; reload()
    }) { Automations.upsert(ctx, it); editing = null; reload() } }
}

@Composable
private fun AutomationDialog(initial: Automation, isNew: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Automation) -> Unit) {
    var a by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = J.Card,
        title = { Text(if (isNew) "Nuova automazione" else "Modifica", color = J.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Cosa deve fare", color = J.Cyan, style = MaterialTheme.typography.labelLarge)
                AutoType.entries.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { a = a.copy(type = t) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (a.type == t) J.Electric else J.CardHi, 12.dp)
                        Spacer(Modifier.width(8.dp))
                        Column { Text(t.title, color = J.Text); Text(t.desc, color = J.Muted, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (a.type == AutoType.AI_COMMAND) {
                    OutlinedTextField(value = a.prompt, onValueChange = { a = a.copy(prompt = it) }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("es. sposta i PDF di Download in Documenti") })
                }
                Spacer(Modifier.height(10.dp))
                Text("Quando", color = J.Cyan, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    Freq.entries.forEach { f -> Chip(f.label.removePrefix("Ogni "), a.freq == f) { a = a.copy(freq = f) } }
                }
                if (a.freq == Freq.WEEKLY) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Automation.DAYS.entries.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { (d, name) -> Chip(name.take(3), a.dayOfWeek == d) { a = a.copy(dayOfWeek = d) } }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Ora", color = J.Text, modifier = Modifier.width(50.dp))
                    GhostButton("−") { a = a.copy(hour = (a.hour + 23) % 24) }
                    Text("  %02d:%02d  ".format(a.hour, a.minute), color = J.Electric, style = MaterialTheme.typography.titleLarge)
                    GhostButton("+") { a = a.copy(hour = (a.hour + 1) % 24) }
                    Spacer(Modifier.width(6.dp))
                    GhostButton(":${"%02d".format((a.minute + 15) % 60)}") { a = a.copy(minute = (a.minute + 15) % 60) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = a.type != AutoType.AI_COMMAND || a.prompt.isNotBlank(), onClick = { onSave(a) }) { Text("Salva") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Elimina", color = J.Red) }
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        },
    )
}

