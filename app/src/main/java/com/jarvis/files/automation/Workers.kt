package com.jarvis.files.automation

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jarvis.files.ai.Brain
import com.jarvis.files.ai.ChatMsg
import com.jarvis.files.core.Cleaner
import com.jarvis.files.core.FileCategory
import com.jarvis.files.core.FileEntry
import com.jarvis.files.core.FileIndex
import com.jarvis.files.core.FileOps
import com.jarvis.files.core.Fmt
import com.jarvis.files.core.Notifier
import com.jarvis.files.core.Op
import com.jarvis.files.core.Plan
import com.jarvis.files.core.Prefs
import com.jarvis.files.core.Stats
import com.jarvis.files.core.Storage
import com.jarvis.files.core.Trash
import com.jarvis.files.photos.PhotoDb
import com.jarvis.files.photos.PhotoIndexer
import com.jarvis.files.security.SecurityScanner
import com.jarvis.files.security.Severity
import org.json.JSONObject
import java.io.File

/** Piano preparato in background che aspetta la conferma dell'utente. */
object PendingPlan {
    private fun file(c: Context) = File(c.filesDir, "pending_plan.json")

    fun save(c: Context, title: String, reply: String, plan: Plan) {
        file(c).writeText(JSONObject().put("title", title).put("reply", reply).put("plan", plan.toJson()).put("at", System.currentTimeMillis()).toString())
    }

    fun load(c: Context): Triple<String, String, Plan>? = runCatching {
        val o = JSONObject(file(c).readText())
        Triple(o.getString("title"), o.getString("reply"), Plan.fromJson(o.getJSONArray("plan")))
    }.getOrNull()

    fun clear(c: Context) { file(c).delete() }
}

/** Regole fisse (senza AI) usate dalle automazioni. */
object Tasks {
    private val folderNames = mapOf(
        FileCategory.DOCUMENT to listOf("Documenti", "Documents", "PDF", "Docs"),
        FileCategory.IMAGE to listOf("Immagini", "Foto", "Images", "Pictures"),
        FileCategory.VIDEO to listOf("Video", "Videos"),
        FileCategory.AUDIO to listOf("Audio", "Musica", "Music"),
        FileCategory.ARCHIVE to listOf("Archivi", "Archivi ZIP", "Zip", "Archives"),
        FileCategory.APK to listOf("App", "APK", "Installazioni"),
        FileCategory.OTHER to listOf("Altro", "Varie", "Other"),
    )

    /** File sciolti in Download → sottocartelle per tipo, usando quelle che esistono già. */
    fun sortDownloadPlan(): Plan {
        val existing = Storage.file("Download").listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        val hourAgo = System.currentTimeMillis() - 3_600_000
        val loose: List<FileEntry> = FileIndex.ensure(0).filter {
            it.folder == "Download" && it.modified < hourAgo && !it.name.startsWith(".") &&
                it.ext !in setOf("crdownload", "part", "tmp")
        }
        val actions = org.json.JSONArray()
        loose.groupBy { it.category }.forEach { (cat, files) ->
            val names = folderNames[cat] ?: return@forEach
            val target = existing.firstOrNull { e -> names.any { it.equals(e, true) } } ?: names.first()
            files.forEach { f -> actions.put(JSONObject().put("type", "move").put("from", f.rel).put("to", "Download/$target")) }
        }
        return FileOps.buildPlan(actions)
    }

    fun junkPlan(c: Context): Plan =
        Plan(Cleaner.junk(c).filter { it.reason != "Cartella vuota" || Storage.file(it.rel).list()?.isEmpty() == true }
            .map { Op.Trash(it.rel, it.size) }, emptyList())

    fun screenshotPlan(): Plan = Plan(Cleaner.oldScreenshots(60).map { Op.Trash(it.rel, it.size) }, emptyList())
}

class AutomationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    companion object {
        const val KEY_ID = "id"
        const val KEY_MANUAL = "manual"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(4100, Notifier.progress(applicationContext, "Jarvis al lavoro", "Automazione in corso…", 0, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    override suspend fun doWork(): Result {
        val c = applicationContext
        val a = Automations.get(c, inputData.getLong(KEY_ID, -1)) ?: return Result.success()
        if (!Storage.hasAllFilesAccess() && a.type != AutoType.SPACE_CHECK && a.type != AutoType.WEEKLY_REPORT) return Result.success()
        val result = runCatching { execute(c, a) }.getOrElse { "Errore: ${it.message}" }
        Automations.recordRun(c, a.id, result)
        return Result.success()
    }

    private suspend fun execute(c: Context, a: Automation): String {
        val nid = (a.id % 100000).toInt() + 5000
        return when (a.type) {
            AutoType.SORT_DOWNLOAD -> {
                val plan = Tasks.sortDownloadPlan()
                if (plan.isEmpty) "Download già in ordine"
                else {
                    val r = FileOps.execute(c, plan, "Riordino automatico Download")
                    Notifier.notify(c, nid, "Download riordinata", "${r.done} file sistemati. Puoi annullare dall'app.", "home")
                    "${r.done} file sistemati"
                }
            }
            AutoType.PHOTO_INDEX -> {
                val n = PhotoIndexer.run(c, { isStopped }) { }
                "$n foto analizzate"
            }
            AutoType.SECURITY_SCAN -> {
                val f = SecurityScanner.scan(c)
                val high = f.count { it.severity == Severity.HIGH }
                val med = f.count { it.severity == Severity.MEDIUM }
                if (high + med > 0) Notifier.notify(c, nid, "Controllo sicurezza", "$high problemi gravi e $med da verificare. Tocca per i dettagli.", "security")
                "$high gravi, $med medi"
            }
            AutoType.SPACE_CHECK -> {
                val free = Storage.freeBytes()
                val lim = Prefs(c).lowSpaceGb * 1024L * 1024 * 1024
                if (free < lim) Notifier.notify(c, nid, "Spazio quasi finito", "Restano ${Fmt.size(free)} liberi. Tocca per vedere cosa liberare.", "clean")
                "Liberi ${Fmt.size(free)}"
            }
            AutoType.JUNK_CLEAN -> {
                val plan = Tasks.junkPlan(c)
                if (plan.isEmpty) "Niente da pulire" else {
                    val r = FileOps.execute(c, plan, "Pulizia automatica file inutili")
                    Notifier.notify(c, nid, "Pulizia completata", "${r.done} elementi nel cestino (${Fmt.size(r.freedBytes)}).", "clean")
                    "${r.done} nel cestino"
                }
            }
            AutoType.SCREENSHOT_CLEAN -> {
                val plan = Tasks.screenshotPlan()
                if (plan.isEmpty) "Nessuno screenshot vecchio" else {
                    val r = FileOps.execute(c, plan, "Screenshot vecchi nel cestino")
                    Notifier.notify(c, nid, "Screenshot vecchi", "${r.done} screenshot nel cestino (${Fmt.size(r.freedBytes)}).", "clean")
                    "${r.done} nel cestino"
                }
            }
            AutoType.DUPLICATES_CHECK -> {
                val groups = Cleaner.findDuplicates()
                val wasted = groups.sumOf { it.wasted }
                if (groups.isNotEmpty()) Notifier.notify(c, nid, "Doppioni trovati", "${groups.size} gruppi di file doppi: puoi liberare ${Fmt.size(wasted)}.", "clean")
                "${groups.size} gruppi, ${Fmt.size(wasted)}"
            }
            AutoType.WEEKLY_REPORT -> {
                val txt = weeklyReport(c)
                Notifier.notify(c, nid, "Il resoconto della settimana", txt, "home")
                Stats.resetWeek(c)
                "Inviato"
            }
            AutoType.AI_COMMAND -> {
                val (reply, _) = Brain.ask(c, listOf(ChatMsg("user", a.prompt)))
                val plan = reply.plan
                if (plan != null && !plan.isEmpty) {
                    PendingPlan.save(c, a.prompt, reply.reply, plan)
                    Notifier.notify(c, nid, "Jarvis ha preparato un piano", "${plan.summary()}. Tocca per controllare e confermare.", "home")
                    "Piano pronto: ${plan.summary()}"
                } else {
                    Notifier.notify(c, nid, "Jarvis", reply.reply, "chat")
                    reply.reply.take(80)
                }
            }
        }
    }

    private fun weeklyReport(c: Context): String {
        val (photos, _, _) = PhotoDb.get(c).counts()
        return buildString {
            append("Spazio liberato: ${Fmt.size(Stats.week(c, "freed"))}. ")
            append("File sistemati: ${Stats.week(c, "organized")}. ")
            append("Problemi di sicurezza gravi: ${Stats.week(c, "threats")}. ")
            append("Foto analizzate in totale: $photos. ")
            append("Spazio libero ora: ${Fmt.size(Storage.freeBytes())}. Cestino: ${Fmt.size(Trash.totalSize(c))}.")
        }
    }
}

class MaintenanceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (Storage.hasAllFilesAccess()) runCatching { Trash.purgeOld(applicationContext, Prefs(applicationContext).trashDays) }
        return Result.success()
    }
}

class PhotoIndexWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    companion object { const val UNIQUE = "photo_index_now" }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(4200, Notifier.progress(applicationContext, "Analisi foto", "Preparazione…", 0, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    override suspend fun doWork(): Result {
        val c = applicationContext
        if (!Storage.hasAllFilesAccess()) return Result.failure()
        runCatching { setForeground(getForegroundInfo()) }
        val n = PhotoIndexer.run(c, { isStopped }) { p ->
            setProgress(workDataOf("done" to p.done, "total" to p.total, "phase" to p.phase))
            runCatching {
                setForeground(ForegroundInfo(4200, Notifier.progress(c, "Analisi foto · ${p.phase}", "${p.done} di ${p.total}", p.done, p.total),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            }
        }
        Notifier.notify(c, 4201, "Analisi foto completata", "$n foto nuove analizzate. Ora puoi cercarle e dare un nome ai volti.", "photos")
        return Result.success()
    }
}
