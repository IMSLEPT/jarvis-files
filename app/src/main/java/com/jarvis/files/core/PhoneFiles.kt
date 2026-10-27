package com.jarvis.files.core

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.UUID

data class PhoneNode(val rel: String, val directory: Boolean, val bytes: Long, val modified: Long,
    val childCount: Int?, val blocked: String?, val mutable: Boolean) {
    val name get() = rel.substringAfterLast('/')
}
data class FileClipboard(val paths: List<String>, val cut: Boolean)
data class BrowserResult(val result: ExecResult, val remaining: List<String> = emptyList())

/** Navigazione reale, un livello alla volta. Non usa campioni, indice AI o limiti di profondità. */
object PhoneFiles {
    const val TEMP_DIR = ".JarvisTrasferimenti"

    private fun checked(rel: String): File {
        require(Storage.sanitize(rel) == rel) { "Percorso protetto o non valido" }
        val f = Storage.file(rel)
        require(f.canonicalPath == f.absolutePath && !Files.isSymbolicLink(f.toPath())) { "Collegamento simbolico non supportato" }
        return f
    }

    fun restriction(rel: String): String? = when {
        rel == Storage.TRASH_DIR -> "Apri il Cestino di Jarvis per recuperare o eliminare questi elementi."
        rel == TEMP_DIR -> "Trasferimento di Jarvis in corso."
        rel == "Android/data" || rel.startsWith("Android/data/") || rel == "Android/obb" || rel.startsWith("Android/obb/") ->
            "Accesso limitato da Android: i dati privati delle altre app non sono disponibili."
        Storage.sanitize(rel) != rel -> "Percorso non accessibile."
        runCatching { Storage.file(rel).canonicalPath != Storage.file(rel).absolutePath || Files.isSymbolicLink(Storage.file(rel).toPath()) }.getOrDefault(true) ->
            "Collegamento simbolico non supportato."
        else -> null
    }

    fun list(rel: String, hidden: Boolean = true, query: String = "", sort: Int = 0): List<PhoneNode> {
        val dir = checked(rel)
        require(dir.isDirectory) { "Cartella non più disponibile" }
        val children = dir.listFiles() ?: throw IllegalStateException("Android non consente di leggere questa cartella")
        val nodes = children.asSequence().filter { hidden || !it.name.startsWith('.') }
            .filter { query.isBlank() || it.name.contains(query.trim(), true) }.map { f ->
                val r = Storage.rel(f)
                val denied = restriction(r)
                val isDir = f.isDirectory
                val count = if (isDir && denied == null) runCatching { f.list()?.size }.getOrNull() else null
                PhoneNode(r, isDir, if (isDir) 0 else f.length(), f.lastModified(), count,
                    denied ?: if (!f.canRead()) "Android non consente di leggere questo elemento" else null,
                    denied == null && FileOps.canModifySource(r) && f.parentFile?.canWrite() == true)
            }.toList()
        val within: Comparator<PhoneNode> = when (sort) {
            1 -> compareByDescending<PhoneNode> { it.modified }.thenBy { it.name.lowercase(Locale.ITALY) }
            2 -> compareByDescending<PhoneNode> { it.bytes }.thenBy { it.name.lowercase(Locale.ITALY) }
            else -> compareBy<PhoneNode> { it.name.lowercase(Locale.ITALY) }.thenBy { it.name }
        }
        return nodes.sortedWith(compareByDescending<PhoneNode> { it.directory }.then(within))
    }

    fun validName(name: String): String {
        require(name.isNotBlank() && name == name.trim() && name !in setOf(".", "..")) { "Inserisci un nome valido, senza spazi iniziali o finali" }
        require(name.none { it.code < 32 || it in "\\/:*?\"<>|" }) { "Il nome contiene caratteri non consentiti" }
        require(name.toByteArray(Charsets.UTF_8).size <= 255) { "Nome troppo lungo" }
        return name
    }

    private fun destination(folder: String): File = checked(folder).also {
        require(it.isDirectory && it.canWrite()) { "La cartella destinazione non è scrivibile" }
    }
    private fun child(folder: String, name: String) = if (folder.isEmpty()) name else "$folder/$name"
    private fun source(rel: String, mutable: Boolean = false): File = checked(rel).also {
        require(rel.isNotEmpty() && it.exists()) { "Elemento non più disponibile: $rel" }
        require(!mutable || FileOps.canModifySource(rel)) { "Questa cartella principale è protetta: aprila per modificare i suoi file" }
    }

    /** Controlla anche i discendenti senza seguire collegamenti o attraversare aree protette. */
    private fun inspect(root: File, checkActive: () -> Unit = {}): Long {
        var size = 0L
        val stack = ArrayDeque<File>(); stack.add(root)
        while (stack.isNotEmpty()) {
            checkActive()
            val f = stack.removeLast()
            checked(Storage.rel(f))
            if (f.isDirectory) {
                val children = f.listFiles() ?: throw IllegalStateException("Cartella non leggibile: ${f.name}")
                children.forEach { stack.add(it) }
            } else {
                require(f.isFile && f.canRead()) { "File non leggibile: ${f.name}" }
                size += f.length()
            }
        }
        return size
    }

    fun mkdirPlan(folder: String, name: String): Plan {
        destination(folder)
        val rel = child(folder, validName(name)); checked(rel)
        require(!Storage.file(rel).exists()) { "Esiste già un elemento con questo nome" }
        return Plan(listOf(Op.Mkdir(rel)), emptyList())
    }

    fun renamePlan(rel: String, name: String): Plan {
        val src = source(rel, true)
        val target = child(rel.substringBeforeLast('/', ""), validName(name))
        checked(target)
        if (target == rel) return Plan(emptyList(), emptyList())
        require(!Storage.file(target).exists()) { "Esiste già un elemento con questo nome" }
        if (src.isDirectory) inspect(src)
        return Plan(listOf(Op.Move(rel, target)), emptyList())
    }

    fun trashPlan(paths: List<String>): Plan = Plan(roots(paths).map { rel ->
        val src = source(rel, true)
        Op.Trash(rel, inspect(src))
    }, emptyList())

    private fun roots(paths: List<String>): List<String> {
        val result = ArrayList<String>()
        paths.distinct().sortedWith(compareBy<String> { it.count { ch -> ch == '/' } }.thenBy { it }).forEach { p ->
            source(p)
            if (result.none { p.startsWith("$it/") }) result.add(p)
        }
        return result
    }

    private fun unique(folder: String, name: String, directory: Boolean, reserved: MutableSet<String>): String {
        val ext = if (!directory && name.lastIndexOf('.') > 0) "." + name.substringAfterLast('.') else ""
        val base = name.removeSuffix(ext)
        var n = 0
        while (true) {
            val marker = if (n == 0) "" else " ($n)"
            val fitted = Storage.fitFileName(base + ext, marker.toByteArray(Charsets.UTF_8).size)
            val candidate = child(folder, fitted.removeSuffix(ext) + marker + ext)
            checked(candidate)
            if (!Storage.file(candidate).exists() && reserved.add(candidate)) return candidate
            n++
        }
    }

    fun movePlan(paths: List<String>, folder: String): Plan {
        destination(folder)
        val reserved = HashSet<String>()
        return Plan(roots(paths).mapNotNull { rel ->
            val src = source(rel, true)
            require(!src.isDirectory || folder != rel && !folder.startsWith("$rel/")) { "Non puoi spostare una cartella dentro sé stessa" }
            if (rel.substringBeforeLast('/', "") == folder) null
            else {
                if (src.isDirectory) inspect(src)
                Op.Move(rel, unique(folder, src.name, src.isDirectory, reserved))
            }
        }, emptyList())
    }

    /** Copia in una zona interna esclusa dall'indice, poi pubblica senza sovrascrivere. */
    fun paste(c: Context, clipboard: FileClipboard, folder: String, checkActive: () -> Unit = {}): BrowserResult = synchronized(FileOps) {
        destination(folder)
        if (clipboard.cut) {
            val plan = movePlan(clipboard.paths, folder)
            val r = FileOps.execute(c, plan, "Spostamento manuale")
            return@synchronized BrowserResult(r, clipboard.paths.filter { it.substringBeforeLast('/', "") != folder && Storage.file(it).exists() })
        }
        val journal = JSONArray()
        val remaining = ArrayList<String>(); val errors = ArrayList<String>(); val published = ArrayList<File>()
        var done = 0
        var stagingPrepared = false
        try {
            val reserved = HashSet<String>()
            for (rel in roots(clipboard.paths)) {
                checkActive()
                var stage: File? = null
                try {
                    val src = source(rel)
                    require(!src.isDirectory || folder != rel && !folder.startsWith("$rel/")) { "Non puoi copiare una cartella dentro sé stessa" }
                    inspect(src, checkActive)
                    val target = unique(folder, src.name, src.isDirectory, reserved)
                    val temp = Storage.file(TEMP_DIR)
                    require(temp.canonicalPath == temp.absolutePath && !Files.isSymbolicLink(temp.toPath())) { "Area temporanea non valida" }
                    require(temp.isDirectory || temp.mkdir()) { "Impossibile preparare la copia" }
                    if (!stagingPrepared) {
                        // Rimuove solo vecchie copie parziali di Jarvis dopo un arresto del processo.
                        temp.listFiles().orEmpty().filter { runCatching { UUID.fromString(it.name) }.isSuccess }.forEach {
                            checkActive()
                            require(!Files.isSymbolicLink(it.toPath()) && it.canonicalPath == it.absolutePath) { "Copia temporanea non valida" }
                            require(it.deleteRecursively()) { "Impossibile ripulire una copia interrotta" }
                        }
                        stagingPrepared = true
                    }
                    stage = File(temp, UUID.randomUUID().toString())
                    copyTree(src, stage, checkActive)
                    checkActive()
                    Files.move(stage.toPath(), Storage.file(target).toPath())
                    journal.put(JSONObject().put("t", "copy").put("p", target))
                    published.add(Storage.file(target)); done++
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { remaining.add(rel); errors.add("${rel.substringAfterLast('/')}: ${e.message}") }
                finally { stage?.takeIf { it.exists() }?.deleteRecursively() }
            }
        } finally {
            Undo.save(c, journal, "Copia manuale")
            Storage.scanMedia(c, published)
            if (published.isNotEmpty()) FileIndex.invalidate()
            Storage.file(TEMP_DIR).takeIf { it.list()?.isEmpty() == true }?.delete()
        }
        BrowserResult(ExecResult(done, remaining.size, 0, errors.take(20)), remaining)
    }

    private fun copyTree(src: File, target: File, checkActive: () -> Unit) {
        val stack = ArrayDeque<Pair<File, File>>(); stack.add(src to target)
        val directories = ArrayList<Pair<File, File>>()
        while (stack.isNotEmpty()) {
            checkActive()
            val (from, to) = stack.removeLast()
            checked(Storage.rel(from))
            if (from.isDirectory) {
                require(to.mkdir()) { "Cartella non copiata" }
                directories.add(from to to)
                (from.listFiles() ?: throw IllegalStateException("Cartella non leggibile")).forEach { stack.add(it to File(to, it.name)) }
            } else {
                val expected = from.length(); val modified = from.lastModified()
                from.inputStream().use { input -> to.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) { checkActive(); val read = input.read(buffer); if (read < 0) break; output.write(buffer, 0, read) }
                } }
                require(to.length() == expected && from.length() == expected && from.lastModified() == modified) { "Il file è cambiato durante la copia" }
                to.setLastModified(modified)
            }
        }
        directories.asReversed().forEach { (from, to) -> to.setLastModified(from.lastModified()) }
    }
}
