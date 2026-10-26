package com.jarvis.files.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TrashItem(val id: String, val original: String, val deletedAt: Long, val size: Long, val isDir: Boolean)

/**
 * Cestino: i file "cancellati" vengono spostati in /.JarvisCestino (stesso volume: istantaneo)
 * e restano recuperabili per [Prefs.trashDays] giorni.
 */
object Trash {
    private val lock = Any()
    private val dir: File get() = Storage.file(Storage.TRASH_DIR).apply {
        if (!exists()) mkdirs()
        val nomedia = File(this, ".nomedia")
        if (!nomedia.exists()) runCatching { nomedia.createNewFile() }
    }
    private fun indexFile(c: Context) = File(c.filesDir, "trash_index.json")

    private fun load(c: Context): JSONArray = runCatching { JSONArray(indexFile(c).readText()) }.getOrDefault(JSONArray())
    private fun store(c: Context, a: JSONArray) = indexFile(c).writeText(a.toString())

    fun items(c: Context): List<TrashItem> = synchronized(lock) {
        val a = load(c)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            TrashItem(o.getString("id"), o.getString("orig"), o.getLong("at"), o.optLong("size"), o.optBoolean("dir"))
        }.sortedByDescending { it.deletedAt }
    }

    /** Sposta nel cestino; restituisce l'id oppure null. */
    fun put(c: Context, rel: String): String? = synchronized(lock) {
        val clean = Storage.sanitize(rel)?.takeIf { it.isNotEmpty() } ?: return null
        val src = Storage.file(clean)
        if (!src.exists()) return null
        val id = "${System.currentTimeMillis()}_${(1000..9999).random()}_${src.name}"
        val size = if (src.isDirectory) FileOps.dirSize(src) else src.length()
        val isDir = src.isDirectory
        val dst = File(dir, id)
        if (!FileOps.moveFile(src, dst)) return null
        val a = load(c)
        a.put(JSONObject().put("id", id).put("orig", rel).put("at", System.currentTimeMillis()).put("size", size).put("dir", isDir))
        store(c, a)
        Stats.addFreed(c, size)
        return id
    }

    fun restore(c: Context, id: String): Boolean = synchronized(lock) {
        val a = load(c)
        val idx = (0 until a.length()).firstOrNull { a.getJSONObject(it).getString("id") == id } ?: return false
        val o = a.getJSONObject(idx)
        var target = Storage.file(o.getString("orig"))
        var n = 1
        val original = target
        while (target.exists()) {
            target = File(original.parentFile, "${original.nameWithoutExtension} (ripristinato $n)" + if (original.extension.isEmpty()) "" else ".${original.extension}")
            n++
        }
        target.parentFile?.mkdirs()
        val ok = FileOps.moveFile(File(dir, id), target)
        if (ok) {
            a.remove(idx)
            store(c, a)
            Storage.scanMedia(c, listOf(target))
            FileIndex.invalidate()
        }
        return ok
    }

    fun deleteForever(c: Context, id: String): Boolean = synchronized(lock) {
        val a = load(c)
        val idx = (0 until a.length()).firstOrNull { a.getJSONObject(it).getString("id") == id } ?: return false
        if (!File(dir, id).deleteRecursively()) return false
        a.remove(idx)
        store(c, a)
        return true
    }

    fun emptyAll(c: Context) = synchronized(lock) {
        items(c).forEach { deleteForever(c, it.id) }
    }

    /** Elimina definitivamente gli elementi più vecchi di [days] giorni. */
    fun purgeOld(c: Context, days: Int) = synchronized(lock) {
        val limit = System.currentTimeMillis() - days * 86_400_000L
        items(c).filter { it.deletedAt < limit }.forEach { deleteForever(c, it.id) }
    }

    fun totalSize(c: Context) = items(c).sumOf { it.size }
}

