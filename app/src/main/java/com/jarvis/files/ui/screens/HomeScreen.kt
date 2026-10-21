package com.jarvis.files.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.files.R
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
import com.jarvis.files.ui.components.GhostButton
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.IconBadge
import com.jarvis.files.ui.components.Mk
import com.jarvis.files.ui.components.MkColor
import com.jarvis.files.ui.components.PlanCard
import com.jarvis.files.ui.components.SectionTitle
import com.jarvis.files.ui.components.T
import com.jarvis.files.ui.components.pressable
import com.jarvis.files.ui.components.rememberMk
import com.jarvis.files.ui.components.style
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

fun openFolder(vm: MainViewModel, path: String) {
    vm.category = null; vm.folder = path; vm.tab = Tab.FILES; vm.sub = Sub.CATEGORY
}

fun tipAction(vm: MainViewModel, t: HomeTip) {
    when (t.kind) {
        "junk", "whatsapp" -> { vm.tab = Tab.CLEAN; vm.sub = Sub.NONE }
        "download" -> vm.openChatWith("Metti in ordine la cartella Download")
        "trash" -> { vm.tab = Tab.MORE; vm.sub = Sub.TRASH }
        else -> { vm.tab = Tab.CHAT; vm.sub = Sub.NONE }
    }
}

/** Una scheda categoria della Home: immagine del mockup oppure icona su tessera colorata. */
private data class CatItem(
    val title: String, val bytes: Long, val image: Int?, val icon: ImageVector,
    val bar: List<Color>, val tile: Color, val chevBg: Color, val chev: Color, val onClick: () -> Unit,
)

// Colori presi dal mockup
private val BAR_VIDEO = listOf(Color(0xFFFF8A3D), Color(0xFFF23A2E))
private val BAR_IMAGE = listOf(Color(0xFF2D98FD), Color(0xFF0341FC))
private val BAR_AUDIO = listOf(Color(0xFFA066FF), Color(0xFF7C3AED))
private val BAR_ARCH = listOf(Color(0xFF34D399), Color(0xFF16A34A))
private val BAR_NAVY = listOf(Color(0xFF3B6FD8), Color(0xFF1C3A85))
private val BAR_TEAL = listOf(Color(0xFF2DD4BF), Color(0xFF0F766E))
private val BAR_GREY = listOf(Color(0xFFA7B0BE), Color(0xFF64748B))

private val Y0 = 24f        // fine della barra di stato nel mockup
private val Y_END = 660f    // fine della scheda suggerimento + margine

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val mk = rememberMk()
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
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    with(mk) {
        // Sfondo come nel mockup: bianco in alto, grigio-azzurro chiarissimo più in basso
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFFBFDFD), Color(0xFFF6F9FA)), endY = with(density) { (statusTop + 260f.d).toPx() }))
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().height(statusTop + (Y_END - Y0).d)) {
                // Origine = angolo in alto del mockup; la barra di stato reale sostituisce quella disegnata
                Box(Modifier.offset(y = statusTop - Y0.d).fillMaxWidth().height(Y_END.d)) {
                    // ---------- intestazione ----------
                    ArcReactor(
                        223f.d, active = vm.scanning, feather = false,
                        modifier = Modifier.offset(189.1f.d, 21.1f.d).pressable({ vm.tab = Tab.CHAT; vm.sub = Sub.NONE }),
                    )
                    Image(painterResource(R.drawable.jarvis_logo), "Jarvis Files", Modifier.offset(15.8f.d, 32.5f.d).size(139.8f.d, 36.9f.d))
                    T(today, 19.3f, 85.3f, 11.9f, 400, MkColor.Slate)
                    T("$greet,", 19.3f, 103.3f, 26.2f, 800, MkColor.Ink, letterSpacing = -0.2f, fit = 172f)
                    T(Prefs(ctx).userName, 18.9f, 131.9f, 26.2f, 800, MkColor.Ink, letterSpacing = -0.2f, fit = 172f)
                    if (vm.scanning) {
                        T("Sto leggendo le cartelle…", 20.2f, 169.7f, 11.8f, 400, MkColor.Slate)
                        T("${vm.fileCount} file trovati", 20.2f, 185.1f, 11.8f, 400, MkColor.Slate)
                    } else {
                        T("Il tuo spazio è sotto controllo", 20.2f, 169.7f, 11.8f, 400, MkColor.Slate)
                        T("con Jarvis.", 20.2f, 185.1f, 11.8f, 400, MkColor.Slate)
                    }

                    // ---------- memoria ----------
                    StorageBlock(vm, free, total, used, sizes)

                    // ---------- categorie (3 pagine, come i 3 puntini) ----------
                    val pages = remember(data, sizes) { categoryPages(vm, sizes, data.extra) }
                    val pager = rememberPagerState { pages.size }
                    HorizontalPager(pager, Modifier.offset(y = 370.7f.d).fillMaxWidth().height(122.3f.d)) { p ->
                        Box(Modifier.fillMaxSize()) {
                            val max = pages[p].maxOf { it.bytes }.coerceAtLeast(1)
                            pages[p].forEachIndexed { i, it ->
                                CatCard(if (i % 2 == 0) 17.2f else 209.5f, if (i < 2) 0f else 64.2f, it, max)
                            }
                        }
                    }
                    pages.indices.forEach { i ->
                        val sel = pager.currentPage == i
                        Box(
                            Modifier.offset((205.4f + (i - (pages.size - 1) / 2f) * 14.7f - 3.5f).d, 500f.d).size(7f.d)
                                .clip(CircleShape).background(if (sel) Color(0xFFFF5A1F) else Color(0xFFD5DAE2)),
                        )
                    }

                    // ---------- suggerimento di Jarvis ----------
                    val tip = data.tips.firstOrNull() ?: HomeTip(
                        "chat", "in ordine", "Tutto", "Nessun file inutile trovato. Chiedimi di riordinare una cartella.",
                    )
                    Suggestion(tip) { tipAction(vm, tip) }
                }
            }

            // Solo quando servono: piano in attesa, annulla, altri suggerimenti
            Column(Modifier.padding(horizontal = 17.2f.d)) {
                vm.pending?.let { (title, reply, plan) ->
                    SectionTitle("Piano in attesa")
                    Text("$title — $reply", color = J.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
                    PlanCard(plan, onConfirm = { vm.confirmPending(it) }, onCancel = { vm.dismissPending() })
                }
                vm.undoInfo?.let { (src, n) ->
                    Spacer(Modifier.height(10.dp))
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
                if (data.tips.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    GlassCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                        data.tips.drop(1).forEach { t ->
                            ListRow(
                                when (t.kind) { "whatsapp" -> Icons.Filled.Chat; "download" -> Icons.Filled.Download; "trash" -> Icons.Filled.Delete; else -> Icons.Filled.CleaningServices },
                                "${t.big} ${t.title}", t.sub,
                            ) { tipAction(vm, t) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun categoryPages(vm: MainViewModel, sizes: Map<FileCategory, Long>, extra: Map<String, Long>): List<List<CatItem>> {
    fun cat(c: FileCategory, title: String, img: Int?, icon: ImageVector, bar: List<Color>, tile: Color, chevBg: Color, chev: Color) =
        CatItem(title, sizes[c] ?: 0L, img, icon, bar, tile, chevBg, chev) { openCategory(vm, c) }
    val peach = Color(0xFFFFEFE6); val blue = Color(0xFFEAF2FF); val lilac = Color(0xFFF1EAFF); val mint = Color(0xFFE6F8EE)
    val orangeC = Color(0xFFF24A1D); val blueC = Color(0xFF1E5BFF); val purpleC = Color(0xFF7C3AED)
    return listOf(
        listOf(
            cat(FileCategory.VIDEO, "Video", R.drawable.cat_video, Icons.Filled.VideoLibrary, BAR_VIDEO, peach, peach, orangeC),
            cat(FileCategory.IMAGE, "Immagini", R.drawable.cat_image, Icons.Filled.Image, BAR_IMAGE, blue, blue, blueC),
            cat(FileCategory.AUDIO, "Audio", R.drawable.cat_audio, Icons.Filled.MusicNote, BAR_AUDIO, lilac, lilac, purpleC),
            cat(FileCategory.ARCHIVE, "Archivi", R.drawable.cat_archive, Icons.Filled.Folder, BAR_ARCH, mint, blue, blueC),
        ),
        listOf(
            cat(FileCategory.DOCUMENT, "Documenti", null, Icons.Filled.Description, BAR_NAVY, blue, blue, blueC),
            cat(FileCategory.APK, "App (APK)", null, Icons.Filled.Android, BAR_TEAL, Color(0xFFE3F7F4), blue, blueC),
            cat(FileCategory.OTHER, "Altro", null, Icons.AutoMirrored.Filled.InsertDriveFile, BAR_GREY, Color(0xFFEEF1F5), blue, blueC),
            CatItem("Download", extra["download"] ?: 0L, null, Icons.Filled.Download, BAR_NAVY, blue, blue, blueC) { openFolder(vm, "Download") },
        ),
        listOf(
            CatItem("WhatsApp", extra["whatsapp"] ?: 0L, null, Icons.Filled.Chat, BAR_ARCH, mint, mint, Color(0xFF16A34A)) { vm.tab = Tab.CLEAN; vm.sub = Sub.NONE },
            CatItem("Screenshot", extra["screenshot"] ?: 0L, null, Icons.Filled.Screenshot, BAR_AUDIO, lilac, lilac, purpleC) { vm.tab = Tab.CLEAN; vm.sub = Sub.NONE },
            CatItem("Fotocamera", extra["camera"] ?: 0L, null, Icons.Filled.CameraAlt, BAR_IMAGE, blue, blue, blueC) { vm.tab = Tab.PHOTOS; vm.sub = Sub.NONE },
            CatItem("Cestino", extra["trash"] ?: 0L, null, Icons.Filled.Delete, BAR_VIDEO, peach, peach, orangeC) { vm.tab = Tab.MORE; vm.sub = Sub.TRASH },
        ),
    )
}

/** Scheda categoria: 185,5 × 58 dp-mockup. */
@Composable
private fun Mk.CatCard(x: Float, y: Float, it: CatItem, max: Long) {
    val anim = remember { Animatable(0f) }
    val target = if (it.bytes <= 0) 0f else (it.bytes.toFloat() / max * 0.85f).coerceIn(0.06f, 1f)
    LaunchedEffect(target) { anim.animateTo(target, tween(900, easing = FastOutSlowInEasing)) }
    Box(
        Modifier.offset(x.d, y.d).size(185.5f.d, 58f.d)
            .shadow(7f.d, RoundedCornerShape(14f.d), ambientColor = Color(0x14000000), spotColor = Color(0x1A1E2A4A))
            .clip(RoundedCornerShape(14f.d)).background(Color.White)
            .pressable(it.onClick),
    ) {
        if (it.image != null) {
            Image(painterResource(it.image), null, Modifier.offset(7.3f.d, 7.5f.d).size(43.1f.d))
        } else {
            Box(
                Modifier.offset(8.8f.d, 8.75f.d).size(40.5f.d).clip(RoundedCornerShape(11f.d)).background(it.tile),
                contentAlignment = Alignment.Center,
            ) { Icon(it.icon, null, tint = it.bar.last(), modifier = Modifier.size(23f.d)) }
        }
        T(it.title, 61f, 11.5f, 9.4f, 400, MkColor.Ink, width = 100f)
        T(Fmt.size(it.bytes), 61f, 26f, 13.4f, 700, MkColor.Ink, letterSpacing = -0.2f, width = 100f)
        Box(Modifier.offset(61f.d, 43.5f.d).size(77.5f.d, 4.4f.d).clip(CircleShape).background(Color(0xFFE3E7EE))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(anim.value.coerceAtLeast(0.001f)).clip(CircleShape).background(Brush.horizontalGradient(it.bar)))
        }
        Box(
            Modifier.offset((185.5f - 19.8f - 11f).d, (29f - 11f).d).size(22f.d).clip(CircleShape).background(it.chevBg),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = it.chev, modifier = Modifier.size(16f.d)) }
    }
}

/** Ciambella della memoria, scheda bianca e didascalie con le linee curve. */
@Composable
private fun Mk.StorageBlock(vm: MainViewModel, free: Long, total: Long, used: Long, sizes: Map<FileCategory, Long>) {
    val gib = 1_073_741_824.0
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
    // Composizione dello spazio usato: video (arancio), immagini (azzurro), documenti/audio/archivi/app (blu), resto (grigio)
    val video = sizes[FileCategory.VIDEO] ?: 0L
    val img = sizes[FileCategory.IMAGE] ?: 0L
    val navy = listOf(FileCategory.DOCUMENT, FileCategory.AUDIO, FileCategory.ARCHIVE, FileCategory.APK).sumOf { sizes[it] ?: 0L }
    val rest = (used - video - img - navy).coerceAtLeast(0)
    val parts = listOf(
        video to listOf(Color(0xFFF0436F), Color(0xFFE8701F), Color(0xFFFF9A5C)),
        img to listOf(Color(0xFF7CC8FF), Color(0xFF4FA8FF)),
        navy to listOf(Color(0xFF2E55B8), Color(0xFF1C3A85)),
        rest to listOf(Color(0xFFC5CBD4), Color(0xFFD7DCE3)),
    ).filter { it.first > 0 }
    val sum = parts.sumOf { it.first }.coerceAtLeast(1)

    // Scheda bianca dietro (la ciambella sporge sopra)
    Box(
        Modifier.offset(17.5f.d, 228.7f.d).size(377.5f.d, 136.3f.d)
            .shadow(10f.d, RoundedCornerShape(26f.d), ambientColor = Color(0x10000000), spotColor = Color(0x141E2A4A))
            .clip(RoundedCornerShape(26f.d)).background(Color(0xFFFDFEFF))
            .border(1f.d, Color(0xFFF0F2F6), RoundedCornerShape(26f.d))
            .pressable({ vm.tab = Tab.FILES; vm.sub = Sub.NONE }),
    )
    Canvas(Modifier.fillMaxWidth().height(380f.d)) {
        val k = size.width / 412f
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        val c = p(205f, 285f)
        // alone chiaro
        drawCircle(Brush.radialGradient(listOf(Color(0x141E2A4A), Color.Transparent), c, 112f * k), 112f * k, c)
        drawCircle(Color(0xF2FFFFFF), 99f * k, c)
        drawCircle(Color(0xFFE8ECF2), 99f * k, c, style = Stroke(1f * k))
        // binario
        drawCircle(Color(0xFFEEF1F6), 73.5f * k, c, style = Stroke(21f * k))
        // segmenti a tessere
        var start = 140f
        val total360 = 360f * sweep.value
        parts.forEach { (bytes, cols) ->
            val span = (bytes.toFloat() / sum) * total360
            val tiles = kotlin.math.ceil(span / 42f).toInt().coerceAtLeast(1)
            val tileSpan = span / tiles
            repeat(tiles) { t ->
                val f = if (tiles == 1) 0.5f else t / (tiles - 1f)
                val col = gradAt(cols, f)
                val s = start + t * tileSpan + 1.2f
                val sw = (tileSpan - 2.4f).coerceAtLeast(0.5f)
                drawArc(col, s, sw, false, topLeft = Offset(c.x - 73.5f * k, c.y - 73.5f * k),
                    size = androidx.compose.ui.geometry.Size(147f * k, 147f * k), style = Stroke(20f * k))
                // riflesso interno
                drawArc(Color.White.copy(alpha = 0.22f), s, sw, false, topLeft = Offset(c.x - 81f * k, c.y - 81f * k),
                    size = androidx.compose.ui.geometry.Size(162f * k, 162f * k), style = Stroke(3f * k))
            }
            start += span
        }
        // disco centrale
        drawCircle(Brush.radialGradient(listOf(Color(0x221E2A4A), Color.Transparent), c, 60f * k), 60f * k, c)
        drawCircle(Color.White, 52f * k, c)
        drawCircle(Color(0xFFEDF0F5), 52f * k, c, style = Stroke(1f * k))
        // icona disco
        val dl = p(198.2f, 330.9f)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFA3AEBF), Color(0xFF7D8899)), dl.y, dl.y + 11f * k), dl,
            androidx.compose.ui.geometry.Size(17f * k, 11f * k), androidx.compose.ui.geometry.CornerRadius(2.5f * k))
        drawLine(Color.White.copy(alpha = 0.7f), p(198.6f, 337f), p(214.8f, 337f), 0.8f * k)
        drawCircle(Color.White, 0.9f * k, p(209.5f, 339.2f)); drawCircle(Color.White, 0.9f * k, p(212f, 339.2f))
        // linea arancio a sinistra
        callout(Color(0xFFFF8A3D), p(31.7f, 292.9f), p(92f, 292.9f), p(103f, 292.9f), p(106f, 276f), p(115f, 276f), k)
        drawCircle(Color(0xFFFF8A3D), 1.5f * k, p(117f, 325f))
        // linea blu a destra
        val blue = Color(0xFF1E7BFF)
        val path = Path().apply { moveTo(307.8f * k, 282.3f * k); cubicTo(318f * k, 282.3f * k, 322f * k, 270f * k, 337.7f * k, 270f * k) }
        drawPath(path, blue, style = Stroke(1f * k))
        drawCircle(blue, 2.4f * k, p(337.7f, 270f))
        drawCircle(blue, 1.4f * k, p(307.4f, 264f))
    }
    val usedTxt = String.format(Locale.ITALY, "%.1f GB", used / gib)
    T("${(used * 100 / total.coerceAtLeast(1))}%", 35.2f, 248.5f, 17.2f, 800, MkColor.Ink, letterSpacing = -0.3f)
    T("utilizzato", 37.8f, 270.4f, 12.1f, 400, MkColor.Slate)
    T(usedTxt, 37.4f, 305.2f, 12.3f, 600, MkColor.Ink)
    T("in uso", 38.3f, 319.7f, 9.5f, 400, MkColor.Slate)
    T(String.format(Locale.ITALY, "%.1f", free / gib), 145f, 259.9f, 29.5f, 800, MkColor.Ink, width = 120f, align = TextAlign.Center, letterSpacing = -0.8f)
    T("GB liberi", 145f, 292f, 14.4f, 700, MkColor.Ink, width = 120f, align = TextAlign.Center, letterSpacing = -0.2f)
    T("su ${String.format(Locale.ITALY, "%.0f", total / gib)} GB", 145f, 310.9f, 9.4f, 400, MkColor.Slate, width = 120f, align = TextAlign.Center)
    T(String.format(Locale.ITALY, "%.0f GB", total / gib), 348.3f, 263.9f, 11.2f, 600, MkColor.Ink)
    T("totale", 348.3f, 278.4f, 9.6f, 400, MkColor.Slate)
}

private fun DrawScope.callout(col: Color, dot: Offset, a: Offset, c1: Offset, c2: Offset, end: Offset, k: Float) {
    drawCircle(col, 2.3f * k, dot)
    val path = Path().apply { moveTo(dot.x, dot.y); lineTo(a.x, a.y); cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y) }
    drawPath(path, Brush.horizontalGradient(listOf(col, col.copy(alpha = 0.55f)), dot.x, end.x), style = Stroke(1f * k))
}

private fun gradAt(cols: List<Color>, f: Float): Color {
    if (cols.size == 1) return cols[0]
    val pos = f * (cols.size - 1)
    val i = pos.toInt().coerceAtMost(cols.size - 2)
    return lerp(cols[i], cols[i + 1], pos - i)
}

/** Scheda "Suggerimento di Jarvis" con pulsante a gradiente e illustrazione. */
@Composable
private fun Mk.Suggestion(tip: HomeTip, onClick: () -> Unit) {
    Box(
        Modifier.offset(17.2f.d, 513.6f.d).size(377.7f.d, 141.6f.d)
            .shadow(8f.d, RoundedCornerShape(22f.d), ambientColor = Color(0x10000000), spotColor = Color(0x14FF7A2E))
            .clip(RoundedCornerShape(22f.d))
            .background(Brush.linearGradient(listOf(Color(0xFFFFF6EC), Color(0xFFFEF3EB), Color(0xFFFDF2EA))))
            .border(1f.d, Color(0xFFFFFBF7), RoundedCornerShape(22f.d))
            .pressable(onClick),
    ) {
        Image(painterResource(R.drawable.jarvis_illus), null, Modifier.offset((237.5f - 17.2f).d, 1.8f.d).size(154.8f.d, 134.6f.d))
        Canvas(Modifier.offset((38.7f - 17.2f - 6.5f).d, (533.4f - 513.6f - 6.5f).d).size(13f.d)) {
            val w = size.width; val h = size.height
            val star = Path().apply {
                moveTo(w / 2, 0f); quadraticTo(w * 0.56f, h * 0.44f, w, h / 2); quadraticTo(w * 0.56f, h * 0.56f, w / 2, h)
                quadraticTo(w * 0.44f, h * 0.56f, 0f, h / 2); quadraticTo(w * 0.44f, h * 0.44f, w / 2, 0f); close()
            }
            drawPath(star, Brush.linearGradient(listOf(Color(0xFFFF9A3D), Color(0xFFFF4A14))))
        }
        T("Suggerimento di Jarvis", 52.3f - 17.2f, 529.9f - 513.6f, 9.4f, 400, Color(0xFFFF5A1F))
        Row(
            Modifier.offset((38.3f - 17.2f).d, (550.1f - 513.6f - 0.2417f * 22.7f).d).width(205f.d),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(tip.big, Modifier.alignByBaseline(), style = style(22.7f, 800, MkColor.Ink).copy(letterSpacing = sp(-0.6f)), maxLines = 1)
            Text(" ${tip.title}", Modifier.alignByBaseline(), style = style(16.2f, 700, MkColor.Ink).copy(letterSpacing = sp(-0.3f)),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        T(tip.sub, 38.3f - 17.2f, 580f - 513.6f, 10.9f, 400, Color(0xFF2F3B4E), width = 178f, lineHeight = 15f, maxLines = 2)
        Row(
            Modifier.offset((38.3f - 17.2f).d, (613f - 513.6f).d).size(196f.d, 33f.d).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Color(0xFF1E91E8), Color(0xFF7731DE), Color(0xFFFD893F))))
                .pressable(onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(18.3f.d))
            Icon(Icons.Filled.CleaningServices, null, tint = Color.White, modifier = Modifier.size(16f.d))
            Spacer(Modifier.width(13.2f.d))
            Text(
                when (tip.kind) { "download" -> "Metti in ordine"; "trash" -> "Apri il cestino"; "chat" -> "Parla con Jarvis"; "whatsapp" -> "Pulisci WhatsApp"; else -> "Avvia pulizia" },
                Modifier.weight(1f), style = style(11.3f, 500, Color.White), maxLines = 1,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(16f.d))
            Spacer(Modifier.width(10f.d))
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
