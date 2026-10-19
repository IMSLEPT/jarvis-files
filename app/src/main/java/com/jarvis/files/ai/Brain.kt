package com.jarvis.files.ai

import android.content.Context
import com.jarvis.files.core.DeviceInfo
import com.jarvis.files.core.FileEntry
import com.jarvis.files.core.FileIndex
import com.jarvis.files.core.FileOps
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.Plan
import com.jarvis.files.core.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class FollowUp(val question: String, val options: List<String>)

data class FileQuery(val keywords: List<String>, val ext: List<String>, val folder: String?, val fromIso: String?, val toIso: String?)

/** Risposta strutturata di Jarvis. */
data class BrainReply(
    val reply: String,
    val questions: List<FollowUp> = emptyList(),
    val plan: Plan? = null,
    val photoSearch: String? = null,
    val fileQuery: FileQuery? = null,
)

/** Costruisce il contesto per l'AI e interpreta la risposta. */
object Brain {

    private fun systemPrompt(name: String) = """
Sei JARVIS, l'assistente del telefono Android di $name. Stile: quello di Jarvis di Iron Man, elegante e leggermente ironico, ma CONCISO (1-3 frasi). Ogni tanto chiamalo "signore". Rispondi sempre in italiano.

Sai fare: riordinare file e cartelle, rinominare, creare cartelle, cancellare (va nel cestino per 30 giorni), cercare file e foto, rispondere a domande sul telefono, diagnosticare problemi (lentezza, batteria, spazio, errori, app che si bloccano) dando passi pratici per Android/Pixel.

RISPONDI SOLO con un oggetto JSON valido, senza testo fuori:
{
 "reply": "messaggio breve per l'utente",
 "questions": [ {"q": "domanda", "options": ["opzione 1", "opzione 2"]} ],
 "actions": [ ... ],
 "photo_search": "testo",
 "file_search": {"keywords": ["..."], "ext": ["pdf"], "folder": "Download", "from": "2026-01-01", "to": "2026-12-31"}
}
Includi SOLO i campi che servono davvero: niente "photo_search" o "file_search" se l'utente non chiede di cercare; niente date se l'utente non le dice.
Se fai domande in "questions", lascia "actions" vuoto: agirai dopo la risposta.

Tipi di "actions" (percorsi RELATIVI alla memoria, es. "Download/file.pdf"):
- {"type":"mkdir","path":"Documenti/Bollette"}
- {"type":"move","from":"Download/a.pdf","to":"Documenti/Bollette","new_name":"(facoltativo)"}
- {"type":"rename","path":"Download/IMG_1.jpg","new_name":"Nuovo nome.jpg"}
- {"type":"delete","path":"Download/vecchio.apk"}
- {"type":"move_where","where":{FILTRO},"to":"cartella"}
- {"type":"delete_where","where":{FILTRO}}
- {"type":"rename_where","where":{FILTRO},"pattern":"{data} {nome}"}  segnaposto: {data}=AAAA-MM-GG {anno} {mese} {nome} {n} {ext}
FILTRO: {"folder":"Download","recursive":false,"ext":["pdf"],"name_contains":["fattura"],"older_than_days":30,"newer_than_days":7,"min_size_mb":50}

Regole:
- Usa SOLO percorsi presenti nell'elenco fornito. Per molti file preferisci le azioni *_where.
- Riordina DENTRO la cartella indicata dall'utente, rispettando le sottocartelle che esistono già (se c'è già "Download/PDF" usala, non crearne una doppia).
- Se la richiesta è ambigua (che nomi usare, dove mettere le cartelle, cosa cancellare) NON agire: fai 1-2 domande in "questions" con opzioni pronte da toccare, ad esempio stili di nome: "2026-10-19 Bolletta Enel.pdf" / "Bolletta Enel 19-10-2026.pdf".
- Per nomi nuovi: chiari, in italiano, senza caratteri strani, mantieni l'estensione.
- L'utente vedrà un'anteprima e confermerà: descrivi in "reply" cosa farai.
- Se chiede foto (persone, luoghi, oggetti, "foto di Leo", "montagne", "lago") usa "photo_search" con la richiesta completa.
- Se chiede di trovare documenti/file usa "file_search".
- Per domande sul telefono rispondi in "reply" con passi numerati brevi; nessuna action.
- Non inventare file o dati che non vedi.
""".trimIndent()

    /** Riassunto cartelle + elenco file pertinenti, entro un budget di righe. */
    fun buildContext(c: Context, userText: String, fileBudget: Int): String {
        val entries = FileIndex.ensure()
        val sb = StringBuilder()
        sb.appendLine("=== STATO TELEFONO ===")
        sb.append(DeviceInfo.snapshot(c))
        sb.appendLine("=== CARTELLE (file, peso) ===")
        val byFolder = entries.groupBy { it.folder.split('/').take(2).joinToString("/") }
        byFolder.entries.sortedByDescending { it.value.size }.take(if (fileBudget > 200) 70 else 18).forEach { (f, list) ->
            sb.appendLine("${f.ifEmpty { "(radice)" }} | ${list.size} | ${Fmt.size(list.sumOf { it.size })}")
        }

        // Cartelle citate nel messaggio (es. "download", "whatsapp", "documenti")
        val text = userText.lowercase(Locale.ITALY)
        val folders = entries.asSequence().map { it.folder }.distinct().toList()
        val mentioned = folders.filter { f ->
            val last = f.substringAfterLast('/').lowercase(Locale.ITALY)
            last.length >= 3 && text.contains(last)
        }.ifEmpty {
            if (Regex("download|scaric").containsMatchIn(text)) folders.filter { it.startsWith("Download") } else emptyList()
        }
        val target = mentioned.ifEmpty { listOf("Download", "") }
        val relevant = entries.filter { e -> target.any { t -> e.folder == t || (t.isNotEmpty() && e.folder.startsWith("$t/")) } }
            .sortedByDescending { it.modified }

        sb.appendLine("=== FILE (percorso | KB | data modifica) — ${relevant.size} totali, mostrati ${minOf(relevant.size, fileBudget)} ===")
        relevant.take(fileBudget).forEach { e: FileEntry ->
            sb.appendLine("${e.rel} | ${e.size / 1024} | ${Fmt.iso(e.modified)}")
        }
        if (relevant.size > fileBudget) sb.appendLine("… altri ${relevant.size - fileBudget} file non mostrati: usa azioni *_where per coprirli tutti.")
        val subdirs = folders.filter { f -> target.any { t -> t.isNotEmpty() && f.startsWith("$t/") } }.take(60)
        if (subdirs.isNotEmpty()) sb.appendLine("Sottocartelle esistenti: ${subdirs.joinToString(", ")}")
        return sb.toString()
    }

    suspend fun ask(c: Context, history: List<ChatMsg>): Pair<BrainReply, String> {
        val engine = AiRouter.engine(c)
        val last = history.last()
        val ctx = buildContext(c, last.text, AiRouter.fileBudget(engine))
        val msgs = history.dropLast(1).takeLast(if (engine is OnlineAi) 8 else 4) + ChatMsg("user", "$ctx\n=== RICHIESTA ===\n${last.text}")
        val raw = engine.complete(systemPrompt(Prefs(c).userName), msgs)
        return clean(parse(raw), last.text) to engine.displayName
    }

    private val photoWords = Regex("(?i)foto|immagin|selfie|scatt|video|ritratt|mostra")
    private val searchWords = Regex("(?i)trova|cerca|dov'?[èe]|dove|mostra|elenca|quali")
    private val dateWords = Regex("(?i)\\b(19|20)\\d{2}\\b|gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre|ieri|oggi|settimana|mese|anno")

    /** Ripulisce la risposta: ricerche non richieste, date inventate, piano proposto insieme a domande. */
    fun clean(r: BrainReply, userText: String): BrainReply {
        val photo = r.photoSearch?.takeIf { photoWords.containsMatchIn(userText) && it.length >= 3 && !Regex("(?i)^(n/?a|null|nessun)").containsMatchIn(it) }
        val fq = r.fileQuery?.takeIf { searchWords.containsMatchIn(userText) && r.plan == null }
            ?.let { q -> if (dateWords.containsMatchIn(userText)) q else q.copy(fromIso = null, toIso = null) }
            ?.let { q -> if (q.folder != null && com.jarvis.files.core.Storage.file(q.folder).isDirectory) q else q.copy(folder = null) }
        // Se Jarvis fa domande, il piano aspetta la risposta (si evita di eseguire scelte non confermate)
        val plan = if (r.questions.isNotEmpty()) null else r.plan
        return r.copy(photoSearch = photo, fileQuery = fq, plan = plan)
    }

    fun parse(raw: String): BrainReply {
        val o = extractJson(raw) ?: return BrainReply(reply = raw.trim().ifEmpty { "Non ho capito, signore. Può riformulare?" })
        val qs = o.optJSONArray("questions")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val q = a.optJSONObject(i) ?: return@mapNotNull null
                val opts = q.optJSONArray("options")?.let { oa -> (0 until oa.length()).map { oa.optString(it) } } ?: emptyList()
                FollowUp(q.optString("q"), opts.filter { it.isNotBlank() }).takeIf { it.question.isNotBlank() }
            }
        } ?: emptyList()
        val actions: JSONArray? = o.optJSONArray("actions")
        val plan = if (actions != null && actions.length() > 0) FileOps.buildPlan(actions) else null
        val fs = o.optJSONObject("file_search")?.let { f -> parseFileQuery(f) }
        return BrainReply(
            reply = o.optString("reply").ifBlank { if (plan != null) "Ecco cosa propongo." else "Fatto." },
            questions = qs,
            plan = plan,
            photoSearch = o.optString("photo_search").takeIf { it.isNotBlank() },
            fileQuery = fs,
        )
    }

    fun parseFileQuery(f: JSONObject): FileQuery {
        fun list(k: String) = f.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } }?.filter { it.isNotBlank() } ?: emptyList()
        return FileQuery(
            keywords = list("keywords"),
            ext = list("ext").map { it.lowercase(Locale.ROOT).trimStart('.') },
            folder = f.optString("folder").takeIf { it.isNotBlank() },
            fromIso = f.optString("from").takeIf { it.length >= 10 },
            toIso = f.optString("to").takeIf { it.length >= 10 },
        )
    }

    /** Ricerca file nell'indice (nome, cartella, data). Funziona anche senza AI. */
    fun searchFiles(q: FileQuery, limit: Int = 200): List<FileEntry> {
        val entries = FileIndex.ensure()
        val kws = q.keywords.map { it.lowercase(Locale.ITALY) }.filter { it.length >= 2 }
        return entries.asSequence()
            .filter { q.ext.isEmpty() || it.ext in q.ext }
            .filter { q.folder == null || it.folder.startsWith(q.folder.trim('/'), ignoreCase = true) }
            .filter { q.fromIso == null || Fmt.iso(it.modified) >= q.fromIso }
            .filter { q.toIso == null || Fmt.iso(it.modified) <= q.toIso }
            .map { e ->
                val hay = e.rel.lowercase(Locale.ITALY).replace('_', ' ').replace('-', ' ')
                e to kws.count { hay.contains(it) }
            }
            .filter { kws.isEmpty() || it.second > 0 }
            .sortedWith(compareByDescending<Pair<FileEntry, Int>> { it.second }.thenByDescending { it.first.modified })
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** Ricerca semplice da testo libero (senza AI). */
    fun simpleQuery(text: String): FileQuery {
        val stop = setOf("trova", "trovami", "cerca", "il", "lo", "la", "i", "gli", "le", "di", "del", "della", "dei", "un", "una", "file", "mio", "mia", "miei", "mie", "documento", "documenti")
        val words = text.lowercase(Locale.ITALY).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 && it !in stop }
        val exts = words.filter { it in setOf("pdf", "doc", "docx", "xls", "xlsx", "jpg", "png", "mp4", "mp3", "zip", "apk") }
        return FileQuery(words - exts.toSet(), exts, null, null, null)
    }
}
