package com.jarvis.files.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.files.core.*
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.components.*
import com.jarvis.files.ui.theme.J
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val path = vm.browserPath
    var nodes by remember { mutableStateOf<List<PhoneNode>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var hidden by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable(path) { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val selection = remember(path) { mutableStateListOf<String>() }
    var rowMenu by remember(path) { mutableStateOf<String?>(null) }
    var edit by remember(path) { mutableStateOf<Pair<String?, String>?>(null) }
    var deleting by remember(path) { mutableStateOf<List<String>?>(null) }
    var details by remember(path) { mutableStateOf<PhoneNode?>(null) }
    val enabled = !vm.busy && !vm.operationBusy
    val listState = rememberLazyListState()
    var loadedKey by remember { mutableStateOf("") }
    val currentKey = "$path|$sort|$hidden|$query"

    fun up() {
        focus.clearFocus()
        if (selection.isNotEmpty()) selection.clear()
        else if (path.isEmpty()) vm.sub = Sub.NONE
        else vm.browserPath = path.substringBeforeLast('/', "")
    }
    BackHandler(selection.isNotEmpty() || path.isNotEmpty()) { up() }

    LaunchedEffect(path, hidden, query, sort, reload, vm.filesRevision) {
        nodes = null; error = null
        try {
            nodes = withContext(Dispatchers.IO) { PhoneFiles.list(path, hidden, query, sort) }
            selection.retainAll(nodes.orEmpty().map { it.rel }.toSet())
            loadedKey = currentKey
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Impossibile leggere la cartella" }
    }
    LaunchedEffect(loadedKey) { if (loadedKey.isNotEmpty()) listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("phone_browser")) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { up() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cartella superiore", tint = J.Text) }
            Column(Modifier.weight(1f)) {
                Text(if (path.isEmpty()) "Memoria interna" else path.substringAfterLast('/'), color = J.Text,
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(nodes?.let { "${it.size} elementi" } ?: "Esplora cartelle", color = J.Muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { edit = null to "" }, enabled = enabled) { Icon(Icons.Filled.CreateNewFolder, "Nuova cartella", tint = J.Blue) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opzioni cartelle", tint = J.Muted) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Aggiorna") }, onClick = { menu = false; reload++ })
                    DropdownMenuItem(text = { Text(if (hidden) "Nascondi file nascosti" else "Mostra file nascosti") }, onClick = { hidden = !hidden; menu = false })
                    listOf("Nome", "Più recenti", "Dimensione").forEachIndexed { i, label ->
                        DropdownMenuItem(text = { Text(if (sort == i) "✓ $label" else label) }, onClick = { sort = i; menu = false })
                    }
                    DropdownMenuItem(text = { Text("Limiti di accesso Android") }, onClick = { menu = false; vm.toast = "Sono disponibili i file condivisi del telefono. Android protegge i dati privati delle app, Android/data e Android/obb." })
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selection.clear(); vm.browserPath = "" }) { Text("Memoria interna", color = J.Blue) }
            var current = ""
            path.split('/').filter { it.isNotEmpty() }.forEach { segment ->
                current = if (current.isEmpty()) segment else "$current/$segment"
                val dest = current
                Text("›", color = J.Muted)
                TextButton(onClick = { selection.clear(); vm.browserPath = dest }) { Text(segment, color = J.Text) }
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 18.dp).testTag("browser_search"),
            placeholder = { Text("Cerca in questa cartella") }, singleLine = true, shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Cancella ricerca cartella") } }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        if (selection.isNotEmpty()) {
            val chosen = nodes.orEmpty().filter { it.rel in selection }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("${selection.size} selezionati", true) { selection.clear() }
                Chip("Tutti") { selection.clear(); selection.addAll(nodes.orEmpty().filter { it.blocked == null }.map { it.rel }) }
                GhostButton("Copia", enabled = enabled) { vm.prepareFileTransfer(selection.toList(), false); selection.clear() }
                GhostButton("Sposta", enabled = enabled && chosen.all { it.mutable }) { vm.prepareFileTransfer(selection.toList(), true); selection.clear() }
                if (chosen.size == 1) GhostButton("Rinomina", enabled = enabled && chosen.single().mutable) { edit = chosen.single().rel to chosen.single().name }
                GhostButton("Cestino", enabled = enabled && chosen.all { it.mutable }) { deleting = selection.toList() }
                if (chosen.all { !it.directory }) GhostButton("Condividi", enabled = enabled) { Storage.shareFiles(ctx, chosen.map { Storage.file(it.rel) }) }
            }
        }
        if (vm.operationBusy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), color = J.Navy)
            if (vm.browserCopyRunning) TextButton(onClick = { vm.stopBrowserCopy() }) { Text("Interrompi copia", color = J.Blue) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("browser_list").semantics { stateDescription = if (nodes == null) "Caricamento" else "Cartella caricata" }, state = listState,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
            when {
                error != null -> item {
                    Text(error!!, color = J.Red, modifier = Modifier.padding(16.dp))
                    GhostButton("Riprova lettura") { reload++ }
                }
                nodes == null -> item { Text("Leggo la cartella…", color = J.Muted, modifier = Modifier.padding(16.dp)) }
                nodes!!.isEmpty() -> item { EmptyState(if (query.isBlank()) "Cartella vuota" else "Nessun risultato", if (query.isBlank()) "Puoi creare una cartella o incollare dei file qui." else "La ricerca riguarda solo questa cartella.") }
                else -> items(nodes!!, key = { it.rel }) { node ->
                    val selected = node.rel in selection
                    fun toggle() { if (node.blocked == null) { if (selected) selection.remove(node.rel) else selection.add(node.rel) } }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (selected) J.AccentSoft else J.Card)
                        .combinedClickable(onClick = {
                            when {
                                node.rel == Storage.TRASH_DIR -> vm.sub = Sub.TRASH
                                node.blocked != null -> vm.toast = node.blocked
                                selection.isNotEmpty() -> toggle()
                                node.directory -> { focus.clearFocus(); vm.browserPath = node.rel }
                                else -> Storage.openFile(ctx, Storage.file(node.rel))
                            }
                        }, onLongClick = { if (enabled) toggle() })
                        .testTag("browser_row_" + node.rel).padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (node.blocked != null && !node.directory) Icon(Icons.Filled.Lock, null, tint = J.Muted, modifier = Modifier.size(44.dp))
                        else if (node.directory) Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(J.AccentSoft), contentAlignment = Alignment.Center) {
                            Icon(if (node.blocked == null) Icons.Filled.Folder else Icons.Filled.FolderOff, null, tint = J.Blue, modifier = Modifier.size(30.dp))
                        } else FileThumb(Storage.file(node.rel), 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(node.name, color = J.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(when {
                                node.blocked != null -> "Accesso limitato"
                                node.directory -> (node.childCount?.let { "$it elementi" } ?: "Cartella") + " · " + Fmt.date(node.modified)
                                else -> Fmt.size(node.bytes) + " · " + Fmt.date(node.modified)
                            }, color = J.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (selected) Icon(Icons.Filled.CheckCircle, "Selezionato", tint = J.Blue, modifier = Modifier.size(22.dp))
                        Box {
                            IconButton(onClick = { rowMenu = node.rel }, enabled = enabled) { Icon(Icons.Filled.MoreVert, "Azioni per ${node.name}", tint = J.Muted) }
                            DropdownMenu(rowMenu == node.rel, onDismissRequest = { rowMenu = null }) {
                                if (node.blocked == null) {
                                    DropdownMenuItem(text = { Text("Seleziona") }, onClick = { toggle(); rowMenu = null })
                                    DropdownMenuItem(text = { Text("Copia") }, onClick = { vm.prepareFileTransfer(listOf(node.rel), false); rowMenu = null })
                                    DropdownMenuItem(text = { Text("Sposta") }, enabled = node.mutable, onClick = { vm.prepareFileTransfer(listOf(node.rel), true); rowMenu = null })
                                    DropdownMenuItem(text = { Text("Rinomina") }, enabled = node.mutable, onClick = { edit = node.rel to node.name; rowMenu = null })
                                    DropdownMenuItem(text = { Text("Nel cestino") }, enabled = node.mutable, onClick = { deleting = listOf(node.rel); rowMenu = null })
                                    if (!node.directory) DropdownMenuItem(text = { Text("Condividi") }, onClick = { Storage.shareFiles(ctx, listOf(Storage.file(node.rel))); rowMenu = null })
                                }
                                DropdownMenuItem(text = { Text("Dettagli") }, onClick = { details = node; rowMenu = null })
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        vm.browserClipboard?.let { clipboard ->
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), padding = PaddingValues(12.dp)) {
                Text("${clipboard.paths.size} elementi da ${if (clipboard.cut) "spostare" else "copiare"}", color = J.Text, style = MaterialTheme.typography.titleSmall)
                Text(if (path.isEmpty()) "Destinazione: Memoria interna" else "Destinazione: $path", color = J.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    GradientButton("Incolla qui", Modifier.weight(1f), enabled = enabled) { focus.clearFocus(); vm.pasteBrowserFiles() }
                    GhostButton("Annulla", enabled = !vm.operationBusy) { vm.cancelFileTransfer() }
                }
            }
        }
        if (vm.browserClipboard == null && vm.undoInfo != null) {
            TextButton(onClick = { vm.undo() }, enabled = enabled, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Annulla ultima modifica", color = J.Blue) }
        }
    }
    edit?.let { (source, initial) ->
        var value by remember(source, initial) { mutableStateOf(initial) }
        var nameError by remember(source, initial) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { edit = null }, containerColor = J.Card,
            title = { Text(if (source == null) "Nuova cartella" else "Rinomina", color = J.Text) },
            text = { Column {
                OutlinedTextField(value, { value = it; nameError = null }, Modifier.fillMaxWidth().testTag("browser_name"), singleLine = true, label = { Text("Nome") }, isError = nameError != null)
                nameError?.let { Text(it, color = J.Red) }
            } },
            confirmButton = { TextButton(enabled = enabled && value.isNotBlank(), onClick = {
                try {
                    PhoneFiles.validName(value)
                    if (source == null) vm.createBrowserFolder(value) else vm.renameBrowserItem(source, value)
                    focus.clearFocus(); edit = null; selection.clear()
                } catch (e: Exception) { nameError = e.message }
            }) { Text("Salva", color = J.Blue) } }, dismissButton = { TextButton(onClick = { edit = null }) { Text("Annulla") } })
    }
    deleting?.let { paths ->
        AlertDialog(onDismissRequest = { deleting = null }, containerColor = J.Card,
            title = { Text("Sposta nel cestino?", color = J.Text) },
            text = { Text("${paths.size} elementi selezionati. Le cartelle includono il loro contenuto. Puoi recuperarli dal Cestino di Jarvis.\n\n" + paths.take(4).joinToString("\n")) },
            confirmButton = { TextButton(enabled = enabled, onClick = { vm.trashBrowserItems(paths); deleting = null; selection.clear() }) { Text("Sposta nel cestino", color = J.Blue) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annulla") } })
    }
    details?.let { node ->
        AlertDialog(onDismissRequest = { details = null }, containerColor = J.Card,
            title = { Text(node.name, color = J.Text) },
            text = { androidx.compose.foundation.text.selection.SelectionContainer { Text(buildString {
                appendLine(if (node.directory) "Cartella" else "File · ${Fmt.size(node.bytes)}")
                appendLine("Modificato: ${Fmt.date(node.modified)}")
                appendLine(Storage.file(node.rel).absolutePath)
                node.blocked?.let { appendLine("\n$it") }
                if (!node.mutable && node.blocked == null) appendLine("\nCartella principale protetta: puoi modificarne il contenuto.")
            }, color = J.Text) } }, confirmButton = { TextButton(onClick = { details = null }) { Text("Chiudi") } })
    }
}
