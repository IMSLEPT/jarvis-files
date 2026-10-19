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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlanState { PENDING, RUNNING, DONE, CANCELLED }

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

enum class Tab { HOME, CHAT, CLEAN, PHOTOS, MORE }

/** Schermate secondarie aperte sopra le schede. */
enum class Sub { NONE, SECURITY, BATTERY, VAULT, AUTOMATIONS, SETTINGS, TRASH, PEOPLE, FIND }

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

    private var nextId = 1L

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
            val list = withContext(Dispatchers.IO) { runCatching { Storage.scanAll { fileCount = it } }.getOrDefault(emptyList()) }
            fileCount = list.size
            scanning = false
        }
    }

    fun openChatWith(text: String) {
        tab = Tab.CHAT
        sub = Sub.NONE
        send(text)
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        messages.add(UiMsg(nextId++, "user", t))
        busy = true
        busyText = "Sto analizzando…"
        viewModelScope.launch {
            try {
                if (!Storage.hasAllFilesAccess()) throw AiUnavailable("Prima concedi l'accesso a tutti i file (Impostazioni → Permessi).")
                val history = messages.filter { !it.error }.takeLast(10).map {
                    ChatMsg(if (it.role == "user") "user" else "assistant", it.text)
                }
                val (reply, engine) = withContext(Dispatchers.IO) {
                    if (FileIndex.entries.isEmpty()) { busyText = "Leggo le cartelle del telefono…"; Storage.scanAll() }
                    busyText = "Jarvis sta pensando…"
                    Brain.ask(ctx, history)
                }
                var photoHits: List<PhotoHit>? = null
                var fileHits: List<FileEntry>? = null
                reply.photoSearch?.let { q ->
                    busyText = "Cerco nelle foto…"
                    photoHits = withContext(Dispatchers.IO) { PhotoSearch.search(ctx, q).second }
                }
                reply.fileQuery?.let { q -> fileHits = withContext(Dispatchers.IO) { Brain.searchFiles(q) } }
                messages.add(
                    UiMsg(
                        nextId++, "assistant", reply.reply, reply.questions,
                        plan = reply.plan?.takeIf { !it.isEmpty || it.warnings.isNotEmpty() },
                        photoHits = photoHits, fileHits = fileHits, engine = engine,
                    )
                )
            } catch (e: AiUnavailable) {
                messages.add(UiMsg(nextId++, "assistant", e.message ?: "AI non disponibile.", error = true))
            } catch (e: Throwable) {
                messages.add(UiMsg(nextId++, "assistant", "Qualcosa è andato storto: ${e.message}", error = true))
            } finally {
                busy = false
            }
        }
    }

    fun executePlan(msgId: Long, plan: Plan) {
        val idx = messages.indexOfFirst { it.id == msgId }
        if (idx < 0) return
        messages[idx] = messages[idx].copy(planState = PlanState.RUNNING)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { FileOps.execute(ctx, plan, "Comando a Jarvis") }
            val i = messages.indexOfFirst { it.id == msgId }
            if (i >= 0) messages[i] = messages[i].copy(planState = PlanState.DONE)
            val txt = buildString {
                append("Fatto, signore. ${r.done} operazioni completate")
                if (r.freedBytes > 0) append(", ${Fmt.size(r.freedBytes)} nel cestino")
                append(".")
                if (r.failed > 0) append(" ${r.failed} non riuscite: ${r.errors.take(2).joinToString("; ")}")
            }
            messages.add(UiMsg(nextId++, "assistant", txt))
            refreshStatus()
        }
    }

    fun cancelPlan(msgId: Long) {
        val idx = messages.indexOfFirst { it.id == msgId }
        if (idx >= 0) messages[idx] = messages[idx].copy(planState = PlanState.CANCELLED)
    }

    /** Esegue un piano fuori dalla chat (pulizia, automazioni in attesa). */
    fun runPlan(plan: Plan, source: String, after: () -> Unit = {}) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { FileOps.execute(ctx, plan, source) }
            toast = "${r.done} operazioni completate" + (if (r.freedBytes > 0) " · ${Fmt.size(r.freedBytes)} liberabili dal cestino" else "") +
                (if (r.failed > 0) " · ${r.failed} non riuscite" else "")
            refreshStatus()
            after()
        }
    }

    fun confirmPending(plan: Plan) {
        PendingPlan.clear(ctx)
        runPlan(plan, pending?.first ?: "Automazione")
        pending = null
    }

    fun dismissPending() {
        PendingPlan.clear(ctx)
        pending = null
    }

    fun undo() {
        viewModelScope.launch {
            val (ok, ko) = withContext(Dispatchers.IO) { Undo.undo(ctx) }
            toast = if (ko == 0) "Annullato: $ok operazioni ripristinate" else "Ripristinate $ok, $ko non riuscite"
            refreshStatus()
        }
    }

    fun clearChat() = messages.clear()
}
