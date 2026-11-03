package com.jarvis.files.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jarvis.files.core.*
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.components.Chip
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.GhostButton
import com.jarvis.files.ui.components.UsageAccessGuide
import com.jarvis.files.ui.theme.J
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

@Composable
fun AppsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    var period by rememberSaveable { mutableIntStateOf(7) }
    var query by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf(AppOrder.NAME) }
    var includeSystem by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var inventory by remember { mutableStateOf<AppInventory?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reload++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { reload++ }
    LaunchedEffect(period, reload) {
        loading = true; error = null; inventory = null
        try {
            val loaded = withContext(Dispatchers.IO) { AppInsights.load(ctx, period) }
            inventory = loaded
            if (!loaded.usageAvailable && order !in listOf(AppOrder.NAME, AppOrder.LARGE)) order = AppOrder.NAME
        }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = "Non riesco a leggere le app. Riprova con Aggiorna." }
        finally { loading = false }
    }
    BackHandler(selected != null) { selected = null }
    val data = inventory
    val chosen = data?.apps?.firstOrNull { it.pkg == selected }
    val all = data?.apps.orEmpty().filter { (includeSystem || !it.system) && (it.name.contains(query, true) || it.pkg.contains(query, true)) }
    val apps = AppInsights.ordered(all, order)
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().testTag("apps_list"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SubHeader(if (chosen == null) "App installate" else chosen.name, "Spazio e tempo di utilizzo") { if (selected != null) selected = null else vm.sub = Sub.NONE } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (data == null) "Lettura delle app…" else "${data.apps.size} app · profilo corrente", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { reload++ }, enabled = !loading) { Text("Aggiorna") }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Analizzo app e spazio occupato…", color = J.Muted) }
        if (error != null) item { Text(error!!, color = J.Red) }
        if (data != null && !data.usageAccess) item {
            UsageAccessGuide { runCatching { permission.launch(AppCache.usageAccessIntent()) }.onFailure { vm.toast = "Apri Impostazioni Android → Accesso ai dati di utilizzo → Jarvis Files" } }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Oggi", 7 to "7 giorni", 30 to "30 giorni").forEach { (days, label) -> Chip(label, period == days) { period = days } }
            }
        }
        if (chosen != null) {
            item {
                GlassCard(Modifier.fillMaxWidth(), highlight = true) {
                    AppIdentity(chosen)
                    Spacer(Modifier.height(12.dp))
                    Text(AppInsights.duration(chosen.foregroundMs), style = MaterialTheme.typography.headlineMedium, color = J.Blue)
                    Text("Tempo registrato in primo piano · ${if (period == 1) "oggi" else "$period giorni"}", color = J.Muted)
                    Text(if (chosen.lastUsed != null) "Ultimo uso registrato: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ITALY).format(java.util.Date(chosen.lastUsed))}" else "Ultimo uso: non disponibile nei dati Android", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Utilizzo giornaliero", style = MaterialTheme.typography.titleMedium, color = J.Text)
                    if (chosen.days != null) UsageChart(chosen.days) else Text("Concedi l'accesso ai dati di utilizzo per vedere il grafico.", color = J.Muted)
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Spazio occupato", style = MaterialTheme.typography.titleMedium, color = J.Text)
                    SizeLine("Totale", chosen.totalBytes)
                    SizeLine("App", chosen.appBytes); SizeLine("Dati, cache inclusa", chosen.dataBytes); SizeLine("Di cui cache", chosen.cacheBytes)
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Impostazioni app") { runCatching { ctx.startActivity(AppCache.appDetailsIntent(chosen.pkg)) }.onFailure { vm.toast = "Scheda app non disponibile" } }
                }
            }
        } else {
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("apps_search"), label = { Text("Cerca un'app o un pacchetto") }, singleLine = true, shape = RoundedCornerShape(18.dp))
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Includi app di sistema", color = J.Text)
                    Switch(includeSystem, { includeSystem = it })
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOrder.entries.forEach { sort ->
                        if (sort == AppOrder.NAME || sort == AppOrder.LARGE || data?.usageAvailable == true) Chip(sort.label, order == sort) { order = sort }
                    }
                }
            }
            if (order == AppOrder.INACTIVE) item { Text("App installate da almeno 30 giorni con ultimo utilizzo noto precedente agli ultimi 30 giorni. Le app senza storico sufficiente non sono classificate come inattive. Non significa che siano arrestate: possono lavorare in background. Lo storico Android può essere parziale.", color = J.Muted, style = MaterialTheme.typography.bodySmall) }
            if (data?.usageAvailable == true && order != AppOrder.INACTIVE) item {
                GlassCard(Modifier.fillMaxWidth(), highlight = true) {
                    Text("Utilizzo delle app in elenco", style = MaterialTheme.typography.titleMedium, color = J.Text)
                    Text(AppInsights.duration(all.sumOf { it.foregroundMs ?: 0 }), style = MaterialTheme.typography.headlineMedium, color = J.Blue)
                    Text("Tocca un'app per il suo grafico giornaliero.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
                    val top = all.filter { (it.foregroundMs ?: 0) > 0 }.sortedByDescending { it.foregroundMs }.take(5)
                    val max = top.firstOrNull()?.foregroundMs?.coerceAtLeast(1) ?: 1
                    top.forEach { app ->
                        Row(Modifier.fillMaxWidth().clickable { selected = app.pkg }.padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(app.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = J.Text)
                            Text(AppInsights.duration(app.foregroundMs), color = J.Blue, style = MaterialTheme.typography.labelMedium)
                        }
                        Box(Modifier.fillMaxWidth().height(6.dp).background(J.Stroke, RoundedCornerShape(6.dp))) {
                            Box(Modifier.fillMaxWidth(((app.foregroundMs ?: 0).toFloat() / max).coerceIn(0f, 1f)).height(6.dp).background(J.Blue, RoundedCornerShape(6.dp)))
                        }
                    }
                }
            }
            if (!loading && data != null && apps.isEmpty()) item { Text("Nessuna app corrisponde a questi filtri.", color = J.Muted) }
            items(apps, key = { it.pkg }) { app ->
                GlassCard(Modifier.fillMaxWidth().testTag("app_${app.pkg}"), onClick = { selected = app.pkg }) {
                    AppIdentity(app)
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(app.totalBytes?.let(Fmt::size) ?: "Spazio non disponibile", color = J.Blue, style = MaterialTheme.typography.labelMedium)
                        Text(AppInsights.duration(app.foregroundMs), color = J.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item { Text("Statistiche giornaliere fornite da Android, aggiornate al caricamento. Lo storico può essere incompleto dopo ripristini o cancellazioni dei dati di utilizzo. Il tempo in primo piano esclude l'audio in background; il totale può includere app usate insieme. Dati elaborati sul telefono.", color = J.Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun AppIdentity(app: InstalledApp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val bitmap = remember(app.icon) { app.icon?.toBitmap(96, 96)?.asImageBitmap() }
        if (bitmap != null) Image(bitmap, null, Modifier.size(44.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, color = J.Text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(app.pkg, color = J.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (app.system) Text("Sistema", color = J.Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SizeLine(label: String, bytes: Long?) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = J.Muted); Text(bytes?.let(Fmt::size) ?: "Non disponibile", color = J.Text)
    }
}

@Composable
private fun UsageChart(days: List<UsageDay>) {
    val blue = J.Blue
    val muted = J.Stroke
    val maximum = days.mapNotNull { it.millis }.maxOrNull()?.coerceAtLeast(1) ?: 1
    Text("Scala: ${AppInsights.duration(maximum.takeIf { it > 1 } ?: 0)}", color = J.Muted, style = MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(130.dp).padding(top = 12.dp).semantics {
        contentDescription = "Grafico utilizzo giornaliero: " + days.joinToString { "${it.date}: ${AppInsights.duration(it.millis)}" }
    }) {
        val slot = size.width / days.size.coerceAtLeast(1)
        drawLine(muted, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        days.forEachIndexed { i, day ->
            val value = day.millis ?: 0
            val h = if (value == 0L) 0f else (value.toFloat() / maximum * size.height).coerceAtLeast(2f)
            if (h > 0) drawRoundRect(blue, Offset(i * slot + slot * .18f, size.height - h), Size(slot * .64f, h), CornerRadius(4f, 4f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(days.firstOrNull()?.date?.format(DateTimeFormatter.ofPattern("dd/MM")) ?: "", color = J.Muted, style = MaterialTheme.typography.labelSmall)
        Text(days.lastOrNull()?.date?.format(DateTimeFormatter.ofPattern("dd/MM")) ?: "", color = J.Muted, style = MaterialTheme.typography.labelSmall)
    }
    if (days.any { it.millis == null }) Text("Giorni senza storico: Non disponibile. Il totale comprende i soli giorni registrati.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
    // Valori esatti accessibili anche senza interpretare il grafico.
    Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
        days.asReversed().forEach { day ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(day.date.format(DateTimeFormatter.ofPattern("dd/MM")), color = J.Muted, style = MaterialTheme.typography.bodySmall)
                Text(AppInsights.duration(day.millis), color = J.Text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
