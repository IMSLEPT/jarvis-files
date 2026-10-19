package com.jarvis.files.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.jarvis.files.core.AiMode
import com.jarvis.files.core.Prefs
import org.json.JSONObject
import java.io.File

data class ChatMsg(val role: String, val text: String) // role: "user" | "assistant"

/** Un motore AI capace di completare una conversazione. */
interface AiEngine {
    val displayName: String
    suspend fun complete(system: String, messages: List<ChatMsg>, maxTokens: Int = 4096): String
}

class AiUnavailable(msg: String) : Exception(msg)

/** Sceglie il motore giusto in base alle impostazioni e alla connessione. */
object AiRouter {

    fun isOnline(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun localReady(c: Context): Boolean {
        val p = Prefs(c).localModelPath
        val dl = LocalAi.downloadState(c)
        return p.isNotEmpty() && File(p).let { it.exists() && it.length() > 100_000_000 } &&
            dl?.running != true && dl?.failed != true
    }

    fun onlineConfigured(c: Context) = Prefs(c).onlineApiKey.isNotBlank()

    /** Restituisce il motore da usare o lancia [AiUnavailable] con un messaggio chiaro. */
    fun engine(c: Context, preferLocal: Boolean = false): AiEngine {
        val p = Prefs(c)
        val online = { OnlineAi(p.onlineBaseUrl, p.onlineApiKey, p.onlineModel) }
        val local = { LocalAi.engine(c) }
        return when (p.aiMode) {
            AiMode.LOCAL -> if (localReady(c)) local()
                else throw AiUnavailable("Il cervello offline non è ancora scaricato. Vai in Impostazioni → AI nel telefono.")
            AiMode.ONLINE -> when {
                !onlineConfigured(c) -> throw AiUnavailable("Manca la chiave gratuita del servizio online. Vai in Impostazioni → AI su internet.")
                !isOnline(c) -> throw AiUnavailable("Nessuna connessione a internet.")
                else -> online()
            }
            AiMode.AUTO -> when {
                preferLocal && localReady(c) -> local()
                onlineConfigured(c) && isOnline(c) -> online()
                localReady(c) -> local()
                else -> throw AiUnavailable(
                    "Nessuna AI pronta. In Impostazioni puoi scaricare l'AI nel telefono (gratis, offline) " +
                        "oppure inserire una chiave gratuita per l'AI su internet."
                )
            }
        }
    }

    /** Quante righe di elenco file conviene mandare a questo motore (contesto limitato in locale). */
    fun fileBudget(engine: AiEngine) = if (engine is OnlineAi) 450 else 120
}

/** Estrae il primo oggetto JSON valido da una risposta del modello (anche dentro ```json ... ```). */
fun extractJson(text: String): JSONObject? {
    val cleaned = text.replace("```json", "```").let {
        val s = it.indexOf("```")
        val e = it.lastIndexOf("```")
        if (s >= 0 && e > s) it.substring(s + 3, e) else it
    }
    for (candidate in listOf(cleaned, text)) {
        var start = candidate.indexOf('{')
        while (start >= 0) {
            var depth = 0
            var inStr = false
            var esc = false
            for (i in start until candidate.length) {
                val ch = candidate[i]
                if (inStr) {
                    if (esc) esc = false
                    else if (ch == '\\') esc = true
                    else if (ch == '"') inStr = false
                    continue
                }
                when (ch) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            runCatching { return JSONObject(candidate.substring(start, i + 1)) }
                            break
                        }
                    }
                }
            }
            start = candidate.indexOf('{', start + 1)
        }
    }
    return null
}
