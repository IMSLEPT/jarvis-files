package com.jarvis.files.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Servizi gratuiti preimpostati che ospitano modelli open source (API compatibile OpenAI). */
data class OnlinePreset(val name: String, val baseUrl: String, val model: String, val keyUrl: String, val note: String, val tokenLimit: Int)

val ONLINE_PRESETS = listOf(
    OnlinePreset(
        "Groq (consigliato)", "https://api.groq.com/openai/v1", "openai/gpt-oss-120b",
        "https://console.groq.com/keys", "Velocissimo. Modelli open source (GPT-OSS, Llama, Qwen). Piano gratuito: 8000 token al minuto, Jarvis si adatta da solo.", 7000
    ),
    OnlinePreset(
        "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-oss-120b:free",
        "https://openrouter.ai/keys", "Molti modelli open source con variante \":free\". Limiti giornalieri bassi.", 30000
    ),
    OnlinePreset(
        "Cerebras", "https://api.cerebras.ai/v1", "gpt-oss-120b",
        "https://cloud.cerebras.ai", "Molto veloce. Verifica i limiti del piano gratuito.", 7000
    ),
)

/** La richiesta supera i token al minuto consentiti dal piano (es. Groq gratuito: 8000). */
class TooLarge(val limit: Int, val requested: Int) : Exception("Richiesta troppo grande: $requested su $limit token")

class RateLimited(val waitSeconds: Int) : Exception("Limite al minuto, attendere $waitSeconds s")

/** Client minimale /chat/completions compatibile OpenAI. */
class OnlineAi(private val baseUrl: String, private val apiKey: String, private val model: String) : AiEngine {
    override val displayName: String get() = "Internet · ${model.substringAfterLast('/')}"

    override suspend fun complete(system: String, messages: List<ChatMsg>, maxTokens: Int): String =
        withContext(Dispatchers.IO) {
            val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
            messages.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.text)) }
            val body = JSONObject()
                .put("model", model)
                .put("messages", msgs)
                .put("temperature", 0.2)
                .put("max_tokens", maxTokens)
            var resp: String? = null
            for (attempt in 0..1) {
                try {
                    resp = post("$baseUrl/chat/completions", body.toString())
                    break
                } catch (e: RateLimited) {
                    // Limite al minuto del piano gratuito: se l'attesa è breve, aspetta e riprova una volta
                    if (attempt == 0 && e.waitSeconds in 1..25) kotlinx.coroutines.delay(e.waitSeconds * 1000L + 500)
                    else throw AiUnavailable("Limite gratuito raggiunto per ora. Riprova tra ${e.waitSeconds.coerceAtLeast(1)} secondi o usa l'AI nel telefono.")
                }
            }
            val o = JSONObject(resp!!)
            o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "")
        }

    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val c = (URL("$baseUrl/models").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $apiKey")
            connectTimeout = 15_000; readTimeout = 30_000
        }
        val text = readResponse(c)
        val data = JSONObject(text).optJSONArray("data") ?: JSONArray()
        (0 until data.length()).map { data.getJSONObject(it).optString("id") }.filter { it.isNotBlank() }.sorted()
    }

    private fun post(url: String, json: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 180_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("HTTP-Referer", "https://github.com/IMSLEPT/jarvis-files")
            setRequestProperty("X-Title", "Jarvis Files")
        }
        c.outputStream.use { it.write(json.toByteArray()) }
        return readResponse(c)
    }

    private fun readResponse(c: HttpURLConnection): String {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(text).getJSONObject("error").optString("message") }.getOrNull()
            // "Limit 8000, Requested 13764": richiesta troppo grande per il piano gratuito
            Regex("Limit (\\d+), Requested (\\d+)").find(msg ?: text)?.let { m ->
                val limit = m.groupValues[1].toInt(); val req = m.groupValues[2].toInt()
                if (code == 413 || req > limit) throw TooLarge(limit, req)
            }
            if (code == 429) {
                val wait = Regex("try again in (?:(\\d+)m)?([\\d.]+)s").find(msg ?: text)?.let {
                    (it.groupValues[1].toIntOrNull() ?: 0) * 60 + it.groupValues[2].toDouble().toInt() + 1
                } ?: 60
                throw RateLimited(wait)
            }
            throw AiUnavailable(
                when (code) {
                    401, 403 -> "Chiave non valida o scaduta ($code)."
                    429 -> "Limite gratuito raggiunto per ora. Riprova più tardi o usa l'AI nel telefono."
                    else -> "Errore del servizio online ($code): ${msg ?: text.take(200)}"
                }
            )
        }
        return text
    }
}
