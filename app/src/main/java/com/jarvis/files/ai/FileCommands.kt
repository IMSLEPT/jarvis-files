package com.jarvis.files.ai

import com.jarvis.files.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Comandi semplici risolti sull'indice completo. Nessuna modifica prima dell'anteprima. */
object FileCommands {
    private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ITALY).replace(Regex("\\s+"), " ").trim().trimEnd('.', '!', '?')

    private fun commandText(s: String): String {
        var n = norm(s).replace(Regex("^(?:(?:per favore,?|puoi|potresti) )+"), "")
        mapOf("cancellare|cancellami|cancelli" to "cancella", "eliminare|eliminami|elimini" to "elimina",
            "rimuovere|rimuovimi|rimuovi" to "rimuovi", "cercare|cercami|cerchi" to "cerca", "trovare|trovami|trovi" to "trova",
            "mostrare|mostrami|mostri" to "mostra", "rinominare|rinominami|rinomini" to "rinomina",
            "spostare|spostami|sposti" to "sposta", "riordinare|riordinami|riordini" to "riordina").forEach { (forms, verb) ->
            n = n.replace(Regex("^(?:mi )?(?:$forms)\\b"), verb)
        }
        return n
    }

    private val types = linkedMapOf(
        "apk" to setOf("apk", "apks", "xapk"), "apks" to setOf("apk", "apks", "xapk"), "pdf" to setOf("pdf"),
        "video" to setOf("mp4", "mkv", "mov", "3gp", "webm", "avi", "m4v"),
        "foto" to setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "dng", "raw"),
        "immagini" to setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "dng", "raw"),
        "audio" to setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "amr"),
        "archivi" to setOf("zip", "rar", "7z", "tar", "gz"),
    )

    fun resolve(text: String, entries: List<FileEntry> = FileIndex.ensure(), now: Long = System.currentTimeMillis()): BrainReply? {
        // Le opzioni dei comandi integrati sono richieste complete, non istruzioni nascoste.
        val request = text.substringAfter("\nScelgo: ", text)
        val n = commandText(request)
        if (n in setOf("cancella", "elimina", "rimuovi", "cancella i file", "elimina i file", "libera spazio")) {
            return BrainReply("Scegli quali file vuoi controllare. Ti mostrerò l'elenco prima di spostarli nel cestino.",
                listOf(FollowUp("Quali file vuoi controllare?", listOf("Cancella gli APK", "Trova i video", "Trova gli archivi"))))
        }
        if (n in setOf("metti in ordine la cartella download", "metti in ordine i download", "riordina download", "organizza download")) {
            val actions = JSONArray()
            entries.filter { it.folder == "Download" }.groupBy { it.category }.forEach { (cat, files) ->
                val dir = "Download/" + when (cat) {
                    FileCategory.APK -> "APK"; FileCategory.OTHER -> "Altri file"; else -> cat.label
                }
                files.forEach { actions.put(JSONObject().put("type", "move").put("from", it.rel).put("to", dir)) }
            }
            val plan = FileOps.buildPlan(actions)
            return BrainReply(if (plan.isEmpty) "Non ci sono file da riordinare direttamente in Download. Le sottocartelle restano intatte."
                else "Ecco il riordino per tipo dei file in Download. Controlla l'anteprima: nessun file è stato ancora spostato.", plan = plan)
        }
        val rename = Regex("""^rinomina (?:il file )?["“](.+?)["”] (?:in|come) ["“](.+?)["”]$""", RegexOption.IGNORE_CASE).matchEntire(request.trim())
        if (rename != null) {
            val source = rename.groupValues[1]
            val matching = entries.filter { it.rel.equals(source, true) || it.name.equals(source, true) }
            if (matching.size != 1) return BrainReply(if (matching.isEmpty()) "Non trovo il file «$source». Nessun file è stato modificato."
                else "Ci sono ${matching.size} file con questo nome. Indica il percorso completo tra virgolette, per esempio: Rinomina \"Download/$source\" in \"Nuovo nome\".")
            val actions = JSONArray().put(JSONObject().put("type", "rename").put("path", matching.single().rel).put("new_name", rename.groupValues[2]))
            val plan = FileOps.buildPlan(actions)
            return BrainReply(if (plan.isEmpty) "Il file ha già questo nome. Nessuna modifica necessaria." else "Controlla il nuovo nome prima di confermare.", plan = plan)
        }
        val verb = Regex("^(cancella|elimina|rimuovi|metti nel cestino|sposta nel cestino|trova|trovami|cerca|cercami|mostra|mostrami|elenca)\\s+(.+)$").matchEntire(n) ?: return null
        val deleting = verb.groupValues[1] in setOf("cancella", "elimina", "rimuovi", "metti nel cestino", "sposta nel cestino")
        var target = verb.groupValues[2].replace(Regex("^(?:(?:tutti|tutte|i|gli|le|il|la|dei|degli|delle|file|miei|mie)\\s+)+"), "")
        val kind = types.keys.firstOrNull { target == it || target.startsWith("$it ") } ?: return null
        target = target.removePrefix(kind).trim()
        var folder: String? = null
        var age: Int? = null
        var nameWords = emptyList<String>()
        if (!deleting && Regex("^(?:di|dei|delle|della|del|con nome|che contengono) ").containsMatchIn(target)) {
            nameWords = target.replace(Regex("^(?:di|dei|delle|della|del|con nome|che contengono) "), "").split(' ').filter { it.isNotBlank() }
            target = ""
        }
        val ageMatch = Regex("(?:piu vecchi di|vecchi di|con piu di|piu vecchie di) (\\d+) giorni").find(target)
        if (ageMatch != null) {
            age = ageMatch.groupValues[1].toIntOrNull() ?: return null
            target = target.removeRange(ageMatch.range).trim()
        }
        if (target.isNotEmpty()) {
            val fm = Regex("^(?:in|da|dalla|della|nella|dentro)(?: la)?(?: cartella)? (.+)$").matchEntire(target) ?: return null
            val raw = fm.groupValues[1].trim().trim('"', '\'')
            folder = when (raw) {
                "download", "downloads", "scaricati" -> "Download"
                "documenti", "documents" -> "Documents"
                "fotocamera" -> "DCIM/Camera"
                else -> entries.map { it.folder }.distinct().firstOrNull { norm(it) == raw }
                    ?: return BrainReply("Non trovo la cartella «$raw» tra quelle accessibili. Controlla il percorso nella scheda File.")
            }
        }
        val exts = types.getValue(kind)
        val matched = entries.filter { e -> e.ext in exts &&
            (folder == null || e.folder == folder || e.folder.startsWith("$folder/")) &&
            (age == null || now - e.modified >= age.toLong() * 86_400_000L) &&
            nameWords.all { norm(e.name).contains(it) } &&
            Storage.sanitize(e.rel) == e.rel && Storage.file(e.rel).isFile }
        val scope = folder?.let { " in $it e nelle sue sottocartelle" } ?: " nella memoria condivisa accessibile"
        val label = kind.uppercase(Locale.ITALY).takeIf { kind in setOf("apk", "pdf") } ?: kind
        if (matched.isEmpty()) return BrainReply("Non ho trovato file $label$scope" +
            (age?.let { " più vecchi di $it giorni" } ?: "") + ". Nessun file è stato modificato.")
        if (!deleting) return BrainReply("Ho trovato ${matched.size} file $label$scope.",
            fileResults = matched.sortedByDescending { it.modified })
        val plan = Plan(matched.sortedBy { it.rel }.map { Op.Trash(it.rel, it.size) }, emptyList())
        return BrainReply("Ho trovato ${matched.size} file $label$scope (${Fmt.size(matched.sumOf { it.size })}). " +
            "Controlla l'elenco e scegli cosa spostare nel cestino." +
            if (kind == "apk") " Le app installate restano sul telefono." else "", plan = plan)
    }

    fun isMutation(text: String) = Regex("^(?:per favore |puoi |potresti )?(?:(?:cancella|elimina|rimuovi|rinomina|sposta|riordina|organizza|pulisci)\\b|metti (?:in ordine|nel cestino)|crea (?:una |la )?cartella)")
        .containsMatchIn(commandText(text))
}
