package com.jarvis.files.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.files.ai.AiUnavailable
import com.jarvis.files.ai.Brain
import com.jarvis.files.ai.ChatMsg
import com.jarvis.files.ai.FollowUp
import com.jarvis.files.automation.PendingPlan
import com.jarvis.files.core.FileEntry
import com.jarvis.files.core.FileIndex
import com.jarvis.files.core.FileOps
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.Plan
import com.jarvis.files.core.Storage
import com.jarvis.files.core.Undo
import com.jarvis.files.photos.PhotoHit
import com.jarvis.files.photos.PhotoSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlanState { PENDING, RUNNING, DONE, FAILED, CANCELLED }

data class UiMsg(
    val id: Long,
    val role: String,
    val text: String,
    val questions: List<FollowUp> = emptyList(),
    val plan: Plan? = null,
    val planState: PlanState = PlanState.PENDING,
    val photoHits: List<PhotoHit>? = null,
    val fileHits: List<FileEntry>? = null,
    val engine: String = "",
    val error: Boolean = false,
)

enum class Tab { HOME, FILES, CHAT, CLEAN, PHOTOS, MORE }

/** Schermate secondarie aperte sopra le schede. */
enum class Sub { NONE, SECURITY, BATTERY, VAULT, AUTOMATIONS, SETTINGS, TRASH, PEOPLE, FIND, CATEGORY }

/** Suggerimento mostrato in Home (l'azione è decisa dalla UI in base al tipo). */
data class HomeTip(val kind: String, val title: String, val big: String, val sub: String)

/** Dati della Home calcolati in background una volta per scansione. */
data class HomeData(
    val byCat: List<Pair<com.jarvis.files.core.FileCategory, Long>> = emptyList(),
    val tips: List<HomeTip> = emptyList(),
    val recent: List<String> = emptyList(),
    /** Spazio di cartelle note: whatsapp, screenshot, camera, download, trash. */
    val extra: Map<String, Long> = emptyMap(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    var tab by mutableStateOf(Tab.HOME)
    var sub by mutableStateOf(Sub.NONE)

    val messages = mutableStateListOf<UiMsg>()
    var busy by mutableStateOf(false)
        private set
    var busyText by mutableStateOf("")
        private set

    var undoInfo by mutableStateOf<Pair<String, Int>?>(null)
        private set
    var pending by mutableStateOf<Triple<String, String, Plan>?>(null)
        private set
    var toast by mutableStateOf<String?>(null)

    /** Risultati di ricerca foto da mostrare nella scheda Foto. */
    var photoResults by mutableStateOf<List<PhotoHit>?>(null)

    var scanning by mutableStateOf(false)
        private set
    var fileCount by mutableStateOf(0)
        private set

    var chatInput by mutableStateOf("")
    var operationBusy by mutableStateOf(false)
        private set
    var filesRevision by mutableStateOf(0)
        private set
    private var chatJob: Job? = null
    private var chatGeneration = 0L
    private var homeJob: Job? = null
    private var nextId = 1L

    /** Ascolto vocale aperto (sopra qualsiasi schermata). */
    var voiceOpen by mutableStateOf(false)

    /** Categoria aperta nella schermata elenco file. */
    var category by mutableStateOf<com.jarvis.files.core.FileCategory?>(null)

    /** Cartella aperta nella stessa schermata (alternativa alla categoria). */
    var cleanSection by mutableStateOf("CACHE")

    var folder by mutableStateOf<String?>(null)

    var searchResults by mutableStateOf<List<FileEntry>?>(null)

    /** File condivisi da altre app verso Jarvis, in attesa di una scelta. */
    var shared by mutableStateOf<List<android.net.Uri>>(emptyList())

    var home by mutableStateOf(HomeData())
        private set
    private var homeAt = 0L

    /** Calcola i dati della Home (categorie, suggerimenti, ultime foto) al massimo ogni 5 minuti. */
    fun refreshHome(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - homeAt < 5 * 60_000 && home.byCat.isNotEmpty()) return
        homeJob?.cancel()
        homeAt = System.currentTimeMillis()
        homeJob = viewModelScope.launch {
            try {
            val data = withContext(Dispatchers.IO) {
            val entries = FileIndex.ensure()
            val byCat = entries.groupBy { it.category }.map { it.key to it.value.sumOf { e -> e.size } }.sortedByDescending { it.second }
            val recent = entries.filter { it.category == com.jarvis.files.core.FileCategory.IMAGE && !it.rel.contains("/.") && it.size > 25_000 }
                .sortedByDescending { it.modified }.take(12).map { it.rel }
            val tips = ArrayList<HomeTip>()
            runCatching {
                val junk = com.jarvis.files.core.Cleaner.junk(ctx)
                if (junk.isNotEmpty()) tips += HomeTip("junk", "di file inutili", Fmt.size(junk.sumOf { it.size }),
                    "File temporanei, cache e residui che puoi rimuovere in sicurezza.")
            }
            runCatching {
                val wa = com.jarvis.files.core.Cleaner.whatsapp().sumOf { it.size }
                if (wa > 500_000_000) tips += HomeTip("whatsapp", "occupati da WhatsApp", Fmt.size(wa), "Vocali, video dei gruppi e media già inviati.")
            }
            val dl = entries.count { it.folder == "Download" }
            if (dl > 15) tips += HomeTip("download", "file sparsi in Download", "$dl", "Jarvis può metterli in ordine in sottocartelle in un attimo.")
            runCatching {
                val trash = com.jarvis.files.core.Trash.totalSize(ctx)
                if (trash > 200_000_000) tips += HomeTip("trash", "nel cestino", Fmt.size(trash), "Svuotalo per liberare spazio subito.")
            }
            val extra = HashMap<String, Long>()
            extra["whatsapp"] = entries.filter { it.rel.contains("whatsapp", true) }.sumOf { it.size }
            extra["screenshot"] = entries.filter { it.rel.contains("/Screenshots/", true) }.sumOf { it.size }
            extra["camera"] = entries.filter { it.rel.startsWith("DCIM/Camera/") }.sumOf { it.size }
            extra["download"] = entries.filter { it.rel.startsWith("Download/") }.sumOf { it.size }
            extra["trash"] = runCatching { com.jarvis.files.core.Trash.totalSize(ctx) }.getOrDefault(0L)
            HomeData(byCat, tips, recent, extra)
            }
            home = data
            fileCount = FileIndex.entries.size
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { homeAt = 0; toast = "Impossibile aggiornare lo spazio: ${e.message}" }
        }
    }

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        undoInfo = Undo.info(ctx)
        pending = PendingPlan.load(ctx)
    }

    fun rescan() {
        if (scanning || !Storage.hasAllFilesAccess()) return
        scanning = true
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { Storage.scanAll() }
                fileCount = list.size
                filesRevision++
                refreshHome(force = true)
            } catch (e: Exception) {
                toast = "Scansione non riuscita: ${e.message}"
            } finally { scanning = false }
        }
    }

    fun openChatWith(text: String) {
        tab = Tab.CHAT
        sub = Sub.NONE
        send(text)
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (busy || operationBusy) { toast = "Attendi la risposta oppure interrompila prima di inviare un nuovo comando"; return }
        messages.indices.forEach { i ->
            if (messages[i].planState == PlanState.PENDING && messages[i].plan != null)
                messages[i] = messages[i].copy(planState = PlanState.CANCELLED)
        }
        val generation = ++chatGeneration
        messages.add(UiMsg(nextId++, "user", t))
        busy = true
        busyText = "Sto analizzando…"
        chatJob = viewModelScope.launch {
            try {
                if (!Storage.hasAllFilesAccess()) throw AiUnavailable("Prima concedi l'accesso a tutti i file (Impostazioni → Permessi).")
                val history = messages.filter { !it.error }.takeLast(10).map {
                    ChatMsg(if (it.role == "user") "user" else "assistant", it.text + if (it.questions.isEmpty()) "" else it.questions.joinToString(prefix = "\n", separator = "\n") { q -> q.question + " " + q.options.joinToString(" / ") })
                }
                val (reply, engine) = withContext(Dispatchers.IO) {
                    Storage.scanAll()
                    Brain.ask(ctx, history)
                }
                ensureActive()
                if (generation != chatGeneration) return@launch
                var photoHits: List<PhotoHit>? = null
                var fileHits: List<FileEntry>? = reply.fileResults
                reply.photoSearch?.let { q ->
                    busyText = "Cerco nelle foto…"
                    photoHits = withContext(Dispatchers.IO) { PhotoSearch.search(ctx, q).second }
                }
                reply.fileQuery?.let { q -> fileHits = withContext(Dispatchers.IO) { Brain.searchFiles(q) } }
                ensureActive()
                if (generation != chatGeneration) return@launch
                messages.add(
                    UiMsg(
                        nextId++, "assistant", reply.reply, reply.questions,
                        plan = reply.plan?.takeIf { !it.isEmpty || it.warnings.isNotEmpty() },
                        photoHits = photoHits, fileHits = fileHits, engine = engine,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiUnavailable) {
                messages.add(UiMsg(nextId++, "assistant", e.message ?: "AI non disponibile.", error = true))
            } catch (e: Exception) {
                messages.add(UiMsg(nextId++, "assistant", "Qualcosa è andato storto: ${e.message}", error = true))
            } finally {
                if (generation == chatGeneration) { busy = false; busyText = "" }
            }
        }
    }

    fun executePlan(msgId: Long, plan: Plan) {
        val idx = messages.indexOfFirst { it.id == msgId }
        if (idx < 0 || messages[idx].planState != PlanState.PENDING || operationBusy || busy || plan.isEmpty) return
        operationBusy = true
        messages[idx] = messages[idx].copy(planState = PlanState.RUNNING)
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { FileOps.execute(ctx, plan, "Comando a Jarvis") }
                val i = messages.indexOfFirst { it.id == msgId }
                if (i >= 0) messages[i] = messages[i].copy(planState = if (r.failed == 0) PlanState.DONE else PlanState.FAILED)
                val txt = buildString {
                    append("${r.done} operazioni completate.")
                    if (r.freedBytes > 0) append(" ${Fmt.size(r.freedBytes)} spostati nel cestino.")
                    if (r.failed > 0) append(" ${r.failed} non riuscite: ${r.errors.take(2).joinToString("; ")}")
                }
                messages.add(UiMsg(nextId++, "assistant", txt, error = r.failed > 0))
                filesChanged()
            } catch (e: Exception) {
                val i = messages.indexOfFirst { it.id == msgId }
                if (i >= 0) messages[i] = messages[i].copy(planState = PlanState.FAILED)
                toast = "Operazione non completata: ${e.message}"
            } finally { operationBusy = false; refreshStatus() }
        }
    }

    fun cancelPlan(msgId: Long) {
        val idx = messages.indexOfFirst { it.id == msgId }
        if (idx >= 0 && messages[idx].planState == PlanState.PENDING)
            messages[idx] = messages[idx].copy(planState = PlanState.CANCELLED)
    }

    fun filesChanged() {
        FileIndex.invalidate()
        filesRevision++
        refreshHome(force = true)
    }

    fun runPlan(plan: Plan, source: String, after: () -> Unit = {}) {
        if (operationBusy || busy || plan.isEmpty) return
        operationBusy = true
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { FileOps.execute(ctx, plan, source) }
                toast = "${r.done} operazioni completate" +
                    (if (r.freedBytes > 0) " · ${Fmt.size(r.freedBytes)} nel cestino" else "") +
                    (if (r.failed > 0) " · ${r.failed} non riuscite" else "")
                filesChanged()
                after()
            } catch (e: Exception) { toast = "Operazione non completata: ${e.message}" }
            finally { operationBusy = false; refreshStatus() }
        }
    }

    fun confirmPending(plan: Plan) {
        if (operationBusy || busy || plan.isEmpty) return
        val source = pending?.first ?: "Automazione"
        PendingPlan.clear(ctx)
        pending = null
        runPlan(plan, source)
    }

    fun dismissPending() {
        PendingPlan.clear(ctx)
        pending = null
    }

    fun undo() {
        if (operationBusy || busy) return
        operationBusy = true
        viewModelScope.launch {
            try {
                val (ok, ko) = withContext(Dispatchers.IO) { Undo.undo(ctx) }
                toast = if (ko == 0) "Annullato: $ok operazioni ripristinate" else "Ripristinate $ok, $ko non riuscite"
                filesChanged()
            } catch (e: Exception) { toast = "Ripristino non riuscito: ${e.message}" }
            finally { operationBusy = false; refreshStatus() }
        }
    }

    fun stopReply() {
        chatGeneration++
        com.jarvis.files.ai.LocalAi.cancelGeneration()
        chatJob?.cancel()
        chatJob = null
        busy = false
        busyText = ""
    }

    fun retryLast() {
        if (busy || operationBusy) return
        val i = messages.indexOfLast { it.role == "user" }
        if (i < 0) return
        val text = messages[i].text
        while (messages.size > i) messages.removeAt(messages.lastIndex)
        send(text)
    }

    fun clearChat() {
        if (operationBusy) { toast = "Attendi il completamento dell'operazione"; return }
        stopReply()
        messages.clear()
        chatInput = ""
    }
}
