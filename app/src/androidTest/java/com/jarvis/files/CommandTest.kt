package com.jarvis.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.ai.*
import com.jarvis.files.core.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommandTest {
    private val root = "JarvisCommands"
    private fun entries() = FileIndex.ensure().filter { it.rel.startsWith("$root/") }
    @Before fun setup() {
        TestUtil.grantAll()
        Storage.file(root).deleteRecursively()
        TestUtil.write("$root/Download/a.apk", "apk one".toByteArray(), 45L * 86_400_000)
        TestUtil.write("$root/Documents/b.APK", "apk two".toByteArray())
        TestUtil.write("$root/Download/keep.pdf", "keep".toByteArray())
        TestUtil.write("$root/Download/Sub/c.xapk", "bundle".toByteArray())
        TestUtil.write("$root/Download2/d.apk", "neighbor".toByteArray())
        Storage.scanAll()
    }
    @After fun cleanup() { Storage.file(root).deleteRecursively(); FileIndex.invalidate() }

    @Test fun exactScreenshotCommandFindsApksOutsideDownloadAndNeverExecutes() {
        val r = FileCommands.resolve("cancella gli apk", entries())!!
        assertEquals(4, r.plan!!.trashes)
        assertTrue(r.plan!!.ops.filterIsInstance<Op.Trash>().all { Storage.file(it.path).exists() })
        assertTrue(r.reply.contains("app installate"))
        assertFalse(r.plan!!.ops.any { it is Op.Trash && it.path.endsWith("keep.pdf") })
    }
    @Test fun folderScopeIncludesChildrenButNotNeighborFolders() {
        val r = FileCommands.resolve("Cancella gli APK in JarvisCommands/Download", entries())!!
        assertEquals(setOf("$root/Download/a.apk", "$root/Download/Sub/c.xapk"), r.plan!!.ops.filterIsInstance<Op.Trash>().map { it.path }.toSet())
    }
    @Test fun ageFilterDoesNotDeleteRecentApks() {
        val r = FileCommands.resolve("Elimina gli APK più vecchi di 30 giorni", entries())!!
        assertEquals(listOf("$root/Download/a.apk"), r.plan!!.ops.filterIsInstance<Op.Trash>().map { it.path })
    }
    @Test fun politeItalianCommandsKeepTheSameScope() {
        listOf("Puoi cancellare gli APK", "Per favore, cancellami gli APK", "Mi cancelli gli APK", "Potresti eliminare gli APK").forEach {
            assertEquals(it, 4, FileCommands.resolve(it, entries())!!.plan!!.trashes)
        }
    }
    @Test fun exclusionsAndCompoundRequestsAreNeverSilentlyIgnored() {
        listOf("Cancella gli APK tranne a.apk", "Cancella gli APK e i PDF", "Cancella gli APK dopo averli copiati", "Non cancellare gli APK", "Cancella gli APK del 2025").forEach {
            assertNull(it, FileCommands.resolve(it, entries()))
        }
    }
    @Test fun plainSearchCannotGenerateDeletionPlan() {
        val r = FileCommands.resolve("Trova gli APK", entries())!!
        assertNull(r.plan)
        assertEquals(4, r.fileResults!!.size)
    }
    @Test fun noResultsAreComputedFromRealIndex() {
        val r = FileCommands.resolve("Cancella i video", entries())!!
        assertNull(r.plan)
        assertTrue(r.reply.contains("Non ho trovato"))
    }
    @Test fun vagueDeletionOffersWorkingChoices() {
        val r = FileCommands.resolve("cancella", entries())!!
        assertNull(r.plan)
        val choice = r.questions.single().options.first()
        val selected = FileCommands.resolve("${r.questions.single().question}\nScelgo: $choice", entries())!!
        assertEquals(4, selected.plan!!.trashes)
    }
    @Test fun invalidAiSuccessOrNotFoundCannotMasqueradeAsCompletedCommand() {
        listOf("Fatto", "Ho not found", "Ho cancellato gli APK").forEach { reply ->
            try { Brain.validateReply("""{"reply":"$reply"}""", "Cancella gli APK tranne a.apk"); fail("Risposta senza piano accettata") }
            catch (_: AiUnavailable) { }
        }
    }
    @Test fun rejectsMalformedReplyAndUnrequestedActions() {
        listOf("""{"reply":null}""", """{"reply":42}""", """{"actions":[]}""", "testo non JSON").forEach {
            try { Brain.validateReply(it, "Ciao"); fail("Formato errato accettato") } catch (_: AiUnavailable) { }
        }
        try { Brain.validateReply("""{"reply":"Ecco","actions":[{"type":"delete","path":"$root/Download/a.apk"}]}""", "Trova gli APK"); fail("Eliminazione non richiesta") }
        catch (_: AiUnavailable) { }
    }
    @Test fun explicitRenameUsesRealPathAndKeepsExtension() {
        val r = FileCommands.resolve("Rinomina \"$root/Download/keep.pdf\" in \"Bolletta\"", entries())!!
        assertEquals(Op.Move("$root/Download/keep.pdf", "$root/Download/Bolletta.pdf"), r.plan!!.ops.single())
        assertTrue(Storage.file("$root/Download/keep.pdf").exists())
    }
    @Test fun keywordSearchWorksWithoutLlm() {
        TestUtil.write("$root/Documents/Bollette_2026.pdf", "bill".toByteArray()); Storage.scanAll()
        val r = FileCommands.resolve("Trova i PDF delle bollette", entries())!!
        assertEquals(listOf("Bollette_2026.pdf"), r.fileResults!!.map { it.name })
    }
    @Test fun commandExecutionAndUndoPreserveUnselectedFiles() {
        val r = FileCommands.resolve("Cancella gli APK più vecchi di 30 giorni", entries())!!
        assertEquals(1, FileOps.execute(TestUtil.ctx, r.plan!!, "CommandTest").done)
        assertFalse(Storage.file("$root/Download/a.apk").exists())
        assertTrue(Storage.file("$root/Documents/b.APK").exists())
        assertEquals(1 to 0, Undo.undo(TestUtil.ctx))
        assertEquals("apk one", Storage.file("$root/Download/a.apk").readText())
    }
}
