package com.jarvis.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.ai.Brain
import com.jarvis.files.ai.extractJson
import com.jarvis.files.automation.Tasks
import com.jarvis.files.core.Cleaner
import com.jarvis.files.core.FileOps
import com.jarvis.files.core.Op
import com.jarvis.files.core.Storage
import com.jarvis.files.core.Trash
import com.jarvis.files.core.Undo
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** Operazioni reali sui file della memoria del dispositivo. */
@RunWith(AndroidJUnit4::class)
class FileOpsTest {
    companion object {
        @BeforeClass @JvmStatic fun perms() = TestUtil.grantAll()
    }

    private val ctx get() = TestUtil.ctx

    @Before fun setUp() {
        Storage.file("JarvisTest").deleteRecursively()
        TestUtil.write("JarvisTest/Download/bolletta_enel_marzo.pdf", "enel".toByteArray())
        TestUtil.write("JarvisTest/Download/fattura_tim.pdf", "tim".toByteArray())
        TestUtil.write("JarvisTest/Download/IMG_001.jpg", ByteArray(2000) { 7 })
        TestUtil.write("JarvisTest/Download/vecchio.apk", "xx".toByteArray())
        TestUtil.write("JarvisTest/Download/note.txt", "appunti".toByteArray())
        Storage.file("JarvisTest/Download/PDF").mkdirs()
        Storage.scanAll()
    }

    @After fun tearDown() {
        Storage.file("JarvisTest").deleteRecursively()
    }

    @Test fun planExecuteAndUndo() {
        val actions = JSONArray(
            """[
            {"type":"move_where","where":{"folder":"JarvisTest/Download","ext":["pdf"]},"to":"JarvisTest/Download/PDF"},
            {"type":"rename","path":"JarvisTest/Download/IMG_001.jpg","new_name":"Foto mare"},
            {"type":"delete","path":"JarvisTest/Download/vecchio.apk"},
            {"type":"mkdir","path":"JarvisTest/Documenti/Note"},
            {"type":"move","from":"JarvisTest/Download/note.txt","to":"JarvisTest/Documenti/Note","new_name":"Appunti.txt"},
            {"type":"delete","path":"../../system"},
            {"type":"delete","path":"Android/data/com.whatsapp"},
            {"type":"delete","path":"Download"}
            ]"""
        )
        val plan = FileOps.buildPlan(actions)
        TestUtil.report("FileOps", "Piano: ${plan.summary()} · avvisi: ${plan.warnings}")
        assertEquals("2 pdf + rename + note = 4 spostamenti", 4, plan.moves)
        assertEquals(1, plan.trashes)
        assertTrue(plan.ops.none { it is Op.Trash && (it.path == "Download" || it.path.contains("system") || it.path.startsWith("Android")) })

        val r = FileOps.execute(ctx, plan, "Test")
        TestUtil.report("FileOps", "Eseguito: ${r.done} ok, ${r.failed} falliti ${r.errors}")
        assertEquals(0, r.failed)
        assertTrue(Storage.file("JarvisTest/Download/PDF/bolletta_enel_marzo.pdf").exists())
        assertTrue(Storage.file("JarvisTest/Download/PDF/fattura_tim.pdf").exists())
        assertTrue("estensione aggiunta", Storage.file("JarvisTest/Download/Foto mare.jpg").exists())
        assertTrue(Storage.file("JarvisTest/Documenti/Note/Appunti.txt").exists())
        assertFalse(Storage.file("JarvisTest/Download/vecchio.apk").exists())
        assertTrue(Trash.items(ctx).any { it.original == "JarvisTest/Download/vecchio.apk" })

        val (ok, ko) = Undo.undo(ctx)
        TestUtil.report("FileOps", "Annulla: $ok ripristinati, $ko falliti")
        assertEquals(0, ko)
        listOf("bolletta_enel_marzo.pdf", "fattura_tim.pdf", "IMG_001.jpg", "vecchio.apk", "note.txt").forEach {
            assertTrue("ripristinato $it", Storage.file("JarvisTest/Download/$it").exists())
        }
        assertFalse("cartella creata rimossa", Storage.file("JarvisTest/Documenti/Note").exists())
        assertEquals("contenuto intatto", "enel", Storage.file("JarvisTest/Download/bolletta_enel_marzo.pdf").readText())
        TestUtil.report("FileOps", "OK sposta/rinomina/cestino/annulla")
    }

    @Test fun collisionsAndPattern() {
        TestUtil.write("JarvisTest/Download/PDF/fattura_tim.pdf", "esistente".toByteArray())
        TestUtil.write("JarvisTest/Foto/a.jpg", ByteArray(10), 3 * 86_400_000L)
        TestUtil.write("JarvisTest/Foto/b.jpg", ByteArray(10), 2 * 86_400_000L)
        Storage.scanAll()
        val plan = FileOps.buildPlan(JSONArray("""[
            {"type":"move","from":"JarvisTest/Download/fattura_tim.pdf","to":"JarvisTest/Download/PDF"},
            {"type":"rename_where","where":{"folder":"JarvisTest/Foto"},"pattern":"{data} Vacanza {n}"}
        ]"""))
        val targets = plan.ops.filterIsInstance<Op.Move>().map { it.to }
        TestUtil.report("FileOps", "Destinazioni: $targets")
        assertTrue(targets.contains("JarvisTest/Download/PDF/fattura_tim (1).pdf"))
        assertTrue(targets.any { Regex("JarvisTest/Foto/\\d{4}-\\d{2}-\\d{2} Vacanza 001\\.jpg").matches(it) })
        FileOps.execute(ctx, plan, "Test")
        assertEquals("esistente", Storage.file("JarvisTest/Download/PDF/fattura_tim.pdf").readText())
        assertEquals("tim", Storage.file("JarvisTest/Download/PDF/fattura_tim (1).pdf").readText())
        TestUtil.report("FileOps", "OK nomi duplicati e rinomina con schema")
    }

    @Test fun moveWithFullTargetPath() {
        // Come fa a volte Gemma: "to" è il percorso completo del file, non la cartella
        val plan = FileOps.buildPlan(JSONArray("""[
            {"type":"move","from":"JarvisTest/Download/fattura_tim.pdf","to":"JarvisTest/Download/PDF/fattura_tim.pdf"},
            {"type":"move","from":"JarvisTest/Download/bolletta_enel_marzo.pdf","to":"JarvisTest/Download/PDF/Bolletta Enel marzo.pdf"}
        ]"""))
        TestUtil.report("FileOps", "Destinazione completa: ${plan.ops.map { it.label }}")
        assertEquals(0, plan.mkdirs)
        assertEquals(2, plan.moves)
        FileOps.execute(ctx, plan, "Test")
        assertTrue(Storage.file("JarvisTest/Download/PDF/fattura_tim.pdf").isFile)
        assertTrue(Storage.file("JarvisTest/Download/PDF/Bolletta Enel marzo.pdf").isFile)
        TestUtil.report("FileOps", "OK destinazione come percorso completo")
    }

    @Test fun cleanSpuriousSearches() {
        val r = Brain.clean(
            com.jarvis.files.ai.BrainReply(
                "ok", questions = listOf(com.jarvis.files.ai.FollowUp("Che nome?", listOf("A", "B"))),
                plan = com.jarvis.files.core.Plan(listOf(Op.Mkdir("X")), emptyList()),
                photoSearch = "N/A",
                fileQuery = com.jarvis.files.ai.FileQuery(listOf("log"), emptyList(), "System", "2024-01-01", "2024-06-01"),
            ),
            "Perché il telefono è lento?",
        )
        assertEquals(null, r.plan)
        assertEquals(null, r.photoSearch)
        assertEquals(null, r.fileQuery)
        val r2 = Brain.clean(
            com.jarvis.files.ai.BrainReply("ok", fileQuery = com.jarvis.files.ai.FileQuery(listOf("bolletta"), listOf("pdf"), "Inesistente", "2024-01-01", null)),
            "Trova la bolletta della luce",
        )
        assertEquals(listOf("bolletta"), r2.fileQuery!!.keywords)
        assertEquals(null, r2.fileQuery!!.fromIso)
        assertEquals(null, r2.fileQuery!!.folder)
        TestUtil.report("AI", "OK pulizia di ricerche e date non richieste")
    }

    @Test fun trashRestore() {
        val id = Trash.put(ctx, "JarvisTest/Download/note.txt")
        assertNotNull(id)
        assertFalse(Storage.file("JarvisTest/Download/note.txt").exists())
        assertTrue(Trash.restore(ctx, id!!))
        assertEquals("appunti", Storage.file("JarvisTest/Download/note.txt").readText())
        TestUtil.report("Cestino", "OK sposta e ripristina")
    }

    @Test fun duplicatesAndSearch() {
        val data = ByteArray(80_000) { (it % 251).toByte() }
        TestUtil.write("JarvisTest/Dup/uno.jpg", data)
        TestUtil.write("JarvisTest/Dup/copia di uno.jpg", data)
        TestUtil.write("JarvisTest/Dup/diverso.jpg", data.copyOf().also { it[100] = 1 })
        Storage.scanAll()
        val groups = Cleaner.findDuplicates().filter { g -> g.files.any { it.rel.startsWith("JarvisTest/Dup") } }
        TestUtil.report("Doppioni", "Gruppi: ${groups.map { g -> g.files.map { it.name } }}")
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].files.size)

        val hits = Brain.searchFiles(Brain.simpleQuery("trova la bolletta enel"))
        TestUtil.report("Cerca", "bolletta enel → ${hits.map { it.rel }}")
        assertTrue(hits.first().name == "bolletta_enel_marzo.pdf")
    }

    @Test fun parseModelReply() {
        val raw = """Certo signore.
```json
{"reply":"Sposto i PDF.","questions":[{"q":"Che nome?","options":["Data davanti","Nome davanti"]}],
 "actions":[{"type":"move_where","where":{"folder":"JarvisTest/Download","ext":["pdf"]},"to":"JarvisTest/Download/PDF"}],
 "file_search":{"keywords":["bolletta"],"ext":["pdf"]}}
```"""
        assertNotNull(extractJson(raw))
        val r = Brain.parse(raw)
        assertEquals("Sposto i PDF.", r.reply)
        assertEquals(2, r.questions[0].options.size)
        assertEquals(2, r.plan!!.moves)
        assertEquals(listOf("pdf"), r.fileQuery!!.ext)
        assertEquals("testo libero", Brain.parse("testo libero").reply)
        TestUtil.report("AI", "OK interpretazione risposta del modello (JSON, domande, piano)")
    }

    @Test fun sortDownloadRule() {
        val old = 2 * 3_600_000L
        TestUtil.write("Download/jt_doc.pdf", "x".toByteArray(), old)
        TestUtil.write("Download/jt_song.mp3", "x".toByteArray(), old)
        TestUtil.write("Download/jt_new.pdf", "x".toByteArray()) // troppo recente: non va toccato
        Storage.file("Download/PDF").mkdirs()
        try {
            val plan = Tasks.sortDownloadPlan()
            val moves = plan.ops.filterIsInstance<Op.Move>().filter { it.from.contains("jt_") }.associate { it.from to it.to }
            TestUtil.report("Riordino", "Download: $moves")
            assertEquals("usa la cartella PDF esistente", "Download/PDF/jt_doc.pdf", moves["Download/jt_doc.pdf"])
            assertEquals("Download/Audio/jt_song.mp3", moves["Download/jt_song.mp3"])
            assertFalse(moves.containsKey("Download/jt_new.pdf"))
        } finally {
            listOf("Download/jt_doc.pdf", "Download/jt_song.mp3", "Download/jt_new.pdf").forEach { Storage.file(it).delete() }
            Storage.file("Download/PDF").delete()
        }
    }
}
