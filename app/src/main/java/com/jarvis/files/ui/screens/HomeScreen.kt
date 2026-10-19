package com.jarvis.files.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Chat
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.files.core.Cleaner
import com.jarvis.files.core.DeviceInfo
import com.jarvis.files.core.FileCategory
import com.jarvis.files.core.FileIndex
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.Prefs
import com.jarvis.files.core.Storage
import com.jarvis.files.core.Trash
import com.jarvis.files.photos.PhotoDb
import com.jarvis.files.photos.PhotoIndexer
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.Tab
import com.jarvis.files.ui.components.AnimatedNumber
import com.jarvis.files.ui.components.ArcReactor
import com.jarvis.files.ui.components.Dot
import com.jarvis.files.ui.components.FileThumb
import com.jarvis.files.ui.components.GhostButton
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.IconBadge
import com.jarvis.files.ui.components.PlanCard
import com.jarvis.files.ui.components.SectionTitle
import com.jarvis.files.ui.components.StackedBar
import com.jarvis.files.ui.components.StatTile
import com.jarvis.files.ui.components.pressable
import com.jarvis.files.ui.theme.J
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class Tip(val icon: ImageVector, val title: String, val sub: String, val action: () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val free = remember(vm.scanning) { Storage.freeBytes() }
    val total = remember { Storage.totalBytes() }
    val battery = remember { DeviceInfo.battery(ctx) }
    val ram = remember { DeviceInfo.ram(ctx) }
    var byCat by remember { mutableStateOf<List<Pair<FileCategory, Long>>>(emptyList()) }
    var tips by remember { mutableStateOf<List<Tip>>(emptyList()) }
    var recent by remember { mutableStateOf<List<String>>(emptyList()) }
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(vm.scanning) {
        shown = true
        if (FileIndex.entries.isEmpty()) { vm.rescan(); return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            val entries = FileIndex.entries
            byCat = entries.groupBy { it.category }.map { it.key to it.value.sumOf { e -> e.size } }.sortedByDescending { it.second }
            recent = entries.filter { it.category == FileCategory.IMAGE && !it.rel.contains("/.") && it.size > 25_000 }
                .sortedByDescending { it.modified }.take(12).map { it.rel }
            val list = ArrayList<Tip>()
            runCatching {
                val junk = Cleaner.junk(ctx)
                if (junk.isNotEmpty()) list += Tip(Icons.Filled.Android, "${Fmt.size(junk.sumOf { it.size })} di file inutili",
                    "${junk.size} elementi: APK già installati, temporanei, cartelle vuote") { vm.tab = Tab.CLEAN }
            }
            runCatching {
                val wa = Cleaner.whatsapp().sumOf { it.size }
                if (wa > 500_000_000) list += Tip(Icons.Filled.Chat, "WhatsApp occupa ${Fmt.size(wa)}", "Vocali, video dei gruppi e media già inviati") { vm.tab = Tab.CLEAN }
            }
            val dl = entries.count { it.folder == "Download" }
            if (dl > 15) list += Tip(Icons.Filled.FolderOpen, "$dl file sparsi in Download", "Jarvis può metterli in ordine in un attimo") {
                vm.openChatWith("Metti in ordine la cartella Download")
            }
            runCatching {
                val analyzed = PhotoDb.get(ctx).counts().first
                val photos = PhotoIndexer.candidates().size
                if (photos - analyzed > 50) list += Tip(Icons.Filled.Face, "${photos - analyzed} foto da analizzare", "Per cercare persone, luoghi e oggetti") { vm.tab = Tab.PHOTOS }
            }
            val trash = Trash.totalSize(ctx)
            if (trash > 200_000_000) list += Tip(Icons.Filled.Delete, "${Fmt.size(trash)} nel cestino", "Svuotalo per liberare spazio subito") { vm.tab = Tab.MORE; vm.sub = Sub.TRASH }
            tips = list
        }
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when (hour) { in 5..12 -> "Buongiorno"; in 13..17 -> "Buon pomeriggio"; else -> "Buonasera" }
    val today = remember { SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(Date()).replaceFirstChar { it.uppercase() } }
    val catColors = listOf(J.Blue, Color(0xFF8E7CFF), Color(0xFF24C6B6), J.Amber, Color(0xFFFF7AA2), J.Muted, J.Green)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(today, style = MaterialTheme.typography.titleSmall, color = J.Muted)
                Text("$greet,", style = MaterialTheme.typography.displaySmall, color = J.Text)
                Text(Prefs(ctx).userName, style = MaterialTheme.typography.displaySmall, color = J.Muted)
            }
            ArcReactor(64.dp, active = vm.scanning, modifier = Modifier.pressable({ vm.tab = Tab.CHAT }))
        }

        Spacer(Modifier.height(22.dp))
        // Barra "Chiedi a Jarvis"
        Row(
            Modifier.fillMaxWidth().pressable({ vm.tab = Tab.CHAT }).clip(RoundedCornerShape(50)).background(J.Card)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = J.Blue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Chiedi a Jarvis…", color = J.Muted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Mic, null, tint = J.Muted, modifier = Modifier.size(20.dp))
        }

        vm.pending?.let { (title, reply, plan) ->
            SectionTitle("Piano in attesa")
            Text("$title — $reply", color = J.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
            PlanCard(plan, onConfirm = { vm.confirmPending(it) }, onCancel = { vm.dismissPending() })
        }

        vm.undoInfo?.let { (src, n) ->
            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(src, color = J.Text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$n modifiche · puoi tornare indietro", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    GhostButton("Annulla", icon = Icons.AutoMirrored.Filled.Undo) { vm.undo() }
                }
            }
        }

        SectionTitle("Memoria")
        GlassCard(Modifier.fillMaxWidth(), onClick = { vm.tab = Tab.CLEAN }, padding = androidx.compose.foundation.layout.PaddingValues(22.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedNumber(free / 1_073_741_824f, { String.format(Locale.ITALY, "%.1f", it) },
                    MaterialTheme.typography.displayMedium, if (free < Prefs(ctx).lowSpaceGb * 1_073_741_824L) J.Amber else J.Text)
                Spacer(Modifier.width(8.dp))
                Text("GB liberi", style = MaterialTheme.typography.titleMedium, color = J.Muted, modifier = Modifier.padding(bottom = 10.dp))
            }
            Text("su ${Fmt.size(total)} · ${(total - free) * 100 / total.coerceAtLeast(1)}% usato", color = J.Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(18.dp))
            val totCat = (total - free).coerceAtLeast(1).toFloat()
            StackedBar(byCat.take(6).mapIndexed { i, (_, s) -> (s / totCat) to catColors[i % catColors.size] })
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                byCat.take(6).forEachIndexed { i, (cat, s) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(catColors[i % catColors.size])
                        Spacer(Modifier.width(6.dp))
                        Text("${cat.label} ${Fmt.size(s)}", style = MaterialTheme.typography.bodySmall, color = J.Muted)
                    }
                }
            }
        }

        AnimatedVisibility(tips.isNotEmpty(), enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 6 }) {
            Column {
                SectionTitle("Suggerimenti di Jarvis")
                GlassCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                    tips.forEach { tip -> ListRow(tip.icon, tip.title, tip.sub, tip.action) }
                }
            }
        }

        if (recent.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Ultime foto", Modifier.weight(1f))
                Text("Vedi tutte", color = J.Blue, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp).clickable { vm.tab = Tab.PHOTOS })
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recent, key = { it }) { p ->
                    FileThumb(Storage.file(p), 92.dp, Modifier.pressable({ Storage.openFile(ctx, Storage.file(p)) }))
                }
            }
        }

        SectionTitle("Strumenti")
        GlassCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            ListRow(Icons.Filled.FolderOpen, "Riordina Download", "Cartelle per tipo, nomi chiari") { vm.openChatWith("Metti in ordine la cartella Download") }
            ListRow(Icons.Filled.ContentCopy, "Doppioni e pulizia", "Cache, WhatsApp, file grandi") { vm.tab = Tab.CLEAN }
            ListRow(Icons.Filled.Search, "Trova un documento", "\"la bolletta della luce di marzo\"") { vm.tab = Tab.MORE; vm.sub = Sub.FIND }
            ListRow(Icons.Filled.Security, "Controllo sicurezza", "App sospette e impostazioni a rischio") { vm.tab = Tab.MORE; vm.sub = Sub.SECURITY }
        }

        SectionTitle("Telefono")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${battery.level}%", if (battery.charging) "In carica" else "Batteria", Modifier.weight(1f).pressable({ vm.tab = Tab.MORE; vm.sub = Sub.BATTERY }),
                color = if (battery.level < 20) J.Red else J.Text)
            StatTile("${battery.tempC.toInt()}°", "Temperatura", Modifier.weight(1f), color = if (battery.tempC > 40) J.Amber else J.Text)
            StatTile(Fmt.size(ram.first), "RAM libera", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(Modifier.fillMaxWidth(), onClick = { vm.tab = Tab.MORE; vm.sub = Sub.BATTERY }, padding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            ListRow(Icons.Filled.BatteryChargingFull, "Batteria e consumi", "Quali app usi di più") { vm.tab = Tab.MORE; vm.sub = Sub.BATTERY }
            ListRow(Icons.Filled.SdStorage, "${FileIndex.entries.size} file sotto controllo", if (vm.scanning) "Sto leggendo le cartelle…" else "Tocca per aggiornare") { vm.rescan() }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Riga di lista minimal: icona tonale, titolo, sottotitolo, freccia. */
@Composable
fun ListRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = J.Text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = J.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = J.Muted)
    }
}
