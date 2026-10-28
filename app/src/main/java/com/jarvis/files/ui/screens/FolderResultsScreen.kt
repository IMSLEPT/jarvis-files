package com.jarvis.files.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.Tab
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.IconBadge
import com.jarvis.files.ui.components.pressable
import com.jarvis.files.ui.theme.J

@Composable
internal fun FolderResultRow(path: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("folder_result_$path").pressable(onClick)
        .padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Filled.FolderOpen)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(path.substringAfterLast('/'), color = J.Text, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(path, color = J.Muted, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Apri cartella", tint = J.Muted, modifier = Modifier.size(18.dp))
    }
}

/** All actual matches stay reachable, including results beyond the chat's compact preview. */
@Composable
fun FolderResultsScreen(vm: MainViewModel) {
    fun back() { vm.sub = Sub.NONE; vm.tab = Tab.CHAT }
    BackHandler { back() }
    val result = vm.folderResults
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Torna alla chat", tint = J.Text) }
            Column(Modifier.weight(1f)) {
                Text(if (result?.emptyOnly != false) "Cartelle vuote" else "Cartelle", style = MaterialTheme.typography.titleLarge, color = J.Text)
                Text((if (result?.paths?.size == 1) "1 risultato" else "${result?.paths?.size ?: 0} risultati") + " · ${result?.scope?.ifEmpty { "Memoria interna" } ?: ""}",
                    color = J.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("Risultati dell’ultima verifica. Apri una cartella per vederne il contenuto attuale e gestirla.",
            color = J.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("folder_results_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(result?.paths.orEmpty(), key = { it }) { path ->
                GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(4.dp)) { FolderResultRow(path) { vm.openBrowser(path) } }
            }
        }
    }
}
