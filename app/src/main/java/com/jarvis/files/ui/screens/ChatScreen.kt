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
        AiMode.AUTO -> if (AiRouter.onlineConfigured(ctx)) "Automatica" else if (AiRouter.localReady(ctx)) "AI nel telefono" else "Da configurare"
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ArcReactor(34.dp, active = vm.busy)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Jarvis", style = MaterialTheme.typography.titleLarge, color = J.Text)
                Text(if (vm.busy) vm.busyText else engineLabel, style = MaterialTheme.typography.bodySmall, color = J.Muted, maxLines = 1)
            }
            if (vm.messages.isNotEmpty()) IconButton(onClick = { vm.clearChat() }) { Icon(Icons.Filled.DeleteSweep, "Nuova chat", tint = J.Muted) }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (vm.messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 36.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ArcReactor(96.dp) }
                    Spacer(Modifier.height(28.dp))
                    Text("Agli ordini, signore.", style = MaterialTheme.typography.headlineLarge, color = J.Text)
                    Text("Cosa metto in ordine oggi?", style = MaterialTheme.typography.headlineLarge, color = J.Muted)
                    Spacer(Modifier.height(24.dp))
                    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                        SUGGESTIONS.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().pressable({ vm.send(s.prompt) }).padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconBadge(s.icon, size = 36.dp)
                                Spacer(Modifier.width(14.dp))
                                Text(s.title, color = J.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = J.Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            items(vm.messages, key = { it.id }) { m ->
                Box(Modifier.animateItem(fadeInSpec = tween(350), placementSpec = tween(300))) { MessageView(vm, m) }
            }
            if (vm.busy) item(key = "typing") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateItem()) {
                    ArcReactor(26.dp)
                    Spacer(Modifier.width(12.dp))
                    TypingDots()
                }
            }
        }

        // Barra di input a pillola, con microfono che diventa "invia"
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(28.dp)).background(J.Card).padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (input.isEmpty()) Text("Chiedi a Jarvis", color = J.Muted, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = input, onValueChange = { input = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = J.Text),
                    cursorBrush = SolidColor(J.Blue), maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            val sendMode = input.isNotBlank()
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (sendMode) J.Blue else J.CardHi).clickable(enabled = !vm.busy) {
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
                AnimatedContent(sendMode, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f)) }, label = "send") { s ->
                    Icon(if (s) Icons.Filled.ArrowUpward else Icons.Filled.Mic, null, tint = if (s) Color.White else J.Text)
                }
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
            Text(m.text, color = if (m.error) J.Amber else J.Text, style = MaterialTheme.typography.bodyLarge)
            if (m.engine.isNotBlank()) Text(m.engine, style = MaterialTheme.typography.labelSmall, color = J.Muted, modifier = Modifier.padding(top = 4.dp))

            m.questions.forEach { q ->
                Spacer(Modifier.height(12.dp))
                Text(q.question, color = J.Text, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    q.options.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().pressable({ vm.send(o) }).clip(RoundedCornerShape(16.dp)).background(J.Card)
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
                    } else PlanCard(plan, onConfirm = { vm.executePlan(m.id, it) }, onCancel = { vm.cancelPlan(m.id) })
                    PlanState.RUNNING -> Row(verticalAlignment = Alignment.CenterVertically) { TypingDots(J.Blue); Spacer(Modifier.width(8.dp)); Text("Eseguo…", color = J.Muted) }
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
