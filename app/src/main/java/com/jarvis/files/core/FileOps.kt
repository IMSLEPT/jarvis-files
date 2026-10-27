package com.jarvis.files.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Un'operazione concreta, già risolta su file reali, mostrata in anteprima. */
sealed class Op {
    abstract val label: String

    data class Mkdir(val dir: String) : Op() {
        override val label get() = "Crea cartella  $dir"
    }
    data class Move(val from: String, val to: String) : Op() {
        override val label get() =
            if (from.substringBeforeLast('/', "") == to.substringBeforeLast('/', ""))
                "Rinomina  ${from.substringAfterLast('/')}  →  ${to.substringAfterLast('/')}"
            else "Sposta  $from  →  ${to.substringBeforeLast('/', "")}/"
    }
    data class Trash(val path: String, val size: Long) : Op() {
        override val label get() = "Cestino  $path"
    }
}

/** Filtro per azioni di gruppo proposte dall'AI. */
data class Where(
    val folder: String?,
    val recursive: Boolean,
    val ext: List<String>,
    val nameContains: List<String>,
    val olderThanDays: Int?,
    val newerThanDays: Int?,
    val minSizeMb: Double?,
) {
    fun matches(e: FileEntry, now: Long): Boolean {
        if (folder != null) {
            val f = folder.trim('/')
            if (f.isNotEmpty()) {
                if (recursive) {
                    if (!(e.folder == f || e.folder.startsWith("$f/"))) return false
                } else if (e.folder != f) return false
            } else if (!recursive && e.folder.isNotEmpty()) return false
        }
        if (ext.isNotEmpty() && e.ext !in ext) return false
        if (nameContains.isNotEmpty() && nameContains.none { e.name.contains(it, ignoreCase = true) }) return false
        val ageDays = (now - e.modified) / 86_400_000L
        if (olderThanDays != null && ageDays < olderThanDays) return false
        if (newerThanDays != null && ageDays > newerThanDays) return false
        if (minSizeMb != null && e.size < minSizeMb * 1024 * 1024) return false
        return true
    }

    companion object {
        fun from(o: JSONObject?): Where? {
            o ?: return null
            fun strList(k: String): List<String> {
                val a = o.optJSONArray(k) ?: return o.optString(k, "").takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
                return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
            }
            return Where(
                folder = o.optString("folder", "").takeIf { o.has("folder") }?.let { Storage.sanitize(it) ?: return null },
                recursive = o.optBoolean("recursive", false),
                ext = strList("ext").map { it.lowercase(Locale.ROOT).trimStart('.') },
                nameContains = strList("name_contains"),
                olderThanDays = o.optInt("older_than_days", -1).takeIf { it >= 0 },
                newerThanDays = o.optInt("newer_than_days", -1).takeIf { it >= 0 },
                minSizeMb = o.optDouble("min_size_mb", -1.0).takeIf { it > 0 },
            )
        }
    }
}

data class Plan(val ops: List<Op>, val warnings: List<String>) {
    val moves get() = ops.count { it is Op.Move }
    val trashes get() = ops.count { it is Op.Trash }
    val mkdirs get() = ops.count { it is Op.Mkdir }
    val isEmpty get() = ops.isEmpty()

    fun summary(): String = buildList {
        if (mkdirs > 0) add("$mkdirs cartelle nuove")
        val renames = ops.count { it is Op.Move && it.from.substringBeforeLast('/', "") == it.to.substringBeforeLast('/', "") }
        if (moves - renames > 0) add("${moves - renames} file spostati")
        if (renames > 0) add("$renames rinominati")
        if (trashes > 0) add("$trashes nel cestino (${Fmt.size(ops.filterIsInstance<Op.Trash>().sumOf { it.size })})")
    }.joinToString(" · ").ifEmpty { "Nessuna modifica" }

    fun toJson(): JSONArray = JSONArray().apply {
        ops.forEach { op ->
            put(when (op) {
                is Op.Mkdir -> JSONObject().put("t", "mkdir").put("d", op.dir)
                is Op.Move -> JSONObject().put("t", "move").put("f", op.from).put("to", op.to)
                is Op.Trash -> JSONObject().put("t", "trash").put("p", op.path).put("s", op.size)
            })
        }
    }

    companion object {
        fun fromJson(a: JSONArray): Plan = Plan((0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            when (o.optString("t")) {
                "mkdir" -> Op.Mkdir(o.getString("d"))
                "move" -> Op.Move(o.getString("f"), o.getString("to"))
                "trash" -> Op.Trash(o.getString("p"), o.optLong("s"))
                else -> null
            }
        }, emptyList())
    }
}

data class ExecResult(val done: Int, val failed: Int, val freedBytes: Long, val errors: List<String>)

/** Trasforma le azioni JSON dell'AI in un piano concreto, poi lo esegue con cestino e annulla. */
object FileOps {

    /** Converte l'array "actions" dell'AI in operazioni concrete e verificate. */
    fun buildPlan(actions: JSONArray?): Plan {
        if (actions == null || actions.length() == 0) return Plan(emptyList(), emptyList())
        val entries = FileIndex.ensure()
        val now = System.currentTimeMillis()
        val ops = ArrayList<Op>()
        val warnings = ArrayList<String>()
        val reservedTargets = HashSet<String>()
        val touched = HashSet<String>()
        val createdDirs = LinkedHashSet<String>()

        fun ensureDir(dir: String) {
            if (dir.isEmpty()) return
            if (!Storage.file(dir).isDirectory && createdDirs.add(dir)) ops.add(Op.Mkdir(dir))
        }

        fun uniqueTarget(target: String): String {
            var t = target
            var n = 1
            val base = target.substringBeforeLast('.')
            val ext = target.substringAfterLast('.', "").let { if (it.isEmpty() || '/' in it) "" else ".$it" }
            val baseNoExt = if (ext.isEmpty()) target else base
            while (Storage.file(t).exists() || t in reservedTargets) {
                t = "$baseNoExt ($n)$ext"
                n++
            }
            reservedTargets.add(t)
            return t
        }

        fun addMove(from: String, toDir: String, newName: String? = null) {
            if (from.isEmpty() || from in protectedTop) { warnings.add("Cartella protetta: $from"); return }
            if (from in touched) return
            val src = Storage.file(from)
            if (!src.exists()) { warnings.add("Non trovato: $from"); return }
            val name = cleanName(newName ?: src.name, src)
            val target = if (toDir.isEmpty()) name else "$toDir/$name"
            if (target == from) return
            if (src.isDirectory && (toDir == from || toDir.startsWith("$from/"))) {
                warnings.add("Impossibile spostare una cartella dentro sé stessa: $from"); return
            }
            ensureDir(toDir)
            touched.add(from)
            ops.add(Op.Move(from, uniqueTarget(target)))
        }

        fun addTrash(path: String) {
            if (path in touched) return
            if (path.isEmpty() || path.count { it == '/' } == 0 && Storage.file(path).isDirectory && path in protectedTop) {
                warnings.add("Cartella di sistema protetta: $path"); return
            }
            val f = Storage.file(path)
            if (!f.exists()) { warnings.add("Non trovato: $path"); return }
            touched.add(path)
            ops.add(Op.Trash(path, if (f.isDirectory) dirSize(f) else f.length()))
        }

        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i) ?: continue
            when (a.optString("type").lowercase(Locale.ROOT)) {
                "mkdir" -> Storage.sanitize(a.optString("path"))?.let { ensureDir(it) }
                "move" -> {
                    val from = Storage.sanitize(a.optString("from")) ?: continue
                    var to = Storage.sanitize(a.optString("to")) ?: continue
                    var newName = a.optString("new_name").takeIf { it.isNotBlank() }
                    // L'AI a volte indica come destinazione il percorso completo del file ("Cartella/file.pdf"):
                    // se l'ultimo pezzo ha un'estensione e non è una cartella esistente, è il nuovo nome.
                    val last = to.substringAfterLast('/')
                    val srcFile = Storage.file(from)
                    if (srcFile.isFile && '.' in last && !Storage.file(to).isDirectory &&
                        last.substringAfterLast('.').equals(srcFile.extension, ignoreCase = true)
                    ) {
                        if (newName == null) newName = last
                        to = to.substringBeforeLast('/', "")
                    }
                    addMove(from, to, newName)
                }
                "rename" -> {
                    val p = Storage.sanitize(a.optString("path")) ?: continue
                    val nn = a.optString("new_name").takeIf { it.isNotBlank() } ?: continue
                    addMove(p, p.substringBeforeLast('/', ""), nn)
                }
                "delete" -> Storage.sanitize(a.optString("path"))?.let { addTrash(it) }
                "move_where", "delete_where", "rename_where" -> {
                    val filter = a.optJSONObject("where")
                    if (filter == null || !filter.has("folder") || filter.isNull("folder")) {
                        warnings.add("Manca la cartella per l'operazione di gruppo")
                        continue
                    }
                    val w = Where.from(filter) ?: continue
                    if (w.folder.isNullOrEmpty()) { warnings.add("Operazioni di gruppo sulla radice non consentite"); continue }
                    val matched = entries.filter { w.matches(it, now) }.sortedBy { it.modified }
                    when (a.optString("type").lowercase(Locale.ROOT)) {
                        "move_where" -> {
                            val to = Storage.sanitize(a.optString("to")) ?: continue
                            matched.forEach { addMove(it.rel, to) }
                        }
                        "delete_where" -> matched.forEach { addTrash(it.rel) }
                        else -> {
                            val pattern = a.optString("pattern", "{data} {nome}")
                            matched.forEachIndexed { idx, e ->
                                addMove(e.rel, e.folder, applyPattern(pattern, e, idx + 1))
                            }
                        }
                    }
                }
                else -> warnings.add("Azione non supportata: ${a.optString("type")}")
            }
        }
        return Plan(ops, warnings)
    }

    private val protectedTop = setOf(
        "DCIM", "Download", "Pictures", "Music", "Movies", "Documents", "Android", "Alarms",
        "Notifications", "Ringtones", "Podcasts", "Audiobooks", "Recordings",
    )

    /** Segnaposto: {data} {nome} {n} {anno} {mese} {ext}. */
    fun applyPattern(pattern: String, e: FileEntry, n: Int): String {
        val base = e.name.substringBeforeLast('.')
        var out = pattern
            .replace("{data}", Fmt.iso(e.modified))
            .replace("{anno}", Fmt.iso(e.modified).take(4))
            .replace("{mese}", Fmt.iso(e.modified).substring(5, 7))
            .replace("{nome}", base)
            .replace("{n}", n.toString().padStart(3, '0'))
            .replace("{ext}", e.ext)
        if (e.ext.isNotEmpty() && !out.endsWith(".${e.ext}", ignoreCase = true)) out += ".${e.ext}"
        return out
    }

    /** Pulisce il nome proposto: caratteri vietati e estensione mancante. */
    private fun cleanName(name: String, src: File): String {
        var n = name.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").replace(Regex("\\s+"), " ").trim().trim('.')
        if (n.isEmpty()) n = src.name
        val ext = src.extension
        if (src.isFile && ext.isNotEmpty() && !n.endsWith(".$ext", ignoreCase = true)) n = "$n.$ext"
        return Storage.fitFileName(n)
    }

    fun dirSize(f: File): Long = f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    // ---------------- Esecuzione ----------------

    @Synchronized fun execute(context: Context, plan: Plan, source: String): ExecResult {
        val journal = JSONArray()
        var done = 0
        var failed = 0
        var freed = 0L
        var moved = 0
        val errors = ArrayList<String>()
        val scan = ArrayList<File>()
        for (op in plan.ops) {
            try {
                when (op) {
                    is Op.Mkdir -> {
                        require(Storage.sanitize(op.dir) == op.dir && op.dir.isNotEmpty()) { "Percorso non valido" }
                        val d = Storage.file(op.dir)
                        require(!d.exists() || d.isDirectory) { "Esiste già un file con questo nome" }
                        if (!d.exists()) {
                            if (d.mkdirs()) journal.put(JSONObject().put("t", "mkdir").put("d", op.dir))
                            else throw IllegalStateException("cartella non creata")
                        }
                        done++
                    }
                    is Op.Move -> {
                        require(Storage.sanitize(op.from) == op.from && op.from.isNotEmpty() && op.from !in protectedTop) { "Origine non valida" }
                        require(Storage.sanitize(op.to) == op.to && op.to.isNotEmpty()) { "Destinazione non valida" }
                        require(!op.to.startsWith(op.from + "/")) { "Cartella dentro sé stessa" }
                        val src = Storage.file(op.from)
                        val dst = Storage.file(op.to)
                        dst.parentFile?.mkdirs()
                        if (dst.exists()) throw IllegalStateException("esiste già ${op.to}")
                        if (!moveFile(src, dst)) throw IllegalStateException("spostamento fallito")
                        journal.put(JSONObject().put("t", "move").put("f", op.from).put("to", op.to))
                        scan.add(src); scan.add(dst)
                        moved++
                        done++
                    }
                    is Op.Trash -> {
                        require(Storage.sanitize(op.path) == op.path && op.path.isNotEmpty() && op.path !in protectedTop) { "Percorso protetto o non valido" }
                        val trashed = Trash.put(context, op.path) ?: throw IllegalStateException("cestino non riuscito")
                        journal.put(JSONObject().put("t", "trash").put("p", op.path).put("id", trashed))
                        scan.add(Storage.file(op.path))
                        freed += op.size
                        done++
                    }
                }
            } catch (t: Throwable) {
                failed++
                if (errors.size < 20) errors.add("${op.label}: ${t.message}")
            }
        }
        Undo.save(context, journal, source)
        Storage.scanMedia(context, scan)
        FileIndex.invalidate()
        Stats.addOrganized(context, moved)
        return ExecResult(done, failed, freed, errors)
    }

    /** Rinomina (stesso volume) con fallback copia+cancella. */
    fun moveFile(src: File, dst: File): Boolean {
        if (!src.exists() || dst.exists() || src.canonicalFile == dst.canonicalFile) return false
        if (src.isDirectory && dst.canonicalPath.startsWith(src.canonicalPath + "/")) return false
        if (src.renameTo(dst)) return true
        if (src.isDirectory) return false
        return runCatching {
            src.inputStream().use { i -> dst.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
            dst.setLastModified(src.lastModified())
            if (dst.length() == src.length() && src.delete()) true else { dst.delete(); false }
        }.onFailure { dst.delete() }.getOrDefault(false)
    }
}

/** Registro dell'ultima operazione per il tasto Annulla. */
object Undo {
    private fun file(c: Context) = File(c.filesDir, "undo.json")

    fun save(c: Context, journal: JSONArray, source: String) {
        if (journal.length() == 0) return
        file(c).writeText(JSONObject().put("at", System.currentTimeMillis()).put("source", source).put("ops", journal).toString())
    }

    fun info(c: Context): Pair<String, Int>? = runCatching {
        val o = JSONObject(file(c).readText())
        o.getString("source") to o.getJSONArray("ops").length()
    }.getOrNull()

    /** Ripristina in ordine inverso. Restituisce (riusciti, falliti). */
    fun undo(c: Context): Pair<Int, Int> {
        val f = file(c)
        if (!f.exists()) return 0 to 0
        val saved = JSONObject(f.readText())
        val ops = saved.getJSONArray("ops")
        val remaining = ArrayList<JSONObject>()
        var ok = 0
        var ko = 0
        val scan = ArrayList<File>()
        for (i in ops.length() - 1 downTo 0) {
            val o = ops.getJSONObject(i)
            val success = runCatching {
                when (o.getString("t")) {
                    "move" -> {
                        val from = Storage.file(o.getString("f"))
                        val to = Storage.file(o.getString("to"))
                        from.parentFile?.mkdirs()
                        scan.add(from); scan.add(to)
                        !from.exists() && FileOps.moveFile(to, from)
                    }
                    "trash" -> Trash.restore(c, o.getString("id"))
                    "mkdir" -> {
                        val d = Storage.file(o.getString("d"))
                        (d.list()?.isEmpty() == true && d.delete()) || !d.exists()
                    }
                    else -> true
                }
            }.getOrDefault(false)
            if (success) ok++ else { ko++; remaining.add(o) }
        }
        if (remaining.isEmpty()) f.delete()
        else f.writeText(saved.put("ops", JSONArray(remaining.asReversed())).toString())
        Storage.scanMedia(c, scan)
        FileIndex.invalidate()
        return ok to ko
    }
}

