package com.jarvis.files.ai

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout

/** Blocca una generazione degenerata prima che diventi una risposta o entri nella cronologia. */
object ResponseGuard {
    private val repeatedCharacter = Regex("(\\S)\\1{23,}")
    private val repeatedBlock = Regex("(.{3,80}?)\\1{5,}")

    fun isRepetitive(text: String): Boolean {
        val compact = text.replace(Regex("\\s+"), " ")
        return repeatedCharacter.containsMatchIn(compact) || repeatedBlock.containsMatchIn(compact)
    }

    fun checkRaw(text: String) {
        if (text.length > 12_000 || isRepetitive(text))
            throw AiUnavailable("Ho interrotto una risposta anomala dell'AI. Nessun file è stato modificato. Prova a indicare una richiesta più precisa.")
    }

    fun checkReply(text: String) {
        checkRaw(text)
        if (text.length > 3_000 || Regex("<\\|(?:im_start|im_end|endoftext)\\|>|<start_of_turn>|<end_of_turn>").containsMatchIn(text))
            throw AiUnavailable("La risposta dell'AI non è utilizzabile. Nessun file è stato modificato. Prova a riformulare la richiesta.")
    }

    suspend fun collect(chunks: Flow<String>, cancel: () -> Unit, timeoutMs: Long = 90_000): String {
        var completed = false
        try {
            val result = withTimeout(timeoutMs) {
                val text = StringBuilder()
                chunks.collect { chunk -> text.append(chunk); checkRaw(text.toString()) }
                text.toString()
            }
            completed = true
            return result
        } catch (_: TimeoutCancellationException) {
            throw AiUnavailable("L'AI locale sta impiegando troppo tempo: ho interrotto la risposta. Puoi riprovare con una richiesta più breve o scegliere il modello più veloce nelle impostazioni.")
        } finally {
            if (!completed) runCatching { cancel() }
        }
    }
}
