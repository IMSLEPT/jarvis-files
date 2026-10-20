package com.jarvis.files.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.files.core.FileCategory
import com.jarvis.files.core.FileIndex
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.Prefs
import com.jarvis.files.core.Storage
import com.jarvis.files.ui.HomeTip
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.Tab
import com.jarvis.files.ui.components.ArcReactor
import com.jarvis.files.ui.components.Callout
import com.jarvis.files.ui.components.CategoryCard
import com.jarvis.files.ui.components.FileThumb
import com.jarvis.files.ui.components.GhostButton
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.IconBadge
import com.jarvis.files.ui.components.JarvisLogo
import com.jarvis.files.ui.components.PagerDots
import com.jarvis.files.ui.components.PlanCard
import com.jarvis.files.ui.components.SectionTitle
import com.jarvis.files.ui.components.StorageDonut
import com.jarvis.files.ui.components.SuggestionCard
import com.jarvis.files.ui.components.pressable
import com.jarvis.files.ui.theme.J
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Aspetto di ogni categoria: icona e colori (come nel design di riferimento). */
data class CatStyle(val cat: FileCategory, val title: String, val icon: ImageVector, val colors: List<Color>)

val CAT_STYLES = listOf(
    CatStyle(FileCategory.VIDEO, "Video", Icons.Filled.VideoLibrary, listOf(Color(0xFFFF8A3D), Color(0xFFFF3D2E))),
    CatStyle(FileCategory.IMAGE, "Immagini", Icons.Filled.Image, listOf(Color(0xFF4F8BFF), Color(0xFF2F5BEA))),
    CatStyle(FileCategory.AUDIO, "Audio", Icons.Filled.MusicNote, listOf(Color(0xFFB07CFF), Color(0xFF7C3AED))),
    CatStyle(FileCategory.ARCHIVE, "Archivi", Icons.Filled.Folder, listOf(Color(0xFF4ADE80), Color(0xFF16A34A))),
    CatStyle(FileCategory.DOCUMENT, "Documenti", Icons.Filled.Description, listOf(Color(0xFF38BDF8), Color(0xFF1E3A8A))),
    CatStyle(FileCategory.APK, "App (APK)", Icons.Filled.Android, listOf(Color(0xFF2DD4BF), Color(0xFF0F766E))),
    CatStyle(FileCategory.OTHER, "Altro", Icons.AutoMirrored.Filled.InsertDriveFile, listOf(Color(0xFF94A3B8), Color(0xFF475569))),
)

/** Apre la categoria: le immagini vanno nella galleria intelligente, il resto nell'elenco file. */
fun openCategory(vm: MainViewModel, cat: FileCategory) {
    if (cat == FileCategory.IMAGE) { vm.tab = Tab.PHOTOS; vm.sub = Sub.NONE }
    else { vm.category = cat; vm.folder = null; vm.tab = Tab.FILES; vm.sub = Sub.CATEGORY }
}

fun tipAction(vm: MainViewModel, t: HomeTip) {
    when (t.kind) {
        "junk", "whatsapp" -> { vm.tab = Tab.CLEAN; vm.sub = Sub.NONE }
        "download" -> vm.openChatWith("Metti in ordine la cartella Download")
        "trash" -> { vm.tab = Tab.MORE; vm.sub = Sub.TRASH }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val free = remember(vm.scanning) { Storage.freeBytes() }
    val total = remember { Storage.totalBytes() }
    LaunchedEffect(Unit) {
        if (FileIndex.entries.isEmpty()) vm.rescan() else vm.refreshHome()
    }
    val data = vm.home
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when (hour) { in 5..12 -> "Buongiorno"; in 13..17 -> "Buon pomeriggio"; else -> "Buonasera" }
    val today = remember { SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(Date()).replaceFirstChar { it.uppercase() } }
    val used = (total - free).coerceAtLeast(0)
    val sizes = data.byCat.toMap()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        // ---------- intestazione con Jarvis animato ----------
        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            ArcReactor(
                170.dp, active = vm.scanning,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-10).dp).pressable({ vm.tab = Tab.CHAT }),
            )
            Column(Modifier.fillMaxWidth(0.7f).padding(start = 20.dp)) {
                JarvisLogo()
                Spacer(Modifier.height(22.dp))
                Text(today, color = J.Muted, style = MaterialTheme.typography.titleSmall)
                Text("$greet,", color = J.Text, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, maxLines = 1, softWrap = false)
                Text(Prefs(ctx).userName, color = J.Text, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (vm.scanning) "Sto leggendo le cartelle… ${vm.fileCount} file" else "Il tuo spazio è sotto controllo\ncon Jarvis.",
                    color = J.Muted, style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Column(Modifier.padding(horizontal = 18.dp)) {
            vm.pending?.let { (title, reply, plan) ->
                SectionTitle("Piano in attesa")
                Text("$title — $reply", color = J.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
                PlanCard(plan, onConfirm = { vm.confirmPending(it) }, onCancel = { vm.dismissPending() })
            }

            // ---------- memoria ----------
            Spacer(Modifier.height(18.dp))
            GlassCard(Modifier.fillMaxWidth(), onClick = { vm.tab = Tab.FILES; vm.sub = Sub.NONE }, padding = PaddingValues(horizontal = 12.dp, vertical = 18.dp)) {
                androidx.compose.foundation.layout.BoxWithConstraints {
                val donut = (maxWidth * 0.5f).coerceAtMost(186.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Callout("${used * 100 / total.coerceAtLeast(1)}%", "utilizzato · ${Fmt.size(used)}", J.Orange, alignEnd = false, modifier = Modifier.weight(1f))
                    val known = CAT_STYLES.mapNotNull { s -> sizes[s.cat]?.let { it to s } }
                    val segs = known.map { (bytes, s) -> (bytes.toFloat() / total.coerceAtLeast(1)) to Brush.sweepGradient(s.colors) as Brush } +
                        listOf(((used - known.sumOf { it.first }).coerceAtLeast(0).toFloat() / total.coerceAtLeast(1)) to Brush.linearGradient(listOf(Color(0xFFB8C0CC), Color(0xFFCBD2DC))) as Brush)
                    StorageDonut(
                        segs, String.format(Locale.ITALY, "%.1f", free / 1_073_741_824f), "su ${Fmt.size(total)}", donut,
                    )
                    Callout(Fmt.size(total), "totale", J.Info, alignEnd = true, modifier = Modifier.weight(1f))
                }
                }
            }

            // ---------- categorie (scorrono) ----------
            Spacer(Modifier.height(16.dp))
            val pages = CAT_STYLES.chunked(4)
            val pager = rememberPagerState { pages.size }
            HorizontalPager(pager, pageSpacing = 12.dp) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    pages[page].chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { s ->
                                val bytes = sizes[s.cat] ?: 0L
                                CategoryCard(
                                    s.icon, s.title, Fmt.size(bytes), bytes.toFloat() / used.coerceAtLeast(1), s.colors,
                                    Modifier.weight(1f),
                                ) { openCategory(vm, s.cat) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PagerDots(pages.size, pager.currentPage) }

            // ---------- suggerimento di Jarvis ----------
            AnimatedVisibility(data.tips.isNotEmpty(), enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 6 }) {
                Column {
                    val first = data.tips.firstOrNull() ?: return@Column
                    SuggestionCard(
                        first.big, first.title, first.sub,
                        when (first.kind) { "download" -> "Metti in ordine"; "trash" -> "Apri il cestino"; else -> "Avvia pulizia" },
                    ) { tipAction(vm, first) }
                    if (data.tips.size > 1) {
                        Spacer(Modifier.height(12.dp))
                        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                            data.tips.drop(1).forEach { t ->
                                ListRow(
                                    when (t.kind) { "whatsapp" -> Icons.Filled.Chat; "download" -> Icons.Filled.FolderOpen; "trash" -> Icons.Filled.Delete; else -> Icons.Filled.AutoAwesome },
                                    "${t.big} ${t.title}", t.sub,
                                ) { tipAction(vm, t) }
                            }
                        }
                    }
                }
            }

            vm.undoInfo?.let { (src, n) ->
                Spacer(Modifier.height(14.dp))
                GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(src, color = J.Text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$n modifiche · puoi tornare indietro", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        GhostButton("Annulla", icon = Icons.AutoMirrored.Filled.Undo) { vm.undo() }
                    }
                }
            }

            if (data.recent.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Ultime foto", Modifier.weight(1f))
                    Text("Vedi tutte", color = J.Orange, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 16.dp).clickable { vm.tab = Tab.PHOTOS })
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(data.recent, key = { it }) { p ->
                        FileThumb(Storage.file(p), 92.dp, Modifier.pressable({ Storage.openFile(ctx, Storage.file(p)) }))
                    }
                }
            }

            SectionTitle("Scorciatoie")
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                ListRow(Icons.Filled.FolderOpen, "Riordina Download", "Cartelle per tipo, nomi chiari") { vm.openChatWith("Metti in ordine la cartella Download") }
                ListRow(Icons.Filled.Security, "Controllo sicurezza", "App sospette e impostazioni a rischio") { vm.tab = Tab.MORE; vm.sub = Sub.SECURITY }
                ListRow(Icons.Filled.AutoAwesome, "Parla con Jarvis", "Tieni premuto Jarvis in basso per comandare a voce") { vm.voiceOpen = true }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Riga di lista: icona tonale, titolo, sottotitolo, freccia. */
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
            Text(sub, color = J.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = J.Muted)
    }
}
