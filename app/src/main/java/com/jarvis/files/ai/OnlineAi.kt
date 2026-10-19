package com.jarvis.files.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Servizi gratuiti preimpostati che ospitano modelli open source (API compatibile OpenAI). */
data class OnlinePreset(val name: String, val baseUrl: String, val model: String, val keyUrl: String, val note: String)

val ONLINE_PRESETS = listOf(
    OnlinePreset(
        "Groq (consigliato)", "https://api.groq.com/openai/v1", "openai/gpt-oss-120b",
        "https://console.groq.com/keys", "Velocissimo. Modelli open source (GPT-OSS, Llama, Qwen). Piano gratuito con limiti giornalieri."
    ),
    OnlinePreset(
        "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-oss-120b:free",
        "https://openrouter.ai/keys", "Molti modelli open source con variante \":free\". Limiti giornalieri bassi."
    ),
    OnlinePreset(
        "Cerebras", "https://api.cerebras.ai/v1", "gpt-oss-120b",
        "https://cloud.cerebras.ai", "Molto veloce. Verifica i limiti del piano gratuito."
    ),
)

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
            val resp = post("$baseUrl/chat/completions", body.toString())
            val o = JSONObject(resp)
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
