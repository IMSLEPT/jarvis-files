package com.jarvis.files.photos

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.jarvis.files.ai.AiRouter
import com.jarvis.files.ai.LocalAi
import com.jarvis.files.core.FileCategory
import com.jarvis.files.core.FileIndex
import com.jarvis.files.core.Prefs
import com.jarvis.files.core.Stats
import com.jarvis.files.core.Storage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Analizza le foto presenti sul telefono (fotocamera, WhatsApp, Download, screenshot...).
 * Livello 1: oggetti, volti, luogo GPS, qualità. Livello 2: descrizione dettagliata con Gemma (visione).
 */
object PhotoIndexer {
    private const val TAG = "PhotoIndexer"
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

    data class Progress(val done: Int, val total: Int, val phase: String)

    /** Foto candidate: immagini reali, non miniature né sticker. */
    fun candidates(): List<com.jarvis.files.core.FileEntry> = FileIndex.ensure(30 * 60_000L).filter {
        it.category == FileCategory.IMAGE && it.ext in IMAGE_EXT && it.size > 25_000 &&
            !it.rel.contains("/.thumbnails", true) && !it.rel.contains("Stickers", true) &&
            !it.rel.contains("/.", false)
    }

    /**
     * Esegue un passaggio di indicizzazione. [shouldStop] permette al Worker di fermarsi.
     * Restituisce quante foto ha analizzato.
     */
    suspend fun run(c: Context, shouldStop: () -> Boolean, onProgress: suspend (Progress) -> Unit): Int {
        val db = PhotoDb.get(c)
        val all = candidates()
        val known = db.known()
        // Rimuovi dal database le foto che non esistono più
        val existing = all.mapTo(HashSet()) { it.rel }
        val gone = known.keys.filter { it !in existing }
        if (gone.isNotEmpty()) db.removePhotos(gone)

        val todo = all.filter { e -> known[e.rel]?.first != e.modified }.sortedByDescending { it.modified }
        var done = 0
        if (todo.isNotEmpty()) {
            val centroids = db.centroids().toMutableMap()
            VisionPipeline(c).use { vp ->
                for (e in todo) {
                    if (shouldStop()) break
                    val f = Storage.file(e.rel)
                    val bmp = BitmapLoader.load(f, 1024) ?: continue
                    try {
                        val r = vp.analyze(bmp)
                        val (lat, lon) = gps(f)
                        db.removePhotos(listOf(e.rel)) // ricrea i volti se la foto è cambiata
                        r.faces.forEach { face ->
                            val best = centroids.maxByOrNull { FaceEmbedder.cosine(it.value, face.emb) }
                            val pid = if (best != null && FaceEmbedder.cosine(best.value, face.emb) >= People.ASSIGN_THRESHOLD) best.key else 0L
                            db.addFace(e.rel, face.emb, face.box, pid)
                        }
                        val labels = r.labels.joinToString(",") { it.first.lowercase(Locale.ROOT) }
                        db.upsertPhoto(e.rel, e.modified, e.size, labels, null, "", lat, lon, r.faces.size, r.dark, r.blur, 1)
                        done++
                    } catch (t: Throwable) {
                        Log.w(TAG, "Analisi fallita ${e.rel}", t)
                    } finally {
                        bmp.recycle()
                    }
                    if (done % 10 == 0) onProgress(Progress(done, todo.size, "Volti e oggetti"))
                }
            }
            Stats.addPhotosIndexed(c, done)
        }
        if (!shouldStop()) geocode(c, shouldStop)
        if (!shouldStop() && Prefs(c).photoDeepCaptions && AiRouter.localReady(c)) deepCaptions(c, shouldStop, onProgress)
        return done
    }

    private fun gps(f: File): Pair<Double?, Double?> = runCatching {
        val ll = ExifInterface(f.absolutePath).latLong
        if (ll != null && !(ll[0] == 0.0 && ll[1] == 0.0)) ll[0] to ll[1] else null to null
    }.getOrDefault(null to null)

    /** Converte le coordinate GPS in nome del luogo (serve internet). */
    private suspend fun geocode(c: Context, shouldStop: () -> Boolean) {
        if (!Geocoder.isPresent() || !AiRouter.isOnline(c)) return
        val db = PhotoDb.get(c)
        val geocoder = Geocoder(c, Locale.ITALY)
        val cache = HashMap<String, String>()
        for ((path, lat, lon) in db.photosNeedingPlace(500)) {
            if (shouldStop()) return
            val key = String.format(Locale.ROOT, "%.3f,%.3f", lat, lon)
            val place = cache[key] ?: reverse(geocoder, lat, lon).also { cache[key] = it }
            db.setPlace(path, place.ifEmpty { "-" })
        }
    }

    private suspend fun reverse(g: Geocoder, lat: Double, lon: Double): String {
        fun fmt(a: android.location.Address?): String = a?.let {
            listOfNotNull(it.featureName?.takeIf { f -> f.any(Char::isLetter) }, it.subLocality, it.locality, it.subAdminArea, it.adminArea, it.countryName)
                .distinct().joinToString(", ")
        } ?: ""
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                g.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) { if (cont.isActive) cont.resume(fmt(addresses.firstOrNull())) }
                    override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume("") }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { fmt(g.getFromLocation(lat, lon, 1)?.firstOrNull()) }.getOrDefault("")
        }
    }

    private const val CAPTION_PROMPT =
        "Descrivi questa foto in italiano in massimo 30 parole, come elenco di parole chiave separate da virgole: " +
            "persone (quante, adulti/bambini/neonato), animali (specie e colore), oggetti, ambiente e luogo " +
            "(montagna, lago, mare, spiaggia, città, casa, ristorante, neve...), monumenti o luoghi famosi se riconoscibili, " +
            "tipo di immagine (screenshot, documento, scontrino, ricevuta, meme), testo leggibile importante."

    /** Descrizioni dettagliate con Gemma: lente, quindi a piccoli blocchi. */
    private suspend fun deepCaptions(c: Context, shouldStop: () -> Boolean, onProgress: suspend (Progress) -> Unit) {
        val db = PhotoDb.get(c)
        val list = db.photosNeedingCaption(400)
        var n = 0
        for (path in list) {
            if (shouldStop()) return
            val f = Storage.file(path)
            // Gemma lavora meglio con immagini ridotte: crea un JPEG temporaneo da 768 px
            val bmp = BitmapLoader.load(f, 512) ?: continue
            val tmp = File(c.cacheDir, "caption.jpg")
            tmp.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
            val cap = runCatching { LocalAi.describeImage(c, tmp, CAPTION_PROMPT) }.getOrNull()
            if (cap == null) {
                if (!LocalAi.visionAvailable) return // il modello non vede le immagini: inutile continuare
                continue
            }
            db.setCaption(path, cap.trim().take(400), 2)
            n++
            if (n % 3 == 0) onProgress(Progress(n, list.size, "Descrizioni dettagliate"))
        }
    }
}
