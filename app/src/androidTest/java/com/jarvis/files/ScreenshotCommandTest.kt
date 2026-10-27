package com.jarvis.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.ai.*
import com.jarvis.files.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenshotCommandTest {
    private val root = "JarvisScreenshotCommands"
    private val folder = "$root/Pictures/Screenshots"
    private fun entries() = FileIndex.ensure().filter { it.rel.startsWith("$root/") }
    private fun paths(r: BrainReply) = r.plan!!.ops.filterIsInstance<Op.Trash>().map { it.path }.toSet()

    @Before fun setup() {
        TestUtil.grantAll()
        Storage.file(root).deleteRecursively()
        TestUtil.write("$folder/shot.png", ByteArray(100))
        TestUtil.write("$folder/Sub/child.jpg", ByteArray(200), 40L * 86_400_000)
        TestUtil.write("$folder/keep.pdf", ByteArray(30))
        TestUtil.write("$root/Pictures/Screenshots2/photo.png", ByteArray(50))
        TestUtil.write("$root/Download/Screenshot_2026.png", ByteArray(80))
        TestUtil.write("$root/Download/photo.png", ByteArray(70))
        Storage.scanAll()
    }
    @After fun cleanup() { Storage.file(root).deleteRecursively(); FileIndex.invalidate() }

    @Test fun screenshotsAreIdentifiedByFolderOrFileNameNeverEveryImage() {
        listOf("cancella tutti gli screenshot", "elimina gli screenshots", "Puoi cancellare tutte le schermate").forEach { request ->
            val reply = FileCommands.resolve(request, entries())!!
            assertEquals(setOf("$folder/shot.png", "$folder/Sub/child.jpg", "$root/Download/Screenshot_2026.png"), paths(reply))
            assertTrue(reply.questions.isEmpty())
            assertTrue(paths(reply).all { Storage.file(it).exists() })
        }
    }

    @Test fun explicitScreenshotFolderKeepsOtherTypesAndNeighbors() {
        val reply = FileCommands.resolve("Cancella tutti gli screenshot nella cartella '$folder'", entries())!!
        assertEquals(setOf("$folder/shot.png", "$folder/Sub/child.jpg"), paths(reply))
        assertFalse(paths(reply).contains("$folder/keep.pdf"))
        assertTrue(Storage.file("$root/Pictures/Screenshots2/photo.png").exists())
    }

    @Test fun explicitAllFilesInFolderIncludesAllTypesButNeverNeighbors() {
        val reply = FileCommands.resolve("Cancella tutti i file nella cartella '$folder'", entries())!!
        assertEquals(setOf("$folder/shot.png", "$folder/Sub/child.jpg", "$folder/keep.pdf"), paths(reply))
        assertTrue(paths(reply).all { Storage.file(it).exists() })
    }

    @Test fun screenshotSearchAgeAndUnsupportedExclusionsKeepScope() {
        val search = FileCommands.resolve("Mostrami gli screenshot", entries())!!
        assertNull(search.plan); assertEquals(3, search.fileResults!!.size)
        val old = FileCommands.resolve("Elimina gli screenshot più vecchi di 30 giorni", entries())!!
        assertEquals(setOf("$folder/Sub/child.jpg"), paths(old))
        listOf("Cancella gli screenshot tranne shot.png", "Cancella gli screenshot e i PDF",
            "Non cancellare gli screenshot", "Cancella gli screenshot del 2025").forEach {
            assertNull(it, FileCommands.resolve(it, entries()))
        }
    }

    @Test fun affirmativeChoiceOnlyReplaysTheOriginalRequest() {
        val original = "Cancella gli screenshot in $folder"
        val reply = ChoiceCommands.resolve(FollowUpChoice(original, "Sì", listOf("Sì", "No")), entries())!!
        assertEquals(paths(FileCommands.resolve(original, entries())!!), paths(reply))
        assertTrue(paths(reply).all { Storage.file(it).exists() })
    }

    @Test fun negativeChoiceNeedsNeitherModelNorFileIndex() = runBlocking {
        FileIndex.invalidate()
        val p = Prefs(TestUtil.ctx); val oldMode = p.aiMode; val oldPath = p.localModelPath
        try {
            p.aiMode = AiMode.LOCAL; p.localModelPath = ""
            val (reply, engine) = Brain.ask(TestUtil.ctx, listOf(ChatMsg("user", "No")),
                choice = FollowUpChoice("Cancella gli screenshot", "No", listOf("Sì", "No")))
            assertNull(reply.plan); assertTrue(reply.reply.contains("annullata")); assertTrue(engine.startsWith("Comando locale"))
            assertEquals(0L, FileIndex.scannedAt)
            assertTrue(Storage.file("$folder/shot.png").exists())
        } finally { p.aiMode = oldMode; p.localModelPath = oldPath }
    }

    @Test fun unsupportedOrUnboundYesNeverInventsABroaderDeletion() {
        listOf(null, "Cancella gli screenshot tranne shot.png", "Cancella gli screenshot e i PDF").forEach { original ->
            val reply = ChoiceCommands.resolve(FollowUpChoice(original, "Sì", listOf("Sì", "No")), entries())!!
            assertNull(reply.plan); assertTrue(reply.questions.isEmpty())
            assertTrue(reply.reply.contains("Nessun file"))
        }
    }

    @Test fun questionSourceIsBoundByAppNotByModelSuppliedRequest() {
        val original = "Cancella gli screenshot in $folder"
        val raw = JSONObject().put("reply", "Quali file confermi?")
            .put("questions", org.json.JSONArray().put(JSONObject()
                .put("q", "Confermi la cancellazione di tutti i file in Download?")
                .put("request", "Cancella gli APK").put("options", org.json.JSONArray(listOf("Sì", "No"))))).toString()
        val question = Brain.validateReply(raw, original).questions.single()
        assertEquals(original, question.request)
        val reply = ChoiceCommands.resolve(FollowUpChoice(question.request, "Sì", question.options), entries())!!
        assertEquals(setOf("$folder/shot.png", "$folder/Sub/child.jpg"), paths(reply))
    }

    @Test fun noScreenshotResultsCannotProduceAnInventedPlan() {
        val reply = FileCommands.resolve("Cancella gli screenshot", entries().filter { it.name == "photo.png" })!!
        assertNull(reply.plan); assertTrue(reply.reply.contains("Non ho trovato"))
    }
}
