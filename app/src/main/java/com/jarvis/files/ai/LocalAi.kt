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
import com.jarvis.files.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Modelli offline proposti (formato .litertlm da Hugging Face, LiteRT Community). */
data class LocalModelOption(val repo: String, val title: String, val note: String)

val LOCAL_MODELS = listOf(
    LocalModelOption(
        "litert-community/gemma-4-E2B-it-litert-lm", "Gemma 4 E2B (consigliato)",
        "Circa 2-3 GB. Veloce, capisce testo e immagini."
    ),
    LocalModelOption(
        "litert-community/gemma-4-E4B-it-litert-lm", "Gemma 4 E4B (più intelligente)",
        "Circa 4-5 GB. Più preciso ma più lento; adatto a telefoni con molta RAM."
    ),
)

/**
 * AI dentro il telefono con LiteRT-LM. Un solo Engine caricato alla volta (occupa diversi GB di RAM).
 */
object LocalAi {
    private const val TAG = "LocalAi"
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedPath: String? = null
    var visionAvailable = false
        private set

    fun engine(c: Context): AiEngine = Wrapper(c.applicationContext)

    private class Wrapper(val c: Context) : AiEngine {
        override val displayName: String
            get() = "Telefono · " + (File(Prefs(c).localModelPath).nameWithoutExtension.take(28))

        override suspend fun complete(system: String, messages: List<ChatMsg>, maxTokens: Int): String =
            withContext(Dispatchers.Default) {
                mutex.withLock {
                    val e = ensureLoaded(c)
                    val history = messages.dropLast(1).takeLast(6).map {
                        if (it.role == "user") Message.user(it.text) else Message.model(it.text)
                    }
                    val cfg = ConversationConfig(
                        systemInstruction = Contents.of(system),
                        initialMessages = history,
                        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
                    )
                    e.createConversation(cfg).use { conv ->
                        conv.sendMessage(messages.last().text).toString()
                    }
                }
            }
    }

    private fun ensureLoaded(c: Context): Engine {
        val p = Prefs(c)
        val path = p.localModelPath
        engine?.let { if (loadedPath == path) return it }
        engine?.close()
        engine = null
        val backend = if (p.localUseGpu) Backend.GPU() else Backend.CPU()
        val cache = c.cacheDir.path
        // Prima prova con la visione (Gemma 4 legge le immagini), poi solo testo, poi CPU.
        val attempts = listOf(
            { EngineConfig(modelPath = path, backend = backend, visionBackend = Backend.GPU(), cacheDir = cache) to true },
            { EngineConfig(modelPath = path, backend = backend, cacheDir = cache) to false },
            { EngineConfig(modelPath = path, backend = Backend.CPU(), cacheDir = cache) to false },
        )
        var last: Throwable? = null
        for (a in attempts) {
            try {
                val (cfg, vision) = a()
                val e = Engine(cfg)
                e.initialize()
                engine = e
                loadedPath = path
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
        mutex.withLock {
            val e = ensureLoaded(c)
            if (!visionAvailable) return@withLock null
            runCatching {
                e.createConversation(
                    ConversationConfig(samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.1))
                ).use { conv ->
                    conv.sendMessage(Contents.of(Content.ImageFile(file.absolutePath), Content.Text(prompt))).toString()
                }
            }.onFailure { Log.w(TAG, "Visione fallita", it) }.getOrNull()
        }
    }

    fun release() {
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
    }

    /** Avvia il download con il DownloadManager di sistema (riprende da solo, notifica di avanzamento). */
    fun startDownload(c: Context, repo: String, fileName: String): Long {
        val dm = c.getSystemService(DownloadManager::class.java)
        val url = "https://huggingface.co/$repo/resolve/main/$fileName?download=true"
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle("Jarvis · cervello offline")
            .setDescription(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(c, "models", fileName)
            .setAllowedOverMetered(true)
        val id = dm.enqueue(req)
        val p = Prefs(c)
        p.localDownloadId = id
        p.localModelRepo = repo
        p.localModelPath = File(c.getExternalFilesDir("models"), fileName).absolutePath
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
        release()
        val p = Prefs(c)
        runCatching { File(p.localModelPath).delete() }
        p.localModelPath = ""
        p.localDownloadId = -1
    }

    /** Info leggibile sul modello: usata dalle Impostazioni. */
    fun info(c: Context): JSONObject {
        val p = Prefs(c)
        val f = File(p.localModelPath)
        return JSONObject().put("path", p.localModelPath).put("exists", f.exists()).put("size", if (f.exists()) f.length() else 0)
    }
}
