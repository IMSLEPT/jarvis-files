package com.jarvis.files.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.files.ai.AiRouter
import com.jarvis.files.ai.ChatMsg
import com.jarvis.files.ai.LOCAL_MODELS
import com.jarvis.files.ai.LocalAi
import com.jarvis.files.ai.ONLINE_PRESETS
import com.jarvis.files.ai.OnlineAi
import com.jarvis.files.automation.Automations
import com.jarvis.files.core.AiMode
import com.jarvis.files.core.AppCache
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.Prefs
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.components.Chip
import com.jarvis.files.ui.components.GhostButton
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.GradientButton
import com.jarvis.files.ui.components.LinearMeter
import com.jarvis.files.ui.components.SectionTitle
import com.jarvis.files.ui.theme.J
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun field(value: String, label: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = J.Text, unfocusedTextColor = J.Text, focusedBorderColor = J.Blue, unfocusedBorderColor = J.Stroke),
    )
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = J.Text)
            Text(sub, color = J.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = J.Blue))
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val p = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(p.userName) }
    var mode by remember { mutableStateOf(p.aiMode) }
    var base by remember { mutableStateOf(p.onlineBaseUrl) }
    var key by remember { mutableStateOf(p.onlineApiKey) }
    var model by remember { mutableStateOf(p.onlineModel) }
    var testResult by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var vt by remember { mutableStateOf(p.virusTotalKey) }
    var lock by remember { mutableStateOf(p.biometricLock) }
    var gpu by remember { mutableStateOf(p.localUseGpu) }
    var charging by remember { mutableStateOf(p.photoIndexOnlyCharging) }
    var deep by remember { mutableStateOf(p.photoDeepCaptions) }
    var lowGb by remember { mutableStateOf(p.lowSpaceGb) }
    var dl by remember { mutableStateOf(LocalAi.downloadState(ctx)) }
    var localReady by remember { mutableStateOf(AiRouter.localReady(ctx)) }
    var dlError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (isActive) {
            dl = LocalAi.downloadState(ctx)
            localReady = AiRouter.localReady(ctx)
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(14.dp)) {
        SubHeader("Impostazioni") { vm.sub = Sub.NONE }

        SectionTitle("Tu")
        field(name, "Come ti chiamo") { name = it; p.userName = it }

        SectionTitle("Cervello di Jarvis")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiMode.entries.forEach { m -> Chip(m.label, mode == m) { mode = m; p.aiMode = m } }
        }
        Text(
            "Automatica: usa internet se configurato (più veloce e intelligente), altrimenti l'AI nel telefono. " +
                "Con l'AI su internet vengono inviati i NOMI dei file e le richieste, mai il contenuto dei file o le foto.",
            color = J.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp),
        )

        GlassCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("AI nel telefono (gratis, offline)", color = J.Text, style = MaterialTheme.typography.titleMedium)
            Text("Gemma 4 di Google, open source. Le foto e i file non escono mai dal telefono.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            val d = dl
            when {
                d != null && d.running -> {
                    Text("Download: ${Fmt.size(d.done)} di ${if (d.total > 0) Fmt.size(d.total) else "?"}", color = J.Cyan)
                    LinearMeter(if (d.total > 0) d.done.toFloat() / d.total else 0f, Modifier.padding(vertical = 6.dp))
                    Text("Puoi chiudere l'app: il download continua da solo.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                }
                localReady -> {
                    Text("✓ Pronto: ${p.localModelPath.substringAfterLast('/')}", color = J.Green)
                    ToggleRow("Usa la GPU", "Più veloce. Disattiva se l'app si blocca.", gpu) { gpu = it; p.localUseGpu = it; LocalAi.release() }
                    GhostButton("Elimina modello", color = J.Red) { LocalAi.deleteModel(ctx); localReady = false }
                }
                else -> {
                    if (d?.failed == true) Text("Download non riuscito: riprova.", color = J.Amber)
                    LOCAL_MODELS.forEach { m ->
                        Column(Modifier.fillMaxWidth().clickable {
                            dlError = ""
                            scope.launch {
                                runCatching {
                                    val (file, size) = LocalAi.findModelFile(m.repo)
                                    if (size > 0 && size > com.jarvis.files.core.Storage.freeBytes() - 1_000_000_000L) error("Spazio insufficiente: servono ${Fmt.size(size)}")
                                    LocalAi.startDownload(ctx, m.repo, file)
                                }.onFailure { dlError = it.message ?: "Errore" }
                            }
                        }.padding(vertical = 8.dp)) {
                            Text("⬇ ${m.title}", color = J.Electric, style = MaterialTheme.typography.titleSmall)
                            Text(m.note, color = J.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (dlError.isNotBlank()) Text(dlError, color = J.Amber, style = MaterialTheme.typography.bodySmall)
                    Text("Consigliato in Wi-Fi.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("AI su internet (gratis, modelli open source)", color = J.Text, style = MaterialTheme.typography.titleMedium)
            Text("Scegli un servizio, crea la chiave gratuita dal sito e incollala qui.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            ONLINE_PRESETS.forEach { pr ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Chip(pr.name, base == pr.baseUrl) { base = pr.baseUrl; model = pr.model; p.onlineBaseUrl = pr.baseUrl; p.onlineModel = pr.model }
                        Spacer(Modifier.width(8.dp))
                        Text("Crea chiave ›", color = J.Cyan, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pr.keyUrl)))
                        })
                    }
                    Text(pr.note, color = J.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            field(key, "Chiave API", password = true) { key = it; p.onlineApiKey = it }
            field(model, "Modello") { model = it; p.onlineModel = it }
            field(base, "Indirizzo servizio") { base = it; p.onlineBaseUrl = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                GradientButton("Prova") {
                    testResult = "Provo…"
                    scope.launch {
                        testResult = runCatching {
                            OnlineAi(p.onlineBaseUrl, p.onlineApiKey, p.onlineModel).complete("Rispondi in 5 parole.", listOf(ChatMsg("user", "Presentati")), 50)
                        }.fold({ "✓ Funziona: $it" }, { "✗ ${it.message}" })
                    }
                }
                GhostButton("Elenco modelli") {
                    scope.launch { models = runCatching { OnlineAi(p.onlineBaseUrl, p.onlineApiKey, p.onlineModel).listModels() }.getOrDefault(emptyList()) }
                }
            }
            if (testResult.isNotBlank()) Text(testResult, color = if (testResult.startsWith("✓")) J.Green else J.Amber, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp))
            models.take(40).forEach { m ->
                Text(m, color = if (m == model) J.Electric else J.Text, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clickable { model = m; p.onlineModel = m }.padding(vertical = 4.dp))
            }
        }

        SectionTitle("Foto")
        ToggleRow("Analizza solo in carica", "Di notte, quando il telefono è collegato al caricatore.", charging) {
            charging = it; p.photoIndexOnlyCharging = it; Automations.rescheduleAll(ctx)
        }
        ToggleRow("Descrizioni dettagliate con Gemma", "Riconosce luoghi e scene (montagne, laghi…). Più lento; serve l'AI nel telefono.", deep) {
            deep = it; p.photoDeepCaptions = it
        }

        SectionTitle("Sicurezza")
        ToggleRow("Blocco con impronta", "Per aprire l'app servono impronta o PIN.", lock) { lock = it; p.biometricLock = it }
        field(vt, "Chiave VirusTotal (facoltativa)", password = true) { vt = it; p.virusTotalKey = it }
        Text("Crea una chiave gratuita ›", color = J.Cyan, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.virustotal.com/gui/join-us")))
        }.padding(vertical = 4.dp))

        SectionTitle("Avvisi")
        Text("Avvisami quando restano meno di $lowGb GB liberi", color = J.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            listOf(5, 10, 20, 30).forEach { g -> Chip("$g GB", lowGb == g) { lowGb = g; p.lowSpaceGb = g } }
        }

        SectionTitle("Permessi")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Accesso file") {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
            GhostButton("Dati di utilizzo") { ctx.startActivity(AppCache.usageAccessIntent()) }
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("Ottimizzazione batteria (per le automazioni)") {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        Spacer(Modifier.height(30.dp))
        Text("Jarvis Files 1.0 · progetto personale", color = J.Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
    }
}
