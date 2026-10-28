package com.jarvis.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.ai.*
import com.jarvis.files.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationContextTest {
    private val root = "JarvisConversationContext"
    private val a = "$root/Reports"
    private val b = "$root/Other"
    private fun ask(text: String, context: ConversationContext? = null) = ContextCommands.resolve(text, context)!!
    @Before fun setup() {
        TestUtil.grantAll(); Storage.file(root).deleteRecursively()
        TestUtil.write("$a/Bolletta.pdf", "bill-a".toByteArray())
        TestUtil.write("$a/Invoice.pdf", "invoice".toByteArray())
        TestUtil.write("$a/Sub/Nested.pdf", "nested".toByteArray())
        TestUtil.write("$a/keep.txt", "keep".toByteArray())
        TestUtil.write("$b/Bolletta.pdf", "bill-b".toByteArray())
        Storage.scanAll()
    }
    @After fun cleanup() { Storage.file(root).deleteRecursively(); FileIndex.invalidate() }

    @Test fun oneFolderThenInsideFileSearchNeedsNoRepeatedPathOrModel() = runBlocking {
        val folder = ask("Trova la cartella '$a'")
        assertEquals(listOf(a), folder.folderResults!!.paths)
        val (reply, engine) = Brain.ask(TestUtil.ctx, listOf(ChatMsg("user", "dentro cercami il file Bolletta.pdf")),
            previousFolders = folder.folderResults, conversation = folder.conversation)
        assertEquals(listOf("$a/Bolletta.pdf"), reply.fileResults!!.map { it.rel })
        assertEquals(a, reply.conversation!!.scope)
        assertTrue(engine.startsWith("Comando locale")); assertNull(reply.plan)
    }

    @Test fun multipleFoldersAskOnceAndResumePendingSearchOnSelection() {
        val folders = ask("Trova le cartelle in '$root'")
        assertEquals(setOf(a, b), folders.folderResults!!.paths.toSet())
        val question = ask("dentro cerca i PDF", folders.conversation)
        assertNull(question.fileResults)
        assertEquals(setOf(a, b), question.questions.single().options.toSet())
        val result = ask("In quale cartella?\nScelgo: $a", question.conversation)
        assertEquals(setOf("$a/Bolletta.pdf", "$a/Invoice.pdf", "$a/Sub/Nested.pdf"), result.fileResults!!.map { it.rel }.toSet())
        assertEquals(a, result.conversation!!.scope)
        assertNull(result.conversation!!.pending)
    }

    @Test fun ordinalAndUniqueFolderNameCanAnswerTheQuestion() {
        val ctx = ConversationContext(root, listOf(b, a))
        val q = ask("in quella cartella cerca il file Bolletta.pdf", ctx)
        assertEquals(listOf("$a/Bolletta.pdf"), ask("Reports", q.conversation).fileResults!!.map { it.rel })
        assertEquals(listOf("$b/Bolletta.pdf"), ask("la prima", q.conversation).fileResults!!.map { it.rel })
    }

    @Test fun duplicateFolderNamesCannotPickAnArbitraryPath() {
        val ctx = ConversationContext(root, listOf("$root/One/Reports", "$root/Two/Reports"), pending = "dentro cerca i PDF")
        val reply = ask("Reports", ctx)
        assertNull(reply.fileResults); assertEquals(2, reply.questions.single().options.size)
    }

    @Test fun askForFileNameOnceAndKeepFolderAcrossSeveralSearches() {
        val first = ask("dentro cercami questo file", ConversationContext(scope = a))
        assertTrue(first.conversation!!.awaitingFileName)
        val named = ask("Bolletta.pdf", first.conversation)
        assertEquals(listOf("$a/Bolletta.pdf"), named.fileResults!!.map { it.rel })
        val next = ask("cerca keep.txt", named.conversation)
        assertEquals(listOf("$a/keep.txt"), next.fileResults!!.map { it.rel })
        val repeated = ask("mostrameli", next.conversation)
        assertEquals(next.fileResults, repeated.fileResults)
    }

    @Test fun explicitFolderChangeOverridesPreviousFolderAndNoContextDoesNotGuess() {
        val reply = ask("cerca il file Bolletta.pdf in '$b'", ConversationContext(scope = a))
        assertEquals(listOf("$b/Bolletta.pdf"), reply.fileResults!!.map { it.rel })
        assertEquals(b, reply.conversation!!.scope)
        assertNull(ask("dentro cerca i PDF").fileResults)
    }

    @Test fun deletionPronounUsesOnlyPriorResultsWithPreviewAndActualUndo() {
        val found = ask("cerca il file Bolletta.pdf in '$a'")
        val deletion = ask("eliminali", found.conversation)
        assertEquals(listOf("$a/Bolletta.pdf"), deletion.plan!!.ops.filterIsInstance<Op.Trash>().map { it.path })
        assertTrue(Storage.file("$a/Bolletta.pdf").exists()) // Only preview so far.
        val executed = FileOps.execute(TestUtil.ctx, deletion.plan!!, "ContextTest")
        assertEquals(1, executed.done); assertEquals(0, executed.failed)
        assertTrue(Storage.file("$b/Bolletta.pdf").exists()); assertTrue(Storage.file("$a/Invoice.pdf").exists())
        val restored = Undo.undo(TestUtil.ctx)
        assertEquals(1, restored.first); assertEquals("bill-a", Storage.file("$a/Bolletta.pdf").readText())
    }

    @Test fun contextMutationCannotAccidentallyDeleteEveryPdfOnPhone() {
        val result = ask("dentro cancella i PDF", ConversationContext(scope = a))
        assertEquals(setOf("$a/Bolletta.pdf", "$a/Invoice.pdf", "$a/Sub/Nested.pdf"), result.plan!!.ops.filterIsInstance<Op.Trash>().map { it.path }.toSet())
        assertTrue(Storage.file("$b/Bolletta.pdf").exists())
        assertTrue(Storage.file("$a/keep.txt").exists())
    }

    @Test fun removedResultOrFolderIsNotInventedAgain() {
        val found = ask("cerca Bolletta.pdf in '$a'")
        Storage.file("$a/Bolletta.pdf").delete()
        val deletion = ask("eliminali", found.conversation)
        assertNull(deletion.plan)
        Storage.file(b).deleteRecursively()
        assertNull(ask("dentro cerca i PDF", ConversationContext(scope = b)).fileResults)
    }

    @Test fun contextualRenamePreservesNewNameCapitalizationAndExtension() {
        val reply = ask("Rinomina il file \"$a/Bolletta.pdf\" in \"Bolletta Marzo.PDF\"", ConversationContext(scope = a))
        val r = FileOps.execute(TestUtil.ctx, reply.plan!!, "ContextRename")
        assertEquals(1, r.done); assertTrue(Storage.file("$a/Bolletta Marzo.PDF").isFile)
        assertTrue(Storage.file("$b/Bolletta.pdf").isFile)
        Undo.undo(TestUtil.ctx)
    }

    @Test fun unsupportedExclusionRetainsScopeWithoutBroaderResultsOrPlan() {
        val reply = ask("dentro cerca i PDF tranne Invoice.pdf", ConversationContext(scope = a))
        assertNull(reply.fileResults); assertNull(reply.plan)
        assertEquals(a, reply.conversation!!.scope)
    }

    @Test fun emptyAndOrdinaryFolderResultsRemainDifferentAndAllPathsAccessible() {
        val listed = ask("trovami le cartelle")
        assertFalse(listed.folderResults!!.emptyOnly)
        assertTrue(listed.folderResults!!.paths.contains(root))
        assertTrue(listed.folderResults!!.paths.all { Storage.file(it).isDirectory && PhoneFiles.restriction(it) == null })
    }
}
