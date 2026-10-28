package com.jarvis.files.ai

import com.jarvis.files.core.PhoneFiles
import com.jarvis.files.core.Storage
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** A real directory result, deliberately separate from the file-only media index. */
data class FolderMatches(val scope: String, val recursive: Boolean, val paths: List<String>, val checked: Int, val skipped: Int, val emptyOnly: Boolean = true)

object FolderCommands {
    private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ITALY)
        .replace('’', '\'').replace(Regex("\\s+"), " ").trim().trimEnd('.', '!', '?')

    private val subject = "cartelle (?:vuote|senza (?:alcun |nessun )?file|che non contengono (?:alcun |nessun )?file)"
    private val family = Regex("\\bcartell[ae]\\b.*\\b(?:vuot[ae]|senza file|senza nessun file|non contengono.*file)\\b")
    private val followUps = setOf("si intendo quelle senza file", "si quelle senza file", "intendo quelle senza file",
        "quelle senza file", "si intendo quelle vuote", "quelle vuote", "mostramele", "mostramele tutte",
        "fammele vedere", "fammele vedere tutte")

    /** Lets the VM skip the unrelated, potentially expensive full file scan. */
    fun handles(text: String, previous: FolderMatches? = null): Boolean =
        family.containsMatchIn(norm(text)) || norm(text) in followUps ||
            (previous != null && norm(text) in setOf("mostrameli", "mostrameli tutti", "mostra l'elenco"))

    private data class Query(val scope: String, val recursive: Boolean)
    private val request = Regex("^(?:(?:per favore,? |puoi |potresti )+)?" +
        "(?:(?:trova|trovami|trovare|cerca|cercami|cercare|mostra|mostrami|mostramele|elenca|elencami|fammi vedere|ci sono|quali sono|se ci sono) )?" +
        "(?:(?:tutte|le|delle|eventuali|mie) )*$subject(.*)$")

    private fun parse(text: String): Query? {
        val match = request.matchEntire(norm(text)) ?: return null
        var tail = match.groupValues[1].trim().trimStart(',').trim()
        tail = tail.replace(Regex("(?:,? (?:mostramele|mostrami|elencamele)(?: tutte)?|^mostramele(?: tutte)?)$"), "").trim()
        var recursive = true
        if (tail.endsWith(" senza sottocartelle")) { recursive = false; tail = tail.removeSuffix(" senza sottocartelle") }
        if (tail.endsWith(" comprese le sottocartelle")) tail = tail.removeSuffix(" comprese le sottocartelle")
        if (tail.endsWith(" e nelle sottocartelle")) tail = tail.removeSuffix(" e nelle sottocartelle")
        if (tail in setOf("", "nel telefono", "sul telefono", "del telefono", "nella memoria interna", "nella memoria condivisa")) return Query("", recursive)
        val scoped = Regex("^(solo direttamente |direttamente )?(?:dentro|dentro a|all'interno di|all'interno della|in|nella|nella sola|della)(?: la)?(?: cartella)? (.+)$").matchEntire(tail) ?: return null
        if (scoped.groupValues[1].isNotEmpty()) recursive = false
        val path = scoped.groupValues[2].trim().trim('"', '\'', '“', '”', '«', '»')
        return Query(when (path) { "downloads", "scaricati" -> "Download"; "documenti" -> "Documents"; else -> path }, recursive)
    }

    /** Resolve each component against the filesystem; never infer folders from contained files. */
    fun actualPath(path: String): String {
        val alias = when (norm(path)) { "downloads", "scaricati" -> "Download"; "documenti" -> "Documents"; "fotocamera" -> "DCIM/Camera"; else -> path }
        val clean = Storage.sanitize(alias) ?: throw IllegalArgumentException("Percorso protetto o non valido: $path")
        var parent = Storage.root
        for (part in clean.split('/').filter { it.isNotEmpty() }) {
            val candidates = parent.listFiles()?.filter { norm(it.name) == norm(part) }.orEmpty()
            require(candidates.size == 1) { "Non trovo una cartella univoca per «$path». Indica il percorso completo dalla scheda File." }
            parent = candidates.single()
            val rel = Storage.rel(parent)
            require(PhoneFiles.restriction(rel) == null) { "Cartella protetta o non accessibile: $rel" }
            require(parent.isDirectory) { "Il percorso «$path» non è una cartella." }
        }
        return Storage.rel(parent)
    }

    /** No depth limit, no sampled file context. Hidden entries and zero-byte files count as content. */
    fun scan(scope: String, recursive: Boolean = true, emptyOnly: Boolean = true, name: String? = null, checkActive: () -> Unit = {}): FolderMatches {
        require(PhoneFiles.restriction(scope) == null) { "Cartella protetta o non accessibile" }
        val base = Storage.file(scope)
        require(base.isDirectory) { "Cartella non più disponibile: $scope" }
        val first = base.listFiles() ?: throw IllegalStateException("Non riesco a leggere la cartella «${scope.ifEmpty { "Memoria interna" }}».")
        val stack = ArrayDeque<File>()
        first.filter { it.isDirectory }.forEach { stack.add(it) }
        val matches = ArrayList<String>()
        var checked = 0; var skipped = 0
        while (stack.isNotEmpty()) {
            checkActive()
            val dir = stack.removeLast()
            val rel = Storage.rel(dir)
            if (PhoneFiles.restriction(rel) != null) { skipped++; continue }
            val children = dir.listFiles()
            // A null listing means unreadable, never empty; a vanished directory is not a result.
            if (children == null || !dir.isDirectory) { skipped++; continue }
            checked++
            if ((!emptyOnly || children.isEmpty()) && (name == null || norm(dir.name).contains(norm(name)))) matches.add(rel)
            if (recursive && children.isNotEmpty()) children.filter { it.isDirectory }.forEach { stack.add(it) }
        }
        checkActive()
        return FolderMatches(scope, recursive, matches.sortedWith(compareBy<String> { it.lowercase(Locale.ITALY) }.thenBy { it }), checked, skipped, emptyOnly)
    }

    fun resolve(history: List<ChatMsg>, previous: FolderMatches? = null, checkActive: () -> Unit = {}): BrainReply? {
        val text = history.lastOrNull()?.text ?: return null
        if (!handles(text, previous)) return null
        val n = norm(text)
        if (previous?.emptyOnly == false && n in setOf("mostramele", "mostramele tutte", "fammele vedere", "fammele vedere tutte", "mostra l'elenco")) return null
        var query = parse(text)
        if (n in followUps || (previous != null && n in setOf("mostrameli", "mostrameli tutti", "mostra l'elenco"))) {
            query = previous?.let { Query(it.scope, it.recursive) }
            if (query == null) {
                // Recover the original user request even after a model error in an older chat.
                for (msg in history.dropLast(1).asReversed().filter { it.role == "user" }) {
                    if (norm(msg.text) in followUps) continue
                    query = parse(msg.text)
                    break // Never resurrect an older scope across an unrelated request.
                }
            }
            if (query == null) return BrainReply("In quale cartella devo cercare? Scrivi, per esempio: «Trova le cartelle vuote in Download».")
        }
        if (query == null) return BrainReply("Per cercare cartelle vuote indica il percorso completo, per esempio «Trova le cartelle vuote nella cartella Download». " +
            "Non ho applicato filtri o modifiche che non riesco a verificare. Puoi gestire le cartelle dalla scheda File.")
        val scope = try { actualPath(query.scope) } catch (e: IllegalArgumentException) { return BrainReply(e.message ?: "Percorso non disponibile.") }
        val result = scan(scope, query.recursive, checkActive = checkActive)
        val where = if (scope.isEmpty()) "nella memoria condivisa accessibile" else "in «$scope»"
        val depth = if (query.recursive) ", comprese le sottocartelle" else ", solo al primo livello"
        val summary = if (result.paths.isEmpty()) "Non ho trovato cartelle vuote $where$depth." else
            "Ho trovato ${result.paths.size} cartelle vuote $where$depth. Tocca una cartella per aprirla."
        return BrainReply(summary + " Una cartella è vuota se non contiene file, file nascosti o sottocartelle. " +
            "${result.checked} cartelle controllate." + if (result.skipped > 0) " ${result.skipped} cartelle protette o non leggibili escluse: la verifica non comprende quelle aree." else "",
            folderResults = result)
    }
}
