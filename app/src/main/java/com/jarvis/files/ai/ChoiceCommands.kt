package com.jarvis.files.ai

import com.jarvis.files.core.FileEntry
import java.text.Normalizer
import java.util.Locale

/** Fonte associata dall'app alla domanda: non viene letta dal JSON del modello. */
data class FollowUpChoice(val request: String?, val option: String, val options: List<String>)

object ChoiceCommands {
    private fun norm(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ITALY).trim().trimEnd('.', '!', '?')

    fun optionFor(input: String, options: List<String>) = options.firstOrNull { norm(it) == norm(input) }
    fun isBinary(options: List<String>) = options.size == 2 && options.map(::norm).toSet() == setOf("si", "no")
    fun declines(choice: FollowUpChoice?) = choice != null && isBinary(choice.options) && norm(choice.option) == "no"

    fun resolve(choice: FollowUpChoice?, entries: List<FileEntry>): BrainReply? {
        if (choice == null || !isBinary(choice.options) || choice.option !in choice.options) return null
        if (declines(choice)) return BrainReply("Operazione annullata. Nessun file è stato modificato.")
        if (norm(choice.option) != "si") return null
        // La domanda del modello non autorizza nuovi file o filtri: usa solo la richiesta originale.
        choice.request?.let { request -> FileCommands.resolve(request, entries)?.let { return it } }
        if (choice.request != null && !FileCommands.isMutation(choice.request)) return null
        return BrainReply("Non ho un elenco preciso da confermare. Indica il tipo di file e la cartella, per esempio «Cancella gli screenshot in Pictures/Screenshots». Nessun file è stato modificato.")
    }
}
