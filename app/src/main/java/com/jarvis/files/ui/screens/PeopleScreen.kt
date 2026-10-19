package com.jarvis.files.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.files.photos.FaceCluster
import com.jarvis.files.photos.People
import com.jarvis.files.photos.Person
import com.jarvis.files.photos.PhotoDb
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.components.ArcReactor
import com.jarvis.files.ui.components.Chip
import com.jarvis.files.ui.components.EmptyState
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.SectionTitle
import com.jarvis.files.ui.theme.J
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SubHeader(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = J.Text) }
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = J.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = J.Muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeopleScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var persons by remember { mutableStateOf(emptyList<Person>()) }
    var clusters by remember { mutableStateOf(emptyList<FaceCluster>()) }
    var naming by remember { mutableStateOf<FaceCluster?>(null) }
    var renaming by remember { mutableStateOf<Person?>(null) }
    var reviewing by remember { mutableStateOf<Person?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        withContext(Dispatchers.IO) {
            persons = PhotoDb.get(ctx).persons()
            clusters = People.clusterUnknown(ctx, minSize = 2).take(60)
        }
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(14.dp)) {
        item { SubHeader("Persone", "Dai un nome ai volti: poi chiedi \"foto di Leo\"") { vm.sub = Sub.NONE } }
        if (loading) item {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                ArcReactor(36.dp); Spacer(Modifier.width(12.dp)); Text("Raggruppo i volti…", color = J.Cyan)
            }
        }
        if (persons.isNotEmpty()) {
            item { SectionTitle("Con nome") }
            items(persons, key = { "p${it.id}" }) { p ->
                GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { reviewing = p }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FaceAvatar(p.coverFaceId, 52.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = J.Text, style = MaterialTheme.typography.titleMedium)
                            Text("${p.count} volti · tocca per controllare", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { renaming = p }) { Icon(Icons.Filled.Edit, "Rinomina", tint = J.Cyan) }
                    }
                }
            }
        }
        item { SectionTitle("Volti senza nome") }
        if (!loading && clusters.isEmpty()) item {
            EmptyState("Nessun gruppo di volti", "Avvia l'analisi delle foto dalla scheda Foto. I volti simili compariranno qui.")
        }
        items(clusters, key = { "c${it.id}" }) { cl ->
            GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { naming = cl }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cl.faces.take(6).forEach { FaceAvatar(it.id, 46.dp) }
                }
                Spacer(Modifier.height(8.dp))
                Text("${cl.faces.size} foto · Chi è? Tocca per dare un nome", color = J.Cyan, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    naming?.let { cl ->
        NameDialog(
            title = "Chi è?",
            existing = persons.map { it.name },
            onDismiss = { naming = null },
        ) { name ->
            naming = null
            scope.launch {
                withContext(Dispatchers.IO) { People.nameFaces(ctx, cl.faces.map { it.id }, name) }
                vm.toast = "Salvato: $name. Ho cercato anche altre sue foto."
                reload++
            }
        }
    }
    renaming?.let { p ->
        NameDialog(title = "Nuovo nome", existing = emptyList(), initial = p.name, onDismiss = { renaming = null }) { name ->
            renaming = null
            scope.launch {
                withContext(Dispatchers.IO) {
                    val other = PhotoDb.get(ctx).personByName(name)
                    if (other != null && other != p.id) People.mergePersons(ctx, other, p.id) else PhotoDb.get(ctx).renamePerson(p.id, name)
                }
                reload++
            }
        }
    }
    reviewing?.let { p ->
        ReviewDialog(p, onDismiss = { reviewing = null; reload++ })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameDialog(title: String, existing: List<String>, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = J.Card,
        title = { Text(title, color = J.Text) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("Nome (es. Leo)") })
                if (existing.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Oppure è già…", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        existing.forEach { Chip(it, selected = it == name) { name = it } }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/** Controllo dei volti assegnati: si tolgono quelli sbagliati ("Non è lui"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewDialog(p: Person, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var faces by remember { mutableStateOf(emptyList<Long>()) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        faces = withContext(Dispatchers.IO) { PhotoDb.get(ctx).faces("person_id = ?", arrayOf(p.id.toString())).map { it.id } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = J.Card,
        title = { Text("${p.name} · ${faces.size} volti", color = J.Text) },
        text = {
            Column {
                Text("Tocca la ✕ sui volti che NON sono ${p.name}.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.height(380.dp)) {
                    items(faces.chunked(4)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            row.forEach { id ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    FaceAvatar(id, 56.dp)
                                    Icon(Icons.Filled.Close, "Non è lui/lei", tint = J.Red, modifier = Modifier.clickable {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { People.rejectFace(ctx, id, p.id) }
                                            reload++
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fatto") } },
        dismissButton = {
            TextButton(onClick = {
                scope.launch { withContext(Dispatchers.IO) { PhotoDb.get(ctx).deletePerson(p.id) }; onDismiss() }
            }) { Text("Elimina persona", color = J.Red) }
        },
    )
}
