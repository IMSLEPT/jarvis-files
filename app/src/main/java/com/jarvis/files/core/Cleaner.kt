package com.jarvis.files.core

import android.content.Context
import android.content.pm.PackageManager
import com.jarvis.files.photos.PhotoDb
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

data class DupGroup(val size: Long, val files: List<FileEntry>) {
    val wasted get() = size * (files.size - 1)
}

data class JunkItem(val rel: String, val size: Long, val reason: String)

data class WaCategory(val key: String, val title: String, val note: String, val files: List<FileEntry>) {
    val size get() = files.sumOf { it.size }
}

/** Ricerche per la pulizia: doppioni, WhatsApp, file grandi/vecchi, screenshot, foto da rivedere, file inutili. */
object Cleaner {

    // ---------- Doppioni ----------

    fun findDuplicates(onProgress: (Int, Int) -> Unit = { _, _ -> }): List<DupGroup> {
        val entries = FileIndex.ensure().filter { it.size >= 50_000 && !it.name.startsWith(".") }
        val bySize = entries.groupBy { it.size }.filter { it.value.size > 1 }
        val candidates = bySize.values.flatten()
        var done = 0
        val groups = ArrayList<DupGroup>()
        for ((size, list) in bySize) {
            val byQuick = list.groupBy { quickHash(Storage.file(it.rel)) }.filter { it.key != null && it.value.size > 1 }
            for ((_, same) in byQuick) {
                // Conferma con hash completo per file fino a 200 MB
                val confirmed = if (size <= 200L * 1024 * 1024) same.groupBy { fullHash(Storage.file(it.rel)) }.filter { it.key != null && it.value.size > 1 }.values
                else listOf(same)
                confirmed.forEach { g -> groups.add(DupGroup(size, g.sortedBy { it.modified })) }
            }
            done += list.size
            onProgress(done, candidates.size)
        }
        return groups.sortedByDescending { it.wasted }
    }

    private fun quickHash(f: File): String? = runCatching {
        val md = MessageDigest.getInstance("MD5")
        RandomAccessFile(f, "r").use { raf ->
            val len = raf.length()
            val buf = ByteArray(65536)
            val n1 = raf.read(buf); if (n1 > 0) md.update(buf, 0, n1)
            if (len > 131072) { raf.seek(len - 65536); val n2 = raf.read(buf); if (n2 > 0) md.update(buf, 0, n2) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun fullHash(f: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = i.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    // ---------- WhatsApp ----------

    private val waRoots = listOf("Android/media/com.whatsapp/WhatsApp/Media", "Android/media/com.whatsapp.w4b/WhatsApp Business/Media", "WhatsApp/Media")

    fun whatsapp(): List<WaCategory> {
        val entries = FileIndex.ensure()
        fun pick(sub: String, sentOnly: Boolean? = null) = entries.filter { e ->
            waRoots.any { r -> e.rel.startsWith("$r/$sub/") } && !e.name.startsWith(".") &&
                when (sentOnly) { true -> e.rel.contains("/Sent/"); false -> !e.rel.contains("/Sent/"); null -> true }
        }
        return listOf(
            WaCategory("voice", "Messaggi vocali", "I vocali già ascoltati restano nelle chat solo come file.", pick("WhatsApp Voice Notes") + pick("WhatsApp Audio")),
            WaCategory("img_sent", "Foto inviate", "Copie delle foto che hai mandato tu: gli originali sono già in galleria.", pick("WhatsApp Images", true)),
            WaCategory("vid_sent", "Video inviati", "Copie dei video che hai mandato: gli originali sono già in galleria.", pick("WhatsApp Video", true)),
            WaCategory("img", "Foto ricevute", "Foto arrivate nelle chat e nei gruppi.", pick("WhatsApp Images", false)),
            WaCategory("vid", "Video ricevuti", "Spesso sono i file più pesanti (video dei gruppi).", pick("WhatsApp Video", false)),
            WaCategory("gif", "GIF e sticker", "Animazioni e adesivi ricevuti.", pick("WhatsApp Animated Gifs") + pick("WhatsApp Stickers")),
            WaCategory("docs", "Documenti ricevuti", "PDF e file ricevuti: controlla prima di eliminarli.", pick("WhatsApp Documents")),
        ).filter { it.files.isNotEmpty() }
    }

    // ---------- Grandi e vecchi ----------

    fun largest(limit: Int = 100) = FileIndex.ensure().sortedByDescending { it.size }.take(limit)

    fun oldUntouched(days: Int = 365, limit: Int = 300): List<FileEntry> {
        val lim = System.currentTimeMillis() - days * 86_400_000L
        return FileIndex.ensure().filter { it.modified < lim && it.size > 1_000_000 && !it.rel.startsWith("DCIM/Camera") }
            .sortedByDescending { it.size }.take(limit)
    }

    // ---------- Screenshot e foto da rivedere ----------

    fun oldScreenshots(days: Int = 30): List<FileEntry> {
        val lim = System.currentTimeMillis() - days * 86_400_000L
        return FileIndex.ensure().filter { it.rel.contains("Screenshots", true) && it.category == FileCategory.IMAGE && it.modified < lim }
            .sortedBy { it.modified }
    }

    /** Usa i dati dell'analisi foto (luminosità e nitidezza). */
    fun questionablePhotos(c: Context): Pair<List<String>, List<String>> {
        val rows = PhotoDb.get(c).allPhotos().filter { !it.path.contains("Screenshots", true) }
        val dark = rows.filter { it.dark in 0.1f..28f }.map { it.path }
        val blurry = rows.filter { it.blur in 0.1f..35f && it.dark > 28f }.map { it.path }
        return dark to blurry
    }

    // ---------- File inutili ----------

    fun junk(c: Context): List<JunkItem> {
        val out = ArrayList<JunkItem>()
        val pm = c.packageManager
        val entries = FileIndex.ensure()
        entries.filter { it.ext == "apk" }.forEach { e ->
            val f = Storage.file(e.rel)
            val info = runCatching { pm.getPackageArchiveInfo(f.absolutePath, 0) }.getOrNull()
            val installed = info?.let { runCatching { pm.getPackageInfo(it.packageName, 0) }.getOrNull() }
            val reason = when {
                info == null -> "File di installazione danneggiato o incompleto"
                installed != null && installed.longVersionCode >= info.longVersionCode -> "App già installata: il file di installazione non serve più"
                else -> null
            }
            if (reason != null) out.add(JunkItem(e.rel, e.size, reason))
        }
        entries.filter { it.ext in setOf("tmp", "temp", "part", "crdownload", "log") || it.name.startsWith(".pending-") }
            .forEach { out.add(JunkItem(it.rel, it.size, "File temporaneo o download incompleto")) }
        listOf("DCIM/.thumbnails", "Pictures/.thumbnails", "Movies/.thumbnails").forEach { d ->
            val f = Storage.file(d)
            if (f.isDirectory) {
                val s = FileOps.dirSize(f)
                if (s > 0) out.add(JunkItem(d, s, "Miniature: il telefono le ricrea da solo"))
            }
        }
        Storage.listDirs(4).filter { d ->
            val f = Storage.file(d)
            // Solo sottocartelle: le cartelle principali (Alarms, Movies…) le ricrea il sistema
            '/' in d && !d.startsWith("Android") && !d.contains("/.") && f.list()?.isEmpty() == true
        }.forEach { out.add(JunkItem(it, 0, "Cartella vuota")) }
        return out
    }

    @Suppress("unused")
    private fun isInstalled(pm: PackageManager, pkg: String) = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
}
