package com.jarvis.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.ai.*
import com.jarvis.files.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrganizeRegressionTest {
    private val root = "JarvisOrganizeRegression"
    @Before fun setup() {
        TestUtil.grantAll(); Storage.file(root).deleteRecursively()
        TestUtil.write("$root/a.pdf", "a".toByteArray())
        TestUtil.write("$root/b.png", "b".toByteArray())
        TestUtil.write("$root/Sub/c.pdf", "c".toByteArray())
        TestUtil.write("$root/PDF/d.pdf", "d".toByteArray())
        Storage.scanAll()
    }
    @After fun cleanup() { Storage.file(root).deleteRecursively(); FileIndex.invalidate() }
    private fun resolve(text: String, context: ConversationContext? = null) = OrganizeCommands.resolve(listOf(ChatMsg("user", text)), context, null, FileIndex.entries)!!
    @Test fun allThenTypeProducesPreviewOnceWithoutModelAndPreservesStructure() = runBlocking {
        val first = resolve("Riordina tutte le cartelle e file per bene nella cartella '$root'")
        assertEquals(root, first.conversation!!.organize!!.scope)
        val all = resolve("tutti", first.conversation)
        assertEquals("tutti", all.conversation!!.organize!!.kind)
        val (reply, engine) = Brain.ask(TestUtil.ctx, listOf(ChatMsg("user", "Come preferisci riordinare?\nScelgo: Per tipo di file")), conversation = all.conversation)
        assertTrue(engine.contains("riordino")); assertTrue(reply.questions.isEmpty())
        val moves = reply.plan!!.ops.filterIsInstance<Op.Move>()
        assertEquals(setOf("$root/a.pdf", "$root/b.png", "$root/Sub/c.pdf"), moves.map { it.from }.toSet())
        assertTrue(moves.any { it.to == "$root/Sub/PDF/c.pdf" })
        assertTrue(moves.none { "PDF/PDF" in it.to })
        assertTrue(Storage.file("$root/a.pdf").exists())
        val result = FileOps.execute(TestUtil.ctx, reply.plan!!, "OrganizeTest")
        assertEquals(0, result.failed); assertTrue(Storage.file("$root/PDF/a.pdf").exists())
        Undo.undo(TestUtil.ctx)
        assertTrue(Storage.file("$root/a.pdf").exists()); assertTrue(Storage.file("$root/Sub/c.pdf").exists())
    }
    @Test fun topLevelOnlyAndCancellationDoNotMoveNestedFiles() {
        val reply = resolve("Riordina per tipo nella cartella '$root'")
        assertEquals(2, reply.plan!!.moves)
        assertFalse(reply.plan!!.ops.filterIsInstance<Op.Move>().any { "/Sub/" in it.from })
        val question = resolve("Riordina nella cartella '$root'")
        assertNull(resolve("annulla", question.conversation).plan)
    }
    @Test fun explicitTypeIsNotLostByFollowingChoice() {
        val first = resolve("Riordina i PDF nella cartella '$root'")
        val result = resolve("Per tipo di file", first.conversation)
        assertEquals(listOf("$root/a.pdf"), result.plan!!.ops.filterIsInstance<Op.Move>().map { it.from })
    }
    @Test fun missingScopeAsksOnceAndRetainsChosenMode() {
        val first = resolve("Riordina tutti i file per tipo")
        assertNull(first.plan)
        val reply = resolve(root, first.conversation)
        assertEquals(2, reply.plan!!.moves)
    }
    @Test fun filenamesAndUnrecognizedRestrictionsNeverBroadenThePlan() {
        val first = resolve("Riordina nella cartella '$root'")
        for (text in listOf("Riordina solo a.pdf nella cartella '$root'", "Riordina i file vecchi nella cartella '$root'", "Riordina i PDF e video nella cartella '$root'")) {
            assertNull(OrganizeCommands.resolve(listOf(ChatMsg("user", text)), first.conversation, null, FileIndex.entries))
        }
    }
    @Test fun unrelatedRequestDoesNotContinuePendingOrganizer() {
        val first = resolve("Riordina nella cartella '$root'")
        assertNull(OrganizeCommands.resolve(listOf(ChatMsg("user", "Trova gli APK")), first.conversation, null, FileIndex.entries))
    }
}
