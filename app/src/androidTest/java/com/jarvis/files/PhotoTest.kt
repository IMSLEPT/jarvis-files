package com.jarvis.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.core.Storage
import com.jarvis.files.photos.BitmapLoader
import com.jarvis.files.photos.FaceEmbedder
import com.jarvis.files.photos.People
import com.jarvis.files.photos.PhotoDb
import com.jarvis.files.photos.PhotoIndexer
import com.jarvis.files.photos.PhotoQuery
import com.jarvis.files.photos.PhotoSearch
import com.jarvis.files.photos.VisionPipeline
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Riconoscimento reale di volti e oggetti su foto vere (immagini d'esempio di face_recognition, licenza MIT). */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PhotoTest {
    companion object {
        private val names = listOf("obama.jpg", "obama2.jpg", "biden.jpg", "two_people.jpg")

        @BeforeClass @JvmStatic fun setUp() {
            TestUtil.grantAll()
            Storage.file("JarvisTest/Pictures").deleteRecursively()
            names.forEach { TestUtil.write("JarvisTest/Pictures/$it", TestUtil.asset(it)) }
            Storage.scanAll()
        }

        @AfterClass @JvmStatic fun cleanup() {
            Storage.file("JarvisTest").deleteRecursively()
        }
    }

    @Test fun a_facesAndEmbeddings() {
        val emb = HashMap<String, List<FloatArray>>()
        VisionPipeline(TestUtil.ctx).use { vp ->
            names.forEach { n ->
                val bmp = BitmapLoader.load(Storage.file("JarvisTest/Pictures/$n"), 1024)!!
                val t0 = System.currentTimeMillis()
                val r = vp.analyze(bmp)
                TestUtil.report("Visione", "$n: ${r.faces.size} volti, etichette=${r.labels.take(6).map { "${it.first} ${"%.2f".format(it.second)}" }}, " +
                    "luminosità=${"%.0f".format(r.dark)}, nitidezza=${"%.0f".format(r.blur)}, ${System.currentTimeMillis() - t0} ms")
                emb[n] = r.faces.map { it.emb }
            }
        }
        assertEquals(1, emb["obama.jpg"]!!.size)
        assertEquals(1, emb["obama2.jpg"]!!.size)
        assertEquals(1, emb["biden.jpg"]!!.size)
        assertEquals(2, emb["two_people.jpg"]!!.size)
        val o1 = emb["obama.jpg"]!![0]; val o2 = emb["obama2.jpg"]!![0]; val b = emb["biden.jpg"]!![0]
        val same = FaceEmbedder.cosine(o1, o2)
        val diff1 = FaceEmbedder.cosine(o1, b)
        val diff2 = FaceEmbedder.cosine(o2, b)
        val twoVsObama = emb["two_people.jpg"]!!.map { FaceEmbedder.cosine(it, o1) }
        val twoVsBiden = emb["two_people.jpg"]!!.map { FaceEmbedder.cosine(it, b) }
        TestUtil.report("Volti", "Obama-Obama2=${"%.3f".format(same)}  Obama-Biden=${"%.3f".format(diff1)}  Obama2-Biden=${"%.3f".format(diff2)}")
        TestUtil.report("Volti", "Foto con 2 persone vs Obama=${twoVsObama.map { "%.3f".format(it) }} vs Biden=${twoVsBiden.map { "%.3f".format(it) }}")
        assertTrue("stessa persona più simile di persone diverse", same > diff1 + 0.15f && same > diff2 + 0.15f)
    }

    @Test fun a2_stopRequest() = runBlocking {
        // Con la richiesta di stop attiva l'analisi non elabora nessuna foto
        com.jarvis.files.photos.PhotoDb.get(TestUtil.ctx).removePhotos(listOf("JarvisTest/Pictures/obama.jpg"))
        val n = PhotoIndexer.run(TestUtil.ctx, { true }) { }
        TestUtil.report("Stop", "Analisi con stop richiesto: $n foto elaborate · caldo=${com.jarvis.files.core.PowerGuard.isHot(TestUtil.ctx)}")
        assertEquals(0, n)
    }

    @Test fun b_indexNameAndSearch() = runBlocking {
        val ctx = TestUtil.ctx
        val n = PhotoIndexer.run(ctx, { false }) { }
        val db = PhotoDb.get(ctx)
        TestUtil.report("Indice", "Foto analizzate: $n · totale db=${db.counts()}")
        val clusters = People.clusterUnknown(ctx, minSize = 1)
        TestUtil.report("Indice", "Gruppi di volti: ${clusters.map { c -> c.faces.map { it.path.substringAfterLast('/') } }}")
        // Il gruppo che contiene obama.jpg diventa "Obama"
        val obamaCluster = clusters.first { c -> c.faces.any { it.path.endsWith("/obama.jpg") } }
        People.nameFaces(ctx, obamaCluster.faces.map { it.id }, "Obama")
        val bidenCluster = People.clusterUnknown(ctx, minSize = 1).firstOrNull { c -> c.faces.any { it.path.endsWith("/biden.jpg") } }
        bidenCluster?.let { People.nameFaces(ctx, it.faces.map { f -> f.id }, "Biden") }

        val obama = PhotoSearch.run(ctx, PhotoQuery(listOf("Obama"), emptyList(), emptyList(), null, null, null)).map { it.path.substringAfterLast('/') }
        val biden = PhotoSearch.run(ctx, PhotoQuery(listOf("Biden"), emptyList(), emptyList(), null, null, null)).map { it.path.substringAfterLast('/') }
        val both = PhotoSearch.run(ctx, PhotoQuery(listOf("Obama", "Biden"), emptyList(), emptyList(), null, null, null)).map { it.path.substringAfterLast('/') }
        TestUtil.report("Ricerca", "foto di Obama → $obama")
        TestUtil.report("Ricerca", "foto di Biden → $biden")
        TestUtil.report("Ricerca", "foto di Obama e Biden insieme → $both")
        val parsed = PhotoSearch.parse(ctx, "fammi vedere tutte le foto di Obama")
        TestUtil.report("Ricerca", "Interpretazione senza AI di \"tutte le foto di Obama\": persone=${parsed.people}")
        assertTrue(obama.containsAll(listOf("obama.jpg", "obama2.jpg", "two_people.jpg")))
        assertTrue("biden.jpg" !in obama)
        assertTrue(biden.containsAll(listOf("biden.jpg", "two_people.jpg")))
        assertEquals(listOf("two_people.jpg"), both)
        assertEquals(listOf("Obama"), parsed.people)
    }
}
