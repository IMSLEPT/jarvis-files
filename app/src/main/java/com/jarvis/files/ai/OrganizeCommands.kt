package com.jarvis.files.ai

import com.jarvis.files.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Scelte di riordino gestite dall'app: non dipendono dalla memoria del modello. */
data class OrganizeRequest(val scope: String?, val mode: String? = null, val kind: String = "tutti", val recursive: Boolean = false)

object OrganizeCommands {
    private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ITALY).replace('’', '\'').replace(Regex("\\s+"), " ").trim().trimEnd('.', '!', '?')
    private val start = Regex("^(?:(?:puoi|potresti|per favore) )?(?:riordina(?:re|mi)?|organizza(?:re|mi)?|metti in ordine)\\b")
    private fun begin(raw: String, context: ConversationContext?): OrganizeRequest? {
        val n = norm(raw).replace(Regex("\\bfoto\\b"), "immagini")
        if (!start.containsMatchIn(n)) return null
        // Filtri o nomi specifici non riconosciuti non autorizzano mai a spostare altri file.
        if (Regex("\\b(?:tranne|eccetto|esclus[ioe]|soltanto|vecchi|vecchie|recenti|grandi|piccoli|prima|dopo|chiamat[ioae]|screenshot|schermate|excel|word|zip|jpeg|jpg|png|mp4|mp3)\\b").containsMatchIn(n) ||
            Regex("\\b[\\w-]+\\.[a-z0-9]{1,8}\\b").containsMatchIn(n)) return null
        if (listOf("pdf", "immagini", "video", "audio", "apk", "archivi", "documenti").count { Regex("\\b$it\\b").containsMatchIn(n) } > 1) return null
        val quoted = Regex("['\"“]([^'\"”]+)['\"”]").find(raw)?.groupValues?.get(1)
        val explicit = Regex("(?:nella|della|la|in|dentro|interno(?: della| di)?) (?:cartella )?([\\w/-]+)$").find(n)?.groupValues?.get(1)
        val candidate = when {
            quoted != null -> quoted
            explicit != null && '/' in explicit -> explicit
            Regex("\\bdownloads?\\b").containsMatchIn(n) -> "Download"
            explicit != null && explicit !in setOf("file", "tipo", "nome", "data", "bene", "tutti") -> explicit
            else -> context?.folders?.singleOrNull() ?: context?.scope
        }
        val scope = candidate?.let { runCatching { FolderCommands.actualPath(it) }.getOrNull() }
        val mode = when { "per tipo" in n -> "tipo"; "per data" in n -> "data"; "per nome" in n -> "nome"; else -> null }
        val kind = listOf("pdf", "immagini", "video", "audio", "apk", "archivi", "documenti").firstOrNull { Regex("\\b$it\\b").containsMatchIn(n) } ?: "tutti"
        return OrganizeRequest(scope, mode, kind, Regex("sottocartell|tutte le cartelle|cartelle e file").containsMatchIn(n))
    }

    fun resolve(history: List<ChatMsg>, context: ConversationContext?, choice: FollowUpChoice?, entries: List<FileEntry>): BrainReply? {
        val raw = history.lastOrNull()?.text ?: return null
        val n = norm(raw.substringAfter("\nScelgo: ", raw))
        val fresh = begin(raw, context)
        if (start.containsMatchIn(norm(raw)) && fresh == null) return null
        var state = fresh ?: context?.organize
        // Recupera anche i pulsanti di una conversazione precedente all'aggiornamento.
        if (state == null && choice != null) {
            state = choice.request?.let { begin(it, context) }
                ?: history.dropLast(1).asReversed().firstOrNull { it.role == "user" && start.containsMatchIn(norm(it.text)) }?.let { begin(it.text, context) }
        }
        state ?: return null
        if (fresh == null && n in setOf("annulla", "no", "lascia perdere"))
            return BrainReply("Riordino annullato. Nessun file è stato modificato.", conversation = ConversationContext(scope = state.scope))
        if (fresh == null) {
            val mode = when { "per tipo" in n -> "tipo"; "per data" in n -> "data"; "per nome" in n -> "nome"; else -> null }
            val kind = when {
                n in setOf("tutti", "tutte", "tutti i file") -> "tutti"
                n in setOf("pdf", "immagini", "video", "audio", "apk", "archivi", "documenti", "altri") -> n
                else -> null
            }
            val scope = if (state.scope == null) runCatching { FolderCommands.actualPath(raw.trim().trim('"', '\'')) }.getOrNull() else null
            val recognized = mode != null || kind != null || scope != null || "mantenere la struttura" in n || "cartelle specifiche" in n
            if (!recognized) return null // Una nuova richiesta non viene assorbita dal riordino.
            state = state.copy(scope = scope ?: state.scope, mode = mode ?: state.mode, kind = kind ?: state.kind)
        }
        val request = state ?: return null
        val ctx = ConversationContext(scope = request.scope, organize = request)
        if (request.scope == null) return BrainReply("In quale cartella vuoi riordinare i file?", listOf(FollowUp("Scegli la cartella", listOf("Download", "Documents"))), conversation = ctx)
        val scope = request.scope
        if (scope.isBlank() || PhoneFiles.restriction(scope) != null || Storage.sanitize(scope) != scope || !Storage.file(scope).isDirectory)
            return BrainReply("La cartella non è disponibile o è protetta. Scegli un percorso accessibile nella scheda File.")
        if (request.mode == null) return BrainReply("Riordino in «$scope» tutti i file selezionati, mantenendo le sottocartelle. Come vuoi raggrupparli?",
            listOf(FollowUp("Come preferisci riordinare i file?", listOf("Per tipo di file", "Per data di modifica", "Per nome"))), conversation = ctx)
        if (request.mode == "nome") return BrainReply("Per vederli in ordine alfabetico apri «$scope» nella scheda File e scegli Nome. Non occorre spostare o rinominare i file.", conversation = ConversationContext(scope = scope))
        val actions = JSONArray()
        val currentEntries = Storage.scanAll()
        currentEntries.filter { e -> (e.folder == scope || request.recursive && e.folder.startsWith("$scope/")) && when (request.kind) {
            "pdf" -> e.ext == "pdf"; "immagini" -> e.category == FileCategory.IMAGE; "video" -> e.category == FileCategory.VIDEO
            "audio" -> e.category == FileCategory.AUDIO; "apk" -> e.category == FileCategory.APK; "archivi" -> e.category == FileCategory.ARCHIVE
            "documenti" -> e.category == FileCategory.DOCUMENT
            "altri" -> e.category == FileCategory.OTHER; else -> true
        } }.forEach { e ->
            val group = if (request.mode == "data") SimpleDateFormat("yyyy-MM", Locale.ROOT).format(Date(e.modified)) else when {
                e.ext == "pdf" -> "PDF"; e.category == FileCategory.APK -> "APK"; e.category == FileCategory.OTHER -> "Altri file"; else -> e.category.label
            }
            // Non annidare ripetutamente PDF/PDF e non appiattire cartelle preesistenti.
            if (!e.folder.substringAfterLast('/').equals(group, true)) actions.put(JSONObject().put("type", "move").put("from", e.rel).put("to", "${e.folder}/$group"))
        }
        val plan = FileOps.buildPlan(actions)
        return BrainReply(if (plan.isEmpty) "Nessun file da riordinare in «$scope» con queste scelte. Le sottocartelle restano intatte."
            else "Anteprima: ${plan.moves} file in «$scope», raggruppati per ${if (request.mode == "data") "mese di modifica" else "tipo"}. Le cartelle esistenti sono mantenute. Premi Esegui per applicare il riordino.",
            plan = plan.takeUnless { it.isEmpty }, conversation = ConversationContext(scope = scope))
    }
}
