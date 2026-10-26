package com.jarvis.files.core

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/** Un file indicizzato. [rel] è il percorso relativo alla memoria interna (es. "Download/x.pdf"). */
data class FileEntry(
    val rel: String,
    val size: Long,
    val modified: Long,
) {
    val name: String get() = rel.substringAfterLast('/')
    val folder: String get() = if ('/' in rel) rel.substringBeforeLast('/') else ""
    val ext: String get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val category: FileCategory get() = FileCategory.of(ext)
}

enum class FileCategory(val label: String) {
    IMAGE("Immagini"), VIDEO("Video"), AUDIO("Audio"), DOCUMENT("Documenti"),
    ARCHIVE("Archivi"), APK("App (APK)"), OTHER("Altro");

    companion object {
        private val map = buildMap {
            listOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "dng", "raw").forEach { put(it, IMAGE) }
            listOf("mp4", "mkv", "mov", "3gp", "webm", "avi", "m4v").forEach { put(it, VIDEO) }
            listOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "amr").forEach { put(it, AUDIO) }
            listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "ods", "rtf", "csv", "epub", "md")
                .forEach { put(it, DOCUMENT) }
            listOf("zip", "rar", "7z", "tar", "gz").forEach { put(it, ARCHIVE) }
            listOf("apk", "apks", "xapk").forEach { put(it, APK) }
        }
        fun of(ext: String) = map[ext] ?: OTHER
    }
}

object Storage {
    val root: File get() = Environment.getExternalStorageDirectory()
    const val TRASH_DIR = ".JarvisCestino"

    /** Cartelle che non si scansionano (inaccessibili o interne). */
    private val skipPrefixes = listOf("Android/data", "Android/obb", TRASH_DIR)

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun file(rel: String): File = File(root, rel)

    fun rel(f: File): String = f.absolutePath.removePrefix(root.absolutePath).trimStart('/')

    /** Normalizza un percorso fornito dall'AI: niente "..", niente percorsi assoluti esterni. */
    fun sanitize(path: String): String? {
        var p = path.trim().replace('\\', '/')
        if (p.any { it.code < 32 }) return null
        for (prefix in listOf(root.absolutePath, "/sdcard", "/storage/emulated/0")) {
            if (p == prefix || p.startsWith("$prefix/")) { p = p.removePrefix(prefix); break }
        }
        if (p.startsWith('/') && !path.trim().let { raw -> listOf(root.absolutePath, "/sdcard", "/storage/emulated/0").any { raw == it || raw.startsWith("$it/") } }) return null
        val parts = p.trim('/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) return null
        val clean = parts.joinToString("/")
        if (skipPrefixes.any { clean == it || clean.startsWith("$it/") }) return null
        val target = runCatching { File(root, clean).canonicalFile }.getOrNull() ?: return null
        val base = root.canonicalFile
        if (target != base && !target.path.startsWith(base.path + "/")) return null
        return clean
    }

    fun freeBytes(): Long = StatFs(root.absolutePath).availableBytes
    fun totalBytes(): Long = StatFs(root.absolutePath).totalBytes

    fun scanMedia(context: Context, files: Collection<File>) {
        if (files.isEmpty()) return
        MediaScannerConnection.scanFile(context, files.map { it.absolutePath }.toTypedArray(), null, null)
    }

    fun openFile(context: Context, f: File) {
        if (!f.isFile) { android.widget.Toast.makeText(context, "File non più disponibile", android.widget.Toast.LENGTH_SHORT).show(); return }
        runCatching {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(i, "Apri con").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { android.widget.Toast.makeText(context, "Impossibile aprire il file", android.widget.Toast.LENGTH_SHORT).show() }
    }

    fun shareFiles(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        runCatching {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) })
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(i, "Condividi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { android.widget.Toast.makeText(context, "Impossibile condividere i file", android.widget.Toast.LENGTH_SHORT).show() }
    }

    /** Scansione completa della memoria; aggiorna [FileIndex]. */
    @Synchronized fun scanAll(onProgress: (Int) -> Unit = {}): List<FileEntry> {
        val out = ArrayList<FileEntry>(20000)
        val rootAbs = root.absolutePath
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (c in children) {
                val r = c.absolutePath.removePrefix(rootAbs).trimStart('/')
                if (skipPrefixes.any { r == it || r.startsWith("$it/") }) continue
                if (c.canonicalPath != c.absolutePath && !c.canonicalPath.startsWith(root.canonicalPath + "/")) continue
                if (c.isDirectory) {
                    if (java.nio.file.Files.isSymbolicLink(c.toPath())) continue
                    stack.add(c)
                } else {
                    out.add(FileEntry(r, c.length(), c.lastModified()))
                    if (out.size % 2000 == 0) onProgress(out.size)
                }
            }
            if (out.size > 400_000) break
        }
        FileIndex.update(out)
        return out
    }

    /** Elenco delle cartelle (relative), anche vuote. */
    fun listDirs(maxDepth: Int = 3): List<String> {
        val out = ArrayList<String>()
        fun walk(d: File, depth: Int) {
            if (depth > maxDepth) return
            d.listFiles()?.filter { it.isDirectory }?.forEach {
                val r = rel(it)
                if (skipPrefixes.any { p -> r == p || r.startsWith("$p/") }) return@forEach
                out.add(r)
                walk(it, depth + 1)
            }
        }
        walk(root, 1)
        return out
    }
}

/** Indice in memoria dell'ultima scansione. */
object FileIndex {
    @Volatile var entries: List<FileEntry> = emptyList()
        private set
    @Volatile var scannedAt: Long = 0
        private set

    fun update(list: List<FileEntry>) {
        entries = list
        scannedAt = System.currentTimeMillis()
    }

    fun ensure(maxAgeMs: Long = 10 * 60_000L): List<FileEntry> {
        if (entries.isEmpty() || System.currentTimeMillis() - scannedAt > maxAgeMs) Storage.scanAll()
        return entries
    }

    fun invalidate() { scannedAt = 0 }
}

object Fmt {
    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, 4)
        val v = bytes / 1024.0.pow(exp)
        return String.format(Locale.ITALY, if (v < 10) "%.1f %s" else "%.0f %s", v, units[exp - 1])
    }

    @Synchronized fun date(ms: Long): String = dateFmt.format(Date(ms))
    @Synchronized fun iso(ms: Long): String = isoFmt.format(Date(ms))
}

