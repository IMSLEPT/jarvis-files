package com.jarvis.files.core

import android.content.Context
import android.content.SharedPreferences

/** Modalità di scelta dell'AI. */
enum class AiMode(val label: String) {
    AUTO("Automatica"),
    LOCAL("Solo telefono"),
    ONLINE("Solo internet"),
}

/** Impostazioni persistenti dell'app (SharedPreferences, nessuna dipendenza extra). */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = sp.getString("user_name", "Alessandro") ?: "Alessandro"
        set(v) = sp.edit().putString("user_name", v).apply()

    var aiMode: AiMode
        get() = runCatching { AiMode.valueOf(sp.getString("ai_mode", AiMode.AUTO.name)!!) }.getOrDefault(AiMode.AUTO)
        set(v) = sp.edit().putString("ai_mode", v.name).apply()

    // Servizio online compatibile OpenAI (Groq, OpenRouter, ...)
    var onlineBaseUrl: String
        get() = sp.getString("online_base", "https://api.groq.com/openai/v1") ?: ""
        set(v) = sp.edit().putString("online_base", v.trim().trimEnd('/')).apply()
    var onlineApiKey: String
        get() = sp.getString("online_key", "") ?: ""
        set(v) = sp.edit().putString("online_key", v.trim()).apply()
    var onlineModel: String
        get() = sp.getString("online_model", "openai/gpt-oss-120b") ?: ""
        set(v) = sp.edit().putString("online_model", v.trim()).apply()

    /** Token al minuto consentiti dal servizio online (si adatta da solo dopo un errore 413). */
    var onlineTokenLimit: Int
        get() = sp.getInt("online_tpm", 7000)
        set(v) = sp.edit().putInt("online_tpm", v).apply()

    // Modello locale
    var localModelRepo: String
        get() = sp.getString("local_repo", "litert-community/gemma-4-E2B-it-litert-lm") ?: ""
        set(v) = sp.edit().putString("local_repo", v).apply()
    var localModelPath: String
        get() = sp.getString("local_path", "") ?: ""
        set(v) = sp.edit().putString("local_path", v).apply()
    var localDownloadId: Long
        get() = sp.getLong("local_dl_id", -1L)
        set(v) = sp.edit().putLong("local_dl_id", v).apply()
    var localUseGpu: Boolean
        get() = sp.getBoolean("local_gpu", true)
        set(v) = sp.edit().putBoolean("local_gpu", v).apply()

    var virusTotalKey: String
        get() = sp.getString("vt_key", "") ?: ""
        set(v) = sp.edit().putString("vt_key", v.trim()).apply()

    var biometricLock: Boolean
        get() = sp.getBoolean("bio_lock", true)
        set(v) = sp.edit().putBoolean("bio_lock", v).apply()

    var lowSpaceGb: Int
        get() = sp.getInt("low_space_gb", 20)
        set(v) = sp.edit().putInt("low_space_gb", v).apply()

    var trashDays: Int
        get() = sp.getInt("trash_days", 30)
        set(v) = sp.edit().putInt("trash_days", v).apply()

    /** Analisi foto solo di notte in carica (true) o sempre (false). */
    var photoIndexOnlyCharging: Boolean
        get() = sp.getBoolean("photo_charging", true)
        set(v) = sp.edit().putBoolean("photo_charging", v).apply()

    /** Descrizioni dettagliate delle foto con Gemma (più lento, più preciso). */
    var photoDeepCaptions: Boolean
        get() = sp.getBoolean("photo_deep", true)
        set(v) = sp.edit().putBoolean("photo_deep", v).apply()

    /** Richiesta di fermare l'analisi foto (letta dal lavoro in corso). */
    var photoStopRequested: Boolean
        get() = sp.getBoolean("photo_stop", false)
        set(v) = sp.edit().putBoolean("photo_stop", v).commit().let { }

    /** Ultimo motivo per cui l'analisi si è fermata da sola (calore, batteria...). */
    var photoStopReason: String
        get() = sp.getString("photo_stop_reason", "") ?: ""
        set(v) = sp.edit().putString("photo_stop_reason", v).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(v) = sp.edit().putBoolean("onboarding_done", v).apply()

    var defaultsCreated: Boolean
        get() = sp.getBoolean("defaults_created", false)
        set(v) = sp.edit().putBoolean("defaults_created", v).apply()

    fun getString(key: String, def: String = ""): String = sp.getString(key, def) ?: def
    fun putString(key: String, v: String) = sp.edit().putString(key, v).apply()
}
