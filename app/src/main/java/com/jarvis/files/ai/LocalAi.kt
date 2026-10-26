package com.jarvis.files.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.Conversation
import com.jarvis.files.core.Prefs
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Modelli offline proposti (formato .litertlm da Hugging Face, LiteRT Community). */
data class LocalModelOption(val repo: String, val title: String, val note: String)

val LOCAL_MODELS = listOf(
    LocalModelOption(
        "litert-community/gemma-4-E4B-it-litert-lm", "Gemma 4 E4B · qualità consigliata",
        "Circa 3,7 GB da scaricare. Apache 2.0, offline. Più capacità, maggior uso di RAM e batteria."
    ),
    LocalModelOption(
        "litert-community/gemma-4-E2B-it-litert-lm", "Gemma 4 E2B · più veloce",
        "Circa 2,6 GB da scaricare. Apache 2.0, offline. Per risposte più rapide e minori consumi."
    ),
)

/**
 * AI dentro il telefono con LiteRT-LM. Un solo Engine caricato alla volta (occupa diversi GB di RAM).
 */
object LocalAi {
    private const val TAG = "LocalAi"
    private val mutex = Mutex()
    private var engine: Engine? = null
    @Volatile private var activeConversation: Conversation? = null
    private var loadedWithVision = false
    private var loadedGpu = false
    private var loadedPath: String? = null
    var visionAvailable = false
        private set
    /** Se la visione su GPU fallisce (driver non compatibile) si passa al processore. */
    private var visionOnCpu = false

    fun engine(c: Context): AiEngine = Wrapper(c.applicationContext)

    // Il modello occupa GB di RAM e tiene sveglia la GPU: si chiude da solo dopo 2 minuti di inattività
    private val idleScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
    private var idleJob: kotlinx.coroutines.Job? = null

    private fun scheduleIdleRelease() {
        idleJob?.cancel()
        idleJob = idleScope.launch {
            kotlinx.coroutines.delay(120_000)
            mutex.withLock { releaseLocked() }
        }
    }

    private class Wrapper(val c: Context) : AiEngine {
        override val displayName: String
            get() = "Telefono · " + (File(Prefs(c).localModelPath).nameWithoutExtension.take(28))

        override suspend fun complete(system: String, messages: List<ChatMsg>, maxTokens: Int): String =
            withContext(Dispatchers.Default) {
                idleJob?.cancel()
                mutex.withLock {
                    val e = ensureLoaded(c, withVision = false)
                    val history = messages.dropLast(1).takeLast(6).map {
                        if (it.role == "user") Message.user(it.text) else Message.model(it.text)
                    }
                    val cfg = ConversationConfig(
                        systemInstruction = Contents.of(system),
                        initialMessages = history,
                        samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.1),
                        maxOutputToken = maxTokens,
                        thinkingConfig = ThinkingConfig(enableThinking = false),
                        automaticToolCalling = false,
                        enableResponseFormat = system.contains("JSON", ignoreCase = true),
                    )
                    e.createConversation(cfg).use { conv ->
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        activeConversation = conv
                        try {
                            conv.sendMessage(messages.last().text,
                                responseFormat = if (system.contains("JSON", ignoreCase = true)) ResponseFormat.json("""{"type":"object"}""") else null,
                            ).toString()
                        } finally { activeConversation = null }
                    }
                }.also { scheduleIdleRelease() }
            }
    }

    private fun ensureLoaded(c: Context, withVision: Boolean): Engine {
        val p = Prefs(c)
        val path = p.localModelPath
        engine?.let { if (loadedPath == path && loadedWithVision == withVision && loadedGpu == p.localUseGpu) return it }
        engine?.close()
        engine = null
        val backend = if (p.localUseGpu) Backend.GPU() else Backend.CPU()
        val cache = c.cacheDir.path
        // Prima prova con la visione (Gemma 4 legge le immagini), poi solo testo, poi CPU.
        val attempts = (if (withVision) listOf(
            { EngineConfig(modelPath = path, backend = backend, visionBackend = if (visionOnCpu) Backend.CPU() else Backend.GPU(), cacheDir = cache, maxNumTokens = 8192) to true }
        ) else emptyList()) + listOf(
            { EngineConfig(modelPath = path, backend = backend, cacheDir = cache, maxNumTokens = 8192) to false },
            { EngineConfig(modelPath = path, backend = Backend.CPU(), cacheDir = cache, maxNumTokens = 8192) to false },
        )
        var last: Throwable? = null
        for (a in attempts) {
            try {
                val (cfg, vision) = a()
                val e = Engine(cfg)
                e.initialize()
                engine = e
                loadedPath = path
                loadedWithVision = withVision
                loadedGpu = p.localUseGpu
                visionAvailable = vision
                return e
            } catch (t: Throwable) {
                Log.w(TAG, "Caricamento fallito", t)
                last = t
            }
        }
        throw AiUnavailable("Impossibile avviare l'AI nel telefono: ${last?.message}")
    }

    /** Descrive un'immagine (solo se il modello supporta la visione). */
    suspend fun describeImage(c: Context, file: File, prompt: String): String? = withContext(Dispatchers.Default) {
        idleJob?.cancel()
        mutex.withLock {
            for (attempt in 0..1) {
                val e = ensureLoaded(c, withVision = true)
                if (!visionAvailable) return@withLock null
                val res = runCatching {
                    e.createConversation(
                        ConversationConfig(samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.1))
                    ).use { conv ->
                        conv.sendMessage(Contents.of(Content.ImageFile(file.absolutePath), Content.Text(prompt))).toString()
                    }
                }.onFailure { Log.w(TAG, "Visione fallita (cpu=$visionOnCpu)", it) }
                if (res.isSuccess) return@withLock res.getOrNull()
                if (visionOnCpu) return@withLock null
                // Ripiego: ricarica il modello con la visione sul processore e riprova una volta
                visionOnCpu = true
                releaseLocked()
            }
            null
        }.also { scheduleIdleRelease() }
    }

    fun cancelGeneration() { runCatching { activeConversation?.cancelProcess() } }

    fun release() {
        idleScope.launch { mutex.withLock { releaseLocked() } }
    }

    private fun releaseLocked() {
        engine?.close()
        engine = null
        loadedPath = null
    }

    // ---------------- Download del modello ----------------

    /** Trova il file .litertlm adatto nel repository Hugging Face. */
    suspend fun findModelFile(repo: String): Pair<String, Long> = withContext(Dispatchers.IO) {
        val c = (URL("https://huggingface.co/api/models/$repo/tree/main").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
        }
        try {
        if (c.responseCode !in 200..299) throw AiUnavailable("Modello non raggiungibile (${c.responseCode}).")
        val arr = org.json.JSONArray(c.inputStream.bufferedReader().use { it.readText() })
        val files = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("type") == "file" && it.optString("path").endsWith(".litertlm") }
            .map { it.getString("path") to it.optLong("size") }
        if (files.isEmpty()) throw AiUnavailable("Nessun file .litertlm nel repository $repo.")
        // Escludi varianti per chip specifici (NPU Qualcomm/MediaTek) e web.
        val generic = files.filterNot { (n, _) ->
            Regex("(?i)qualcomm|mediatek|mt6\\d|sm8\\d|npu|web|tensor_g").containsMatchIn(n)
        }
        (generic.ifEmpty { files }).minBy { it.first.length }
        } finally { c.disconnect() }
    }

    /** Avvia il download con il DownloadManager di sistema (riprende da solo, notifica di avanzamento). */
    fun startDownload(c: Context, repo: String, fileName: String): Long {
        val dm = c.getSystemService(DownloadManager::class.java)
        val url = "https://huggingface.co/$repo/resolve/main/$fileName?download=true"
        val p = Prefs(c)
        if (p.localDownloadId >= 0) dm.remove(p.localDownloadId)
        val localName = repo.substringAfterLast('/') + "_" + fileName.substringAfterLast('/')
        val target = File(c.getExternalFilesDir("models"), localName)
        if (target.exists() && target.absolutePath != loadedPath) target.delete()
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle("Jarvis · cervello offline")
            .setDescription(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(c, "models", localName)
            .setAllowedOverMetered(true)
        val id = dm.enqueue(req)
        p.localDownloadId = id
        p.localModelRepo = repo
        p.localModelPath = target.absolutePath
        release()
        return id
    }

    data class DlState(val running: Boolean, val done: Long, val total: Long, val failed: Boolean)

    fun downloadState(c: Context): DlState? {
        val id = Prefs(c).localDownloadId
        if (id < 0) return null
        val dm = c.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id)).use { cur ->
            if (!cur.moveToFirst()) return null
            val status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return DlState(
                running = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING ||
                    status == DownloadManager.STATUS_PAUSED,
                done = done, total = total, failed = status == DownloadManager.STATUS_FAILED,
            )
        }
    }

    fun deleteModel(c: Context) {
        val p = Prefs(c)
        val oldPath = p.localModelPath
        val oldId = p.localDownloadId
        p.localModelPath = ""
        p.localDownloadId = -1
        idleScope.launch {
            mutex.withLock {
                releaseLocked()
                if (oldId >= 0) c.getSystemService(DownloadManager::class.java).remove(oldId)
                runCatching { File(oldPath).takeIf { oldPath.isNotBlank() }?.delete() }
            }
        }
    }

    /** Info leggibile sul modello: usata dalle Impostazioni. */
    fun info(c: Context): JSONObject {
        val p = Prefs(c)
        val f = File(p.localModelPath)
        return JSONObject().put("path", p.localModelPath).put("exists", f.exists()).put("size", if (f.exists()) f.length() else 0)
    }
}

