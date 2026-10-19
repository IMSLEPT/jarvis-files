package com.jarvis.files.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

/**
 * Cartella segreta: i file vengono spostati nella memoria privata dell'app (invisibile a galleria,
 * file manager e altre app, cifrata da Android). Si apre solo dopo l'impronta.
 */
object Vault {
    fun dir(c: Context) = File(c.filesDir, "vault").apply { mkdirs() }

    fun list(c: Context): List<File> = dir(c).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    private fun unique(d: File, name: String): File {
        var f = File(d, name)
        var n = 1
        while (f.exists()) { f = File(d, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.', "")}".trimEnd('.')); n++ }
        return f
    }

    /** Sposta nella cassaforte un file della memoria (percorso relativo). */
    fun importPath(c: Context, rel: String): Boolean {
        val src = Storage.file(rel)
        if (!src.isFile) return false
        val dst = unique(dir(c), src.name)
        val ok = runCatching {
            src.inputStream().use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
            dst.length() == src.length() && src.delete()
        }.getOrDefault(false)
        if (!ok) dst.delete() else Storage.scanMedia(c, listOf(src))
        return ok
    }

    /** Importa da un Uri del selettore di sistema; prova a cancellare l'originale. */
    fun importUri(c: Context, uri: Uri): Boolean {
        val name = c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "file_${System.currentTimeMillis()}"
        val dst = unique(dir(c), name)
        val copied = runCatching {
            c.contentResolver.openInputStream(uri)!!.use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
            true
        }.getOrDefault(false)
        if (!copied) { dst.delete(); return false }
        runCatching { DocumentsContract.deleteDocument(c.contentResolver, uri) }
        return true
    }

    /** Riporta il file nella cartella Download/Jarvis Esportati. */
    fun export(c: Context, f: File): File? {
        val outDir = Storage.file("Download/Jarvis Esportati").apply { mkdirs() }
        val dst = unique(outDir, f.name)
        return if (FileOps.moveFile(f, dst)) dst.also { Storage.scanMedia(c, listOf(it)) } else null
    }
}
