package com.jarvis.files.ai

import com.jarvis.files.core.FileEntry
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.Storage
import java.text.Normalizer
import java.util.Locale

/** Domande verificabili sui file: nessuna inferenza del modello, nessuna modifica. */
object FileFacts {
    private fun norm(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ITALY)
        .replace(Regex("\\s+"), " ").trim().trimEnd('.', '!', '?')

    fun isShowFollowUp(text: String) = norm(text) in setOf(
        "mostrameli", "mostrameli tutti", "fammeli vedere", "fammi vedere", "vediamoli", "mostra l'elenco", "mostra i file"
    )

    fun resolve(text: String, entries: List<FileEntry>): BrainReply? {
        val n = norm(text).replace(Regex("^(?:per favore,? |puoi |potresti )+"), "")
        // L'intera frase deve corrispondere: mai ignorare cartelle, esclusioni o altri vincoli.
        val pattern = Regex("^(?:(?:quali sono|quali|mostra|mostrami|elenca|trova|trovami|fammi vedere) )?(?:i |gli |dei )?(?:(\\d{1,3}) )?file (?:piu (?:grandi|pesanti|voluminosi)|(?:che )?(?:occupano|pesano) (?:di piu|maggiormente|piu spazio|piu memoria|piu spazio in memoria|piu spazio sul telefono)(?: (?:la memoria|memoria|sul telefono|nella memoria|nella memoria del telefono))?)(?: (?:del telefono|nella memoria condivisa))?$")
        val match = pattern.matchEntire(n) ?: return null
        val count = match.groupValues[1].toIntOrNull()?.coerceIn(1, 200) ?: 20
        val files = entries.asSequence().filter { it.size > 0 }
            .sortedWith(compareByDescending<FileEntry> { it.size }.thenBy { it.rel })
            .filter { Storage.sanitize(it.rel) == it.rel && Storage.file(it.rel).isFile }.take(count).toList()
        if (files.isEmpty()) return BrainReply("Non trovo file nella memoria condivisa accessibile. Controlla i permessi nella scheda File.", fileResults = emptyList())
        return BrainReply("Ecco i ${files.size} file più pesanti, dal più grande al più piccolo: ${Fmt.size(files.sumOf { it.size })} in totale. " +
            "Tocca un file per aprirlo. L'elenco riguarda i file accessibili, non i dati privati delle altre app.", fileResults = files)
    }

    fun followUp(previous: List<FileEntry>, entries: List<FileEntry>): BrainReply {
        val current = entries.associateBy { it.rel }
        // Mantiene l'ordine della ricerca precedente e aggiorna dimensioni e file rimossi.
        val files = previous.mapNotNull { current[it.rel] }.filter { Storage.sanitize(it.rel) == it.rel && Storage.file(it.rel).isFile }
        return BrainReply(if (files.isEmpty()) "I file dell'elenco precedente non sono più disponibili. Prova una nuova ricerca."
            else "Ecco i ${files.size} file dell'elenco precedente. Tocca un file per aprirlo.", fileResults = files)
    }
}
