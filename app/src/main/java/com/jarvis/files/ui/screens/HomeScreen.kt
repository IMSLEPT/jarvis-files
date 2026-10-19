package com.jarvis.files.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.files.core.DeviceInfo
import com.jarvis.files.core.FileCategory
import com.jarvis.files.core.FileIndex
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.Prefs
import com.jarvis.files.core.Storage
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.Tab
import com.jarvis.files.ui.components.ArcReactor
import com.jarvis.files.ui.components.GhostButton
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.LinearMeter
import com.jarvis.files.ui.components.PlanCard
import com.jarvis.files.ui.components.SectionTitle
import com.jarvis.files.ui.components.StatTile
import com.jarvis.files.ui.components.UsageRing
import com.jarvis.files.ui.theme.J
import java.util.Calendar

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val free = remember(vm.scanning) { Storage.freeBytes() }
    val total = remember { Storage.totalBytes() }
    val battery = remember { DeviceInfo.battery(ctx) }
    var byCat by remember { mutableStateOf<List<Pair<FileCategory, Long>>>(emptyList()) }
    LaunchedEffect(vm.scanning) {
        if (FileIndex.entries.isEmpty()) vm.rescan()
        byCat = FileIndex.entries.groupBy { it.category }.map { it.key to it.value.sumOf { e -> e.size } }.sortedByDescending { it.second }
    }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when (hour) { in 5..12 -> "Buongiorno"; in 13..17 -> "Buon pomeriggio"; else -> "Buonasera" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("JARVIS FILES", style = MaterialTheme.typography.labelSmall, color = J.Cyan)
                Text("$greet, ${Prefs(ctx).userName}.", style = MaterialTheme.typography.headlineMedium, color = J.Text)
                Text(
                    if (vm.scanning) "Sto leggendo le cartelle… ${vm.fileCount} file" else "Tutti i sistemi operativi. ${FileIndex.entries.size} file sotto controllo.",
                    color = J.Muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
            ArcReactor(64.dp, active = vm.scanning)
        }

        vm.pending?.let { (title, reply, plan) ->
            SectionTitle("Piano in attesa · $title")
            Text(reply, color = J.Text, modifier = Modifier.padding(bottom = 8.dp))
            PlanCard(plan, onConfirm = { vm.confirmPending(it) }, onCancel = { vm.dismissPending() })
        }

        vm.undoInfo?.let { (src, n) ->
            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ultima operazione", style = MaterialTheme.typography.labelSmall, color = J.Muted)
                        Text("$src · $n modifiche", color = J.Text, style = MaterialTheme.typography.bodyMedium)
                    }
                    GhostButton("Annulla", icon = Icons.AutoMirrored.Filled.Undo) { vm.undo() }
                }
            }
        }

        SectionTitle("Memoria")
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val used = total - free
                UsageRing(used.toFloat() / total.coerceAtLeast(1), 120.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(used * 100 / total.coerceAtLeast(1))}%", style = MaterialTheme.typography.headlineSmall, color = J.Text)
                        Text("usata", style = MaterialTheme.typography.bodySmall, color = J.Muted)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("${Fmt.size(free)} liberi", style = MaterialTheme.typography.titleLarge, color = if (free < Prefs(ctx).lowSpaceGb * 1_073_741_824L) J.Amber else J.Electric)
                    Text("su ${Fmt.size(total)}", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    val totCat = byCat.sumOf { it.second }.coerceAtLeast(1)
                    byCat.take(4).forEach { (cat, size) ->
                        Text("${cat.label} · ${Fmt.size(size)}", style = MaterialTheme.typography.bodySmall, color = J.Text)
                        LinearMeter(size.toFloat() / totCat, Modifier.padding(vertical = 3.dp))
                    }
                }
            }
        }

        SectionTitle("Azioni rapide")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction("Riordina Download", Icons.Filled.FolderOpen, Modifier.weight(1f)) { vm.openChatWith("Metti in ordine la cartella Download") }
            QuickAction("Pulizia", Icons.Filled.CleaningServices, Modifier.weight(1f)) { vm.tab = Tab.CLEAN }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction("Doppioni", Icons.Filled.ContentCopy, Modifier.weight(1f)) { vm.tab = Tab.CLEAN }
            QuickAction("Sicurezza", Icons.Filled.Security, Modifier.weight(1f)) { vm.tab = Tab.MORE; vm.sub = Sub.SECURITY }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction("Trova un file", Icons.Filled.Search, Modifier.weight(1f)) { vm.tab = Tab.MORE; vm.sub = Sub.FIND }
            QuickAction("Chiedi a Jarvis", Icons.Filled.AutoAwesome, Modifier.weight(1f)) { vm.tab = Tab.CHAT }
        }

        SectionTitle("Stato")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("${battery.level}%", if (battery.charging) "Batteria · in carica" else "Batteria", Modifier.weight(1f),
                color = if (battery.level < 20) J.Red else J.Green)
            StatTile("${battery.tempC}°", "Temperatura", Modifier.weight(1f), color = if (battery.tempC > 40) J.Amber else J.Cyan)
            StatTile(DeviceInfo.securityPatchAgeDays()?.let { "${it}g" } ?: "-", "Ultima patch", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth(), onClick = { vm.tab = Tab.MORE; vm.sub = Sub.BATTERY }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BatteryChargingFull, null, tint = J.Green)
                Spacer(Modifier.width(10.dp))
                Text("Controllo batteria e app che la consumano", color = J.Text, modifier = Modifier.weight(1f))
                Text("›", color = J.Muted, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QuickAction(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick) {
        Icon(icon, null, tint = J.Electric, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = J.Text, style = MaterialTheme.typography.titleSmall)
    }
}
