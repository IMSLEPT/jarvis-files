package com.jarvis.files.photos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PhotoRow(
    val path: String,
    val modified: Long,
    val labels: String,
    val caption: String,
    val place: String,
    val lat: Double?,
    val lon: Double?,
    val faces: Int,
    val dark: Float,
    val blur: Float,
)

data class FaceRow(val id: Long, val path: String, val emb: FloatArray, val box: IntArray, val personId: Long, val cluster: Long)

data class Person(val id: Long, val name: String, val count: Int, val coverFaceId: Long)

/** Database locale delle foto analizzate (resta solo sul telefono). */
class PhotoDb private constructor(c: Context) : SQLiteOpenHelper(c, "photos.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE photos(
                path TEXT PRIMARY KEY, modified INTEGER, size INTEGER, labels TEXT DEFAULT '',
                caption TEXT DEFAULT '', place TEXT DEFAULT '', lat REAL, lon REAL, taken INTEGER,
                faces INTEGER DEFAULT 0, dark REAL DEFAULT 0, blur REAL DEFAULT 0, level INTEGER DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE faces(
                id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT, emb BLOB, l INTEGER, t INTEGER, r INTEGER, b INTEGER,
                person_id INTEGER DEFAULT 0, cluster INTEGER DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX faces_path ON faces(path)")
        db.execSQL("CREATE INDEX faces_person ON faces(person_id)")
        db.execSQL("CREATE TABLE persons(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, centroid BLOB)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---------- foto ----------

    fun known(): Map<String, Pair<Long, Int>> {
        val out = HashMap<String, Pair<Long, Int>>()
        readableDatabase.rawQuery("SELECT path, modified, level FROM photos", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getLong(1) to c.getInt(2)
        }
        return out
    }

    fun upsertPhoto(
        path: String, modified: Long, size: Long, labels: String, caption: String?, place: String,
        lat: Double?, lon: Double?, faces: Int, dark: Float, blur: Float, level: Int,
    ) {
        val cv = ContentValues().apply {
            put("path", path); put("modified", modified); put("size", size); put("labels", labels)
            if (caption != null) put("caption", caption)
            put("place", place); put("lat", lat); put("lon", lon); put("faces", faces)
            put("dark", dark); put("blur", blur); put("level", level)
        }
        val db = writableDatabase
        if (db.update("photos", cv, "path=?", arrayOf(path)) == 0) db.insert("photos", null, cv)
    }

    fun setCaption(path: String, caption: String, level: Int) {
        writableDatabase.update("photos", ContentValues().apply { put("caption", caption); put("level", level) }, "path=?", arrayOf(path))
    }

    fun setPlace(path: String, place: String) {
        writableDatabase.update("photos", ContentValues().apply { put("place", place) }, "path=?", arrayOf(path))
    }

    fun removePhotos(paths: Collection<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            paths.forEach {
                db.delete("photos", "path=?", arrayOf(it))
                db.delete("faces", "path=?", arrayOf(it))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun allPhotos(): List<PhotoRow> {
        val out = ArrayList<PhotoRow>()
        readableDatabase.rawQuery(
            "SELECT path, modified, labels, caption, place, lat, lon, faces, dark, blur FROM photos", null
        ).use { c ->
            while (c.moveToNext()) out.add(
                PhotoRow(
                    c.getString(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: "",
                    if (c.isNull(5)) null else c.getDouble(5), if (c.isNull(6)) null else c.getDouble(6),
                    c.getInt(7), c.getFloat(8), c.getFloat(9),
                )
            )
        }
        return out
    }

    fun photosNeedingPlace(limit: Int): List<Triple<String, Double, Double>> {
        val out = ArrayList<Triple<String, Double, Double>>()
        readableDatabase.rawQuery(
            "SELECT path, lat, lon FROM photos WHERE lat IS NOT NULL AND (place IS NULL OR place='') LIMIT $limit", null
        ).use { c -> while (c.moveToNext()) out.add(Triple(c.getString(0), c.getDouble(1), c.getDouble(2))) }
        return out
    }

    fun photosNeedingCaption(limit: Int): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT path FROM photos WHERE level < 2 ORDER BY modified DESC LIMIT $limit", null)
            .use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out
    }

    fun counts(): Triple<Int, Int, Int> {
        fun q(sql: String) = readableDatabase.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return Triple(q("SELECT COUNT(*) FROM photos"), q("SELECT COUNT(*) FROM photos WHERE level >= 2"), q("SELECT COUNT(*) FROM faces"))
    }

    // ---------- volti ----------

    fun addFace(path: String, emb: FloatArray, box: IntArray, personId: Long): Long {
        val cv = ContentValues().apply {
            put("path", path); put("emb", toBlob(emb)); put("l", box[0]); put("t", box[1]); put("r", box[2]); put("b", box[3])
            put("person_id", personId)
        }
        return writableDatabase.insert("faces", null, cv)
    }

    fun faces(where: String = "1", args: Array<String>? = null): List<FaceRow> {
        val out = ArrayList<FaceRow>()
        readableDatabase.rawQuery("SELECT id, path, emb, l, t, r, b, person_id, cluster FROM faces WHERE $where", args).use { c ->
            while (c.moveToNext()) out.add(
                FaceRow(
                    c.getLong(0), c.getString(1), fromBlob(c.getBlob(2)),
                    intArrayOf(c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6)), c.getLong(7), c.getLong(8)
                )
            )
        }
        return out
    }

    fun setFacePerson(faceIds: Collection<Long>, personId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            faceIds.forEach { db.update("faces", ContentValues().apply { put("person_id", personId) }, "id=?", arrayOf(it.toString())) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun setClusters(assign: Map<Long, Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            assign.forEach { (id, cl) -> db.update("faces", ContentValues().apply { put("cluster", cl) }, "id=?", arrayOf(id.toString())) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    // ---------- persone ----------

    fun persons(): List<Person> {
        val out = ArrayList<Person>()
        readableDatabase.rawQuery(
            """SELECT p.id, p.name, COUNT(f.id), MIN(f.id) FROM persons p LEFT JOIN faces f ON f.person_id = p.id
               GROUP BY p.id ORDER BY COUNT(f.id) DESC""", null
        ).use { c -> while (c.moveToNext()) out.add(Person(c.getLong(0), c.getString(1), c.getInt(2), c.getLong(3))) }
        return out
    }

    fun personByName(name: String): Long? =
        readableDatabase.rawQuery("SELECT id FROM persons WHERE name = ? COLLATE NOCASE", arrayOf(name.trim()))
            .use { if (it.moveToFirst()) it.getLong(0) else null }

    fun createPerson(name: String): Long =
        personByName(name) ?: writableDatabase.insert("persons", null, ContentValues().apply { put("name", name.trim()) })

    fun renamePerson(id: Long, name: String) {
        writableDatabase.update("persons", ContentValues().apply { put("name", name.trim()) }, "id=?", arrayOf(id.toString()))
    }

    fun deletePerson(id: Long) {
        writableDatabase.update("faces", ContentValues().apply { put("person_id", 0) }, "person_id=?", arrayOf(id.toString()))
        writableDatabase.delete("persons", "id=?", arrayOf(id.toString()))
    }

    fun setCentroid(id: Long, c: FloatArray) {
        writableDatabase.update("persons", ContentValues().apply { put("centroid", toBlob(c)) }, "id=?", arrayOf(id.toString()))
    }

    fun centroids(): Map<Long, FloatArray> {
        val out = HashMap<Long, FloatArray>()
        readableDatabase.rawQuery("SELECT id, centroid FROM persons WHERE centroid IS NOT NULL", null).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = fromBlob(c.getBlob(1))
        }
        return out
    }

    fun photosOfPerson(id: Long): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT DISTINCT path FROM faces WHERE person_id = ?", arrayOf(id.toString()))
            .use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out
    }

    companion object {
        @Volatile private var inst: PhotoDb? = null
        fun get(c: Context): PhotoDb = inst ?: synchronized(this) { inst ?: PhotoDb(c.applicationContext).also { inst = it } }

        fun toBlob(f: FloatArray): ByteArray {
            val bb = ByteBuffer.allocate(f.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            f.forEach { bb.putFloat(it) }
            return bb.array()
        }

        fun fromBlob(b: ByteArray): FloatArray {
            val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(b.size / 4) { bb.getFloat() }
        }
    }
}
