package com.jarvis.files.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.files.ai.AiRouter
import com.jarvis.files.core.AiMode
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.Prefs
import com.jarvis.files.core.Storage
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.PlanState
import com.jarvis.files.ui.Tab
import com.jarvis.files.ui.UiMsg
import com.jarvis.files.ui.components.ArcReactor
import com.jarvis.files.ui.components.Chip
import com.jarvis.files.ui.components.FileThumb
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.IconBadge
import com.jarvis.files.ui.components.PlanCard
import com.jarvis.files.ui.components.TypingDots
import com.jarvis.files.ui.components.pressable
import com.jarvis.files.ui.theme.J

private data class Suggestion(val icon: ImageVector, val title: String, val prompt: String)

private val SUGGESTIONS = listOf(
    Suggestion(Icons.Filled.FolderOpen, "Metti in ordine i Download", "Metti in ordine la cartella Download"),
    Suggestion(Icons.Filled.Search, "Trova i PDF delle bollette", "Trova i PDF delle bollette"),
    Suggestion(Icons.Filled.Landscape, "Foto di montagna", "Mostrami le foto di montagna"),
    Suggestion(Icons.Filled.CleaningServices, "Libera spazio velocemente", "Come libero spazio velocemente?"),
    Suggestion(Icons.Filled.PhoneAndroid, "Perché il telefono è lento?", "Perché il telefono è lento?"),
)

@Composable
fun ChatScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var input by vm::chatInput
    val list = rememberLazyListState()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val enabled = !vm.busy && !vm.operationBusy
    fun send() {
        if (input.isBlank() || !enabled) return
        val text = input
        input = ""
        focus.clearFocus()
        vm.send(text)
    }
    LaunchedEffect(vm.messages.size, vm.busy) {
        val n = vm.messages.size + if (vm.busy) 1 else 0
        if (n > 0) list.animateScrollToItem(n - 1)
    }
    val p = Prefs(ctx)
    val engineLabel = when (p.aiMode) {
        AiMode.LOCAL -> if (AiRouter.localReady(ctx)) "Locale · sul telefono" else "AI locale da scaricare"
        AiMode.ONLINE -> "Internet · ${p.onlineModel.substringAfterLast('/')}"
        AiMode.AUTO -> "Automatica · preferenza locale"
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ArcReactor(36.dp, active = vm.busy)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Jarvis", style = MaterialTheme.typography.titleLarge, color = J.Text)
                Text(if (vm.busy) vm.busyText else engineLabel, style = MaterialTheme.typography.bodySmall, color = J.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.sub = com.jarvis.files.ui.Sub.SETTINGS }) { Icon(Icons.Filled.Settings, "Impostazioni AI", tint = J.Muted) }
            IconButton(onClick = { vm.clearChat() }, enabled = !vm.operationBusy) { Icon(Icons.Filled.AddComment, "Nuova chat", tint = J.Blue) }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (vm.messages.isEmpty()) item(key = "welcome") {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ArcReactor(128.dp, active = false) }
                    Spacer(Modifier.height(20.dp))
                    Text("Facciamo spazio alle cose importanti.", style = MaterialTheme.typography.headlineMedium, color = J.Text)
                    Spacer(Modifier.height(8.dp))
                    Text("Cerca, organizza o rinomina. Prima di modificare i file vedrai sempre un'anteprima.", style = MaterialTheme.typography.bodyMedium, color = J.Muted)
                    Spacer(Modifier.height(20.dp))
                    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                        SUGGESTIONS.forEach { suggestion ->
                            Row(Modifier.fillMaxWidth().pressable({ vm.send(suggestion.prompt) }, enabled).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(suggestion.icon, size = 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(suggestion.title, color = J.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = J.Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            items(vm.messages, key = { it.id }) { m -> MessageView(vm, m) }
            if (vm.busy) item(key = "typing") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypingDots()
                    Spacer(Modifier.width(12.dp))
                    Text(vm.busyText, color = J.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (vm.operationBusy) Text("Operazione sui file in corso…", color = J.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(26.dp)).background(J.CardHi).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (input.isEmpty()) Text("Chiedi a Jarvis…", color = J.Muted, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = input, onValueChange = { input = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = J.Text), cursorBrush = SolidColor(J.Blue), maxLines = 5,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            if (vm.busy) IconButton(onClick = { vm.stopReply() }) { Icon(Icons.Filled.StopCircle, "Interrompi risposta", tint = J.Blue) }
            else IconButton(onClick = { if (input.isNotBlank()) send() else vm.voiceOpen = true }, enabled = enabled) {
                Icon(if (input.isNotBlank()) Icons.Filled.ArrowUpward else Icons.Filled.Mic,
                    if (input.isNotBlank()) "Invia messaggio" else "Detta un messaggio", tint = if (enabled) J.Blue else J.Muted)
            }
        }
    }
}

@Composable
private fun MessageView(vm: MainViewModel, m: UiMsg) {
    val ctx = LocalContext.current
    if (m.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                m.text, color = Color.White, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(22.dp)).background(J.Blue)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
        return
    }
    // Messaggi di Jarvis: testo libero con piccolo orb, come i moderni assistenti
    Row(Modifier.fillMaxWidth()) {
        ArcReactor(22.dp, active = false, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(m.text, color = if (m.error) J.Red else J.Text, style = MaterialTheme.typography.bodyLarge)
            }
            if (m.error && vm.messages.lastOrNull()?.id == m.id) {
                Spacer(Modifier.height(8.dp))
                com.jarvis.files.ui.components.GhostButton("Riprova") { vm.retryLast() }
            }
            if (m.engine.isNotBlank()) Text(m.engine, style = MaterialTheme.typography.labelSmall, color = J.Muted, modifier = Modifier.padding(top = 4.dp))

            m.questions.forEach { q ->
                Spacer(Modifier.height(12.dp))
                Text(q.question, color = J.Text, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    q.options.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().pressable({ vm.send("${q.question}\nScelgo: $o") }, !vm.busy && !vm.operationBusy && vm.messages.lastOrNull()?.id == m.id).clip(RoundedCornerShape(16.dp)).background(J.Card)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(o, color = J.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = J.Blue, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            m.plan?.let { plan ->
                Spacer(Modifier.height(12.dp))
                when (m.planState) {
                    PlanState.PENDING -> if (plan.isEmpty) {
                        Text(plan.warnings.joinToString("\n"), color = J.Amber, style = MaterialTheme.typography.bodySmall)
                    } else PlanCard(plan, onConfirm = { vm.executePlan(m.id, it) }, onCancel = { vm.cancelPlan(m.id) }, enabled = !vm.busy && !vm.operationBusy)
                    PlanState.RUNNING -> Row(verticalAlignment = Alignment.CenterVertically) { TypingDots(J.Blue); Spacer(Modifier.width(8.dp)); Text("Eseguo…", color = J.Muted) }
                    PlanState.FAILED -> Text("Operazione non completata: vedi i dettagli sotto.", color = J.Red, style = MaterialTheme.typography.bodySmall)
                    PlanState.DONE -> Text("✓ Eseguito", color = J.Green, style = MaterialTheme.typography.titleSmall)
                    PlanState.CANCELLED -> Text("Annullato", color = J.Muted, style = MaterialTheme.typography.titleSmall)
                }
            }

            m.photoHits?.let { hits ->
                Spacer(Modifier.height(12.dp))
                if (hits.isEmpty()) Text("Nessuna foto trovata. Se non hai ancora analizzato le foto, avvia l'analisi dalla scheda Foto.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(hits.take(60), key = { it.path }) { h ->
                            FileThumb(Storage.file(h.path), 104.dp, Modifier.pressable({ Storage.openFile(ctx, Storage.file(h.path)) }))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Chip("Vedi tutte le ${hits.size} foto") { vm.photoResults = hits; vm.tab = Tab.PHOTOS }
                }
            }

            m.fileHits?.let { files ->
                Spacer(Modifier.height(12.dp))
                if (files.isEmpty()) Text("Nessun file trovato.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                else GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                    files.take(12).forEach { e ->
                        Row(Modifier.fillMaxWidth().pressable({ Storage.openFile(ctx, Storage.file(e.rel)) }).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            FileThumb(Storage.file(e.rel), 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, color = J.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text("${e.folder} · ${Fmt.size(e.size)} · ${Fmt.date(e.modified)}", color = J.Muted, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (files.size > 12) Text("+${files.size - 12} altri", color = J.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}

