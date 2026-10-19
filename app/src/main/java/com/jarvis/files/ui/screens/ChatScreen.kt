package com.jarvis.files.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.jarvis.files.ui.components.PlanCard
import com.jarvis.files.ui.theme.J

private val SUGGESTIONS = listOf(
    "Metti in ordine la cartella Download",
    "Rinomina le foto di WhatsApp con la data",
    "Trova i PDF delle bollette",
    "Mostrami le foto di montagna",
    "Perché il telefono è lento?",
    "Cancella gli APK già installati",
    "Come libero spazio velocemente?",
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) vm.send(text)
        }
    }
    LaunchedEffect(vm.messages.size, vm.busy) {
        val n = vm.messages.size + (if (vm.busy) 1 else 0)
        if (n > 0) list.animateScrollToItem(n - 1)
    }
    val p = Prefs(ctx)
    val engineLabel = when (p.aiMode) {
        AiMode.LOCAL -> "AI nel telefono"
        AiMode.ONLINE -> "AI su internet"
        AiMode.AUTO -> if (AiRouter.onlineConfigured(ctx)) "Automatica" else if (AiRouter.localReady(ctx)) "AI nel telefono" else "Nessuna AI configurata"
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ArcReactor(36.dp, active = vm.busy)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Jarvis", style = MaterialTheme.typography.titleLarge, color = J.Text)
                Text(engineLabel, style = MaterialTheme.typography.labelSmall, color = J.Cyan)
            }
            if (vm.messages.isNotEmpty()) IconButton(onClick = { vm.clearChat() }) { Icon(Icons.Filled.DeleteSweep, "Pulisci chat", tint = J.Muted) }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArcReactor(110.dp)
                    Spacer(Modifier.height(18.dp))
                    Text("Agli ordini, signore.", style = MaterialTheme.typography.headlineSmall, color = J.Text)
                    Text("Scrivi o tieni premuto il microfono e dimmi cosa fare.", color = J.Muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(18.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SUGGESTIONS.forEach { s -> Chip(s) { vm.send(s) } }
                    }
                }
            }
            items(vm.messages, key = { it.id }) { m -> MessageView(vm, m) }
            if (vm.busy) item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                    ArcReactor(26.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(vm.busyText, color = J.Cyan, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Cosa faccio, signore?", color = J.Muted) },
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).border(1.dp, J.Stroke, RoundedCornerShape(24.dp)),
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = J.Card, unfocusedContainerColor = J.Card,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = J.Text, unfocusedTextColor = J.Text, cursorColor = J.Electric,
                ),
            )
            Spacer(Modifier.width(8.dp))
            val sendMode = input.isNotBlank()
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(J.accent).clickable(enabled = !vm.busy) {
                    if (sendMode) { vm.send(input); input = "" }
                    else {
                        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Dimmi cosa fare")
                        runCatching { speech.launch(i) }.onFailure { vm.toast = "Riconoscimento vocale non disponibile" }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (sendMode) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic, null, tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageView(vm: MainViewModel, m: UiMsg) {
    val ctx = LocalContext.current
    if (m.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                m.text, color = Color.White,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                    .background(Brush.linearGradient(listOf(J.Blue, Color(0xFF1F5FD0)))).padding(12.dp),
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            m.text, color = if (m.error) J.Amber else J.Text,
            modifier = Modifier.widthIn(max = 330.dp).clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp))
                .background(J.Card).border(1.dp, J.Stroke, RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)).padding(12.dp),
        )
        if (m.engine.isNotBlank()) Text(m.engine, style = MaterialTheme.typography.labelSmall, color = J.Muted, modifier = Modifier.padding(start = 6.dp, top = 2.dp))

        m.questions.forEach { q ->
            Spacer(Modifier.height(8.dp))
            Text(q.question, color = J.Cyan, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                q.options.forEach { o -> Chip(o) { vm.send(o) } }
            }
        }

        m.plan?.let { plan ->
            Spacer(Modifier.height(8.dp))
            when (m.planState) {
                PlanState.PENDING -> if (plan.isEmpty) {
                    Text(plan.warnings.joinToString("\n"), color = J.Amber, style = MaterialTheme.typography.bodySmall)
                } else PlanCard(plan, onConfirm = { vm.executePlan(m.id, it) }, onCancel = { vm.cancelPlan(m.id) })
                PlanState.RUNNING -> Text("Eseguo…", color = J.Cyan)
                PlanState.DONE -> Text("✓ Eseguito", color = J.Green)
                PlanState.CANCELLED -> Text("Annullato", color = J.Muted)
            }
        }

        m.photoHits?.let { hits ->
            Spacer(Modifier.height(8.dp))
            if (hits.isEmpty()) Text("Nessuna foto trovata. Se non hai ancora fatto l'analisi delle foto, avviala dalla scheda Foto.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
            else {
                Text("${hits.size} foto trovate", color = J.Cyan, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(hits.take(60), key = { it.path }) { h ->
                        FileThumb(Storage.file(h.path), 96.dp, Modifier.clickable { Storage.openFile(ctx, Storage.file(h.path)) })
                    }
                }
                Text("Vedi tutte nella scheda Foto", color = J.Cyan, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp).clickable { vm.photoResults = hits; vm.tab = Tab.PHOTOS })
            }
        }

        m.fileHits?.let { files ->
            Spacer(Modifier.height(8.dp))
            if (files.isEmpty()) Text("Nessun file trovato.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
            else GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(10.dp)) {
                files.take(15).forEach { e ->
                    Row(Modifier.fillMaxWidth().clickable { Storage.openFile(ctx, Storage.file(e.rel)) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FileThumb(Storage.file(e.rel), 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, color = J.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text("${e.folder} · ${Fmt.size(e.size)} · ${Fmt.date(e.modified)}", color = J.Muted, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (files.size > 15) Text("+${files.size - 15} altri", color = J.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
