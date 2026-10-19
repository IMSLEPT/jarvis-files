package com.jarvis.files.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.jarvis.files.automation.Automations
import com.jarvis.files.automation.PhotoIndexWorker
import com.jarvis.files.core.Storage
import com.jarvis.files.photos.BitmapLoader
import com.jarvis.files.photos.PhotoDb
import com.jarvis.files.photos.PhotoIndexer
import com.jarvis.files.photos.PhotoSearch
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.Tab
import com.jarvis.files.ui.components.ArcReactor
import com.jarvis.files.ui.components.FileThumb
import com.jarvis.files.ui.components.GhostButton
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.GradientButton
import com.jarvis.files.ui.components.LinearMeter
import com.jarvis.files.ui.components.SectionTitle
import com.jarvis.files.ui.theme.J
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PhotosScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf("") }
    val db = remember { PhotoDb.get(ctx) }
    var refresh by remember { mutableStateOf(0) }
    val counts by produceState(Triple(0, 0, 0), refresh) { value = withContext(Dispatchers.IO) { db.counts() } }
    val totalPhotos by produceState(0) { value = withContext(Dispatchers.IO) { PhotoIndexer.candidates().size } }
    val persons by produceState(emptyList<com.jarvis.files.photos.Person>(), refresh) { value = withContext(Dispatchers.IO) { db.persons() } }
    val recent by produceState(emptyList<String>(), refresh) {
        value = withContext(Dispatchers.IO) { PhotoIndexer.candidates().sortedByDescending { it.modified }.take(120).map { it.rel } }
    }
    val work by WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(PhotoIndexWorker.UNIQUE).collectAsState(emptyList())
    val running = work.firstOrNull()?.state == WorkInfo.State.RUNNING || work.firstOrNull()?.state == WorkInfo.State.ENQUEUED
    val prog = work.firstOrNull()?.progress
    LaunchedEffect(running) { if (!running) refresh++ }

    fun doSearch(text: String) {
        if (text.isBlank()) { vm.photoResults = null; return }
        searching = true
        scope.launch {
            val (q, hits) = withContext(Dispatchers.IO) { PhotoSearch.search(ctx, text) }
            why = listOfNotNull(
                q.people.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Persone: "),
                q.labelsEn.takeIf { it.isNotEmpty() }?.take(5)?.joinToString(prefix = "Oggetti: "),
                q.place?.let { "Luogo: $it" },
            ).joinToString(" · ")
            vm.photoResults = hits
            searching = false
        }
    }

    val results = vm.photoResults
    val gridItems: List<String> = results?.map { it.path } ?: recent

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("FOTO", style = MaterialTheme.typography.labelSmall, color = J.Cyan)
                Text("Cerca come su Google Foto", style = MaterialTheme.typography.headlineSmall, color = J.Text)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("es. foto di Leo, montagne, lago Nero, Ginger sul divano", color = J.Muted) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = J.Cyan) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch(query) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = J.Blue, unfocusedBorderColor = J.Stroke, focusedTextColor = J.Text, unfocusedTextColor = J.Text,
                        focusedContainerColor = J.Card, unfocusedContainerColor = J.Card, cursorColor = J.Electric,
                    ),
                )
                Spacer(Modifier.height(12.dp))

                GlassCard(Modifier.fillMaxWidth(), highlight = running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArcReactor(40.dp, active = running)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (running) "Analisi in corso · ${prog?.getString("phase") ?: "avvio"}" else "Foto analizzate: ${counts.first} di $totalPhotos",
                                color = J.Text, style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                if (running) "${prog?.getInt("done", 0) ?: 0} di ${prog?.getInt("total", 0) ?: 0}"
                                else "${counts.third} volti trovati · ${counts.second} descrizioni dettagliate",
                                color = J.Muted, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearMeter(if (totalPhotos == 0) 0f else counts.first.toFloat() / totalPhotos)
                    if (!running) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GradientButton("Analizza ora", icon = Icons.Filled.PlayArrow) { Automations.indexPhotosNow(ctx) }
                            GhostButton("Persone", icon = Icons.Filled.Face) { vm.tab = Tab.MORE; vm.sub = Sub.PEOPLE }
                        }
                        Text(
                            "Ogni notte, quando il telefono è in carica, Jarvis analizza da solo le foto nuove.",
                            color = J.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                if (persons.isNotEmpty()) {
                    SectionTitle("Persone")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(persons, key = { it.id }) { p ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                                query = "foto di ${p.name}"; doSearch(query)
                            }) {
                                FaceAvatar(p.coverFaceId, 64.dp)
                                Text(p.name, color = J.Text, style = MaterialTheme.typography.labelLarge)
                                Text("${p.count}", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                SectionTitle(
                    when {
                        searching -> "Cerco…"
                        results != null -> "${results.size} risultati" + if (why.isNotBlank()) " · $why" else ""
                        else -> "Ultime foto"
                    }
                )
                if (results != null) Text("Mostra tutte le foto recenti", color = J.Cyan, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp).clickable { vm.photoResults = null; query = "" })
            }
        }
        items(gridItems, key = { it }) { path ->
            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { Storage.openFile(ctx, Storage.file(path)) }) {
                FileThumb(Storage.file(path), 200.dp, Modifier.fillMaxSize())
            }
        }
    }
}

/** Ritaglio del volto (stesse coordinate della foto ridotta a 1024 px usata nell'analisi). */
@Composable
fun FaceAvatar(faceId: Long, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, faceId) {
        value = withContext(Dispatchers.IO) { faceCrop(ctx, faceId) }
    }
    Box(modifier.size(size).clip(CircleShape).background(J.CardHi), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.Face, null, tint = J.Muted)
    }
}

private val faceCache = android.util.LruCache<Long, Bitmap>(200)

fun faceCrop(ctx: android.content.Context, faceId: Long): Bitmap? {
    faceCache.get(faceId)?.let { return it }
    val f = PhotoDb.get(ctx).faces("id = ?", arrayOf(faceId.toString())).firstOrNull() ?: return null
    val src = BitmapLoader.load(Storage.file(f.path), 1024) ?: return null
    val (l, t, r, b) = f.box.toList()
    if (r > src.width || b > src.height || r - l <= 0 || b - t <= 0) { src.recycle(); return null }
    val crop = Bitmap.createScaledBitmap(Bitmap.createBitmap(src, l, t, r - l, b - t), 160, 160, true)
    src.recycle()
    faceCache.put(faceId, crop)
    return crop
}
