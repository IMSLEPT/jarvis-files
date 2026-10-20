package com.jarvis.files.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jarvis.files.core.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class AutoType(val title: String, val desc: String) {
    SORT_DOWNLOAD("Riordina Download", "Mette i file nuovi di Download nelle sottocartelle giuste. Si può annullare."),
    PHOTO_INDEX("Analisi foto", "Riconosce volti, oggetti e luoghi nelle foto nuove."),
    SECURITY_SCAN("Controllo sicurezza", "Cerca app sospette, APK e impostazioni a rischio."),
    SPACE_CHECK("Avviso spazio", "Ti avvisa quando lo spazio libero scende sotto la soglia."),
    JUNK_CLEAN("Pulizia file inutili", "Sposta nel cestino APK già installati, file temporanei e cartelle vuote."),
    SCREENSHOT_CLEAN("Screenshot vecchi", "Sposta nel cestino gli screenshot più vecchi di 60 giorni."),
    DUPLICATES_CHECK("Cerca doppioni", "Conta i file doppi e ti avvisa quanto spazio puoi liberare."),
    WEEKLY_REPORT("Resoconto settimanale", "Riepilogo di spazio liberato, file sistemati e problemi trovati."),
    AI_COMMAND("Comando a Jarvis", "Un tuo comando (es. \"riordina i documenti\"): Jarvis prepara il piano e ti chiede conferma."),
}

enum class Freq(val label: String, val days: Long) { DAILY("Ogni giorno", 1), WEEKLY("Ogni settimana", 7), MONTHLY("Ogni mese", 30) }

data class Automation(
    val id: Long,
    val type: AutoType,
    val freq: Freq,
    val hour: Int,
    val minute: Int,
    val dayOfWeek: Int, // Calendar.MONDAY..SUNDAY
    val enabled: Boolean,
    val prompt: String = "",
    val lastRun: Long = 0,
    val lastResult: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("type", type.name).put("freq", freq.name).put("h", hour).put("m", minute)
        .put("dow", dayOfWeek).put("on", enabled).put("prompt", prompt).put("last", lastRun).put("res", lastResult)

    fun whenLabel(): String {
        val time = "%02d:%02d".format(hour, minute)
        return when (freq) {
            Freq.DAILY -> "Ogni giorno alle $time"
            Freq.WEEKLY -> "Ogni ${DAYS[dayOfWeek] ?: "settimana"} alle $time"
            Freq.MONTHLY -> "Ogni mese alle $time"
        }
    }

    companion object {
        val DAYS = mapOf(
            Calendar.MONDAY to "lunedì", Calendar.TUESDAY to "martedì", Calendar.WEDNESDAY to "mercoledì", Calendar.THURSDAY to "giovedì",
            Calendar.FRIDAY to "venerdì", Calendar.SATURDAY to "sabato", Calendar.SUNDAY to "domenica",
        )

        fun fromJson(o: JSONObject) = Automation(
            o.getLong("id"), AutoType.valueOf(o.getString("type")), Freq.valueOf(o.getString("freq")), o.getInt("h"), o.getInt("m"),
            o.optInt("dow", Calendar.MONDAY), o.optBoolean("on", true), o.optString("prompt"), o.optLong("last"), o.optString("res"),
        )
    }
}

object Automations {
    private fun file(c: Context) = File(c.filesDir, "automations.json")
    private val lock = Any()

    fun all(c: Context): List<Automation> = synchronized(lock) {
        runCatching {
            val a = JSONArray(file(c).readText())
            (0 until a.length()).map { Automation.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun saveAll(c: Context, list: List<Automation>) = synchronized(lock) {
        file(c).writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
    }

    fun get(c: Context, id: Long) = all(c).firstOrNull { it.id == id }

    fun upsert(c: Context, a: Automation) {
        val list = all(c).filter { it.id != a.id } + a
        saveAll(c, list.sortedBy { it.id })
        schedule(c, a)
    }

    fun delete(c: Context, id: Long) {
        saveAll(c, all(c).filter { it.id != id })
        WorkManager.getInstance(c).cancelUniqueWork("auto_$id")
    }

    fun recordRun(c: Context, id: Long, result: String) {
        val a = get(c, id) ?: return
        saveAll(c, all(c).map { if (it.id == id) a.copy(lastRun = System.currentTimeMillis(), lastResult = result) else it })
    }

    /** Automazioni predefinite create al primo avvio (modificabili). */
    fun createDefaults(c: Context) {
        val p = Prefs(c)
        if (p.defaultsCreated) return
        val base = System.currentTimeMillis()
        listOf(
            Automation(base + 1, AutoType.PHOTO_INDEX, Freq.DAILY, 1, 30, Calendar.MONDAY, true),
            Automation(base + 2, AutoType.SPACE_CHECK, Freq.DAILY, 20, 0, Calendar.MONDAY, true),
            Automation(base + 3, AutoType.SORT_DOWNLOAD, Freq.WEEKLY, 9, 0, Calendar.MONDAY, true),
            Automation(base + 4, AutoType.WEEKLY_REPORT, Freq.WEEKLY, 19, 0, Calendar.SUNDAY, true),
            Automation(base + 5, AutoType.SECURITY_SCAN, Freq.WEEKLY, 21, 0, Calendar.WEDNESDAY, true),
        ).forEach { upsert(c, it) }
        p.defaultsCreated = true
    }

    fun rescheduleAll(c: Context) = all(c).forEach { schedule(c, it) }

    private fun nextDelayMs(a: Automation): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, a.hour); set(Calendar.MINUTE, a.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (a.freq == Freq.WEEKLY) {
                set(Calendar.DAY_OF_WEEK, a.dayOfWeek)
                if (!after(now)) add(Calendar.DAY_OF_YEAR, 7)
            } else if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    fun schedule(c: Context, a: Automation) {
        val wm = WorkManager.getInstance(c)
        if (!a.enabled) { wm.cancelUniqueWork("auto_${a.id}"); return }
        val constraints = Constraints.Builder().apply {
            if (a.type == AutoType.PHOTO_INDEX) {
                if (Prefs(c).photoIndexOnlyCharging) setRequiresCharging(true)
                setRequiresBatteryNotLow(true)
            }
        }.build()
        val req = PeriodicWorkRequestBuilder<AutomationWorker>(a.freq.days, TimeUnit.DAYS)
            .setInitialDelay(nextDelayMs(a), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setInputData(Data.Builder().putLong(AutomationWorker.KEY_ID, a.id).build())
            .build()
        wm.enqueueUniquePeriodicWork("auto_${a.id}", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun runNow(c: Context, id: Long) {
        val req = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setInputData(Data.Builder().putLong(AutomationWorker.KEY_ID, id).putBoolean(AutomationWorker.KEY_MANUAL, true).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("now_$id", ExistingWorkPolicy.KEEP, req)
    }

    /** Manutenzione giornaliera interna: svuota dal cestino gli elementi scaduti. */
    fun scheduleMaintenance(c: Context) {
        val req = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("maintenance", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Analisi foto immediata (senza vincolo di ricarica), lanciata dall'utente. */
    fun indexPhotosNow(c: Context, auto: Boolean = false) {
        Prefs(c).photoStopRequested = false
        val req = OneTimeWorkRequestBuilder<PhotoIndexWorker>()
            .setInputData(Data.Builder().putBoolean(PhotoIndexWorker.KEY_AUTO, auto).build())
            .apply {
                // In automatico: solo in carica e con batteria non bassa (se Android interrompe il lavoro, riprende alle stesse condizioni)
                if (auto) setConstraints(Constraints.Builder().apply {
                    if (Prefs(c).photoIndexOnlyCharging) setRequiresCharging(true)
                    setRequiresBatteryNotLow(true)
                }.build())
            }
            .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork(PhotoIndexWorker.UNIQUE, ExistingWorkPolicy.KEEP, req)
    }

    /** Ferma subito l'analisi foto in corso (finisce solo la foto che sta elaborando). */
    fun stopPhotoIndex(c: Context) {
        Prefs(c).photoStopRequested = true
        WorkManager.getInstance(c).cancelUniqueWork(PhotoIndexWorker.UNIQUE)
    }
}
