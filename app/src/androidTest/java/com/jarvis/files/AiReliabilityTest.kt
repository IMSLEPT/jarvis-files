package com.jarvis.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.ai.*
import com.jarvis.files.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiReliabilityTest {
    private val root = "JarvisAiReliability"
    private val question = "quali sono i file che occupano maggiormente la memoria"
    @Before fun setup() {
        TestUtil.grantAll()
        TestUtil.write("$root/old-video.mp4", ByteArray(8192), 20L * 86_400_000)
        TestUtil.write("$root/new-small.docx", ByteArray(10))
        TestUtil.write("$root/archive.zip", ByteArray(4096))
        Storage.scanAll()
    }
    @After fun cleanup() { Storage.file(root).deleteRecursively(); FileIndex.invalidate() }
    private fun entries() = FileIndex.entries.filter { it.rel.startsWith("$root/") }

    @Test fun screenshotQuestionRanksActualBytesInsteadOfRecentFiles() {
        listOf(question, "Quali sono i file più pesanti?", "Mostrami i file più grandi", "Quali file occupano più spazio sul telefono?").forEach { q ->
            val reply = FileFacts.resolve(q, entries())!!
            assertEquals(q, listOf("old-video.mp4", "archive.zip", "new-small.docx"), reply.fileResults!!.map { it.name })
            assertNull(reply.plan)
            assertEquals(12298L, reply.fileResults!!.sumOf { it.size })
        }
        assertEquals(2, FileFacts.resolve("mostra i 2 file più grandi", entries())!!.fileResults!!.size)
    }

    @Test fun rankingDoesNotDiscardFiltersOrAcceptDestructiveCommands() {
        listOf("Cancella i file più grandi", "Quali sono i file più grandi in Download", "Mostra i file più grandi tranne i video", "Mostra i file più grandi del 2025").forEach {
            assertNull(it, FileFacts.resolve(it, entries()))
        }
    }

    @Test fun screenshotQuestionAndShowThemNeedNoConfiguredModel() = runBlocking {
        val p = Prefs(TestUtil.ctx)
        val oldMode = p.aiMode; val oldPath = p.localModelPath
        try {
            p.aiMode = AiMode.LOCAL; p.localModelPath = ""
            val (reply, engine) = Brain.ask(TestUtil.ctx, listOf(ChatMsg("user", question)))
            assertTrue(engine.startsWith("Dati locali"))
            assertNotNull(reply.fileResults)
            assertEquals(reply.fileResults!!.map { it.size }.sortedDescending(), reply.fileResults!!.map { it.size })
            val (follow, _) = Brain.ask(TestUtil.ctx, listOf(ChatMsg("user", "mostrameli")), reply.fileResults)
            assertEquals(reply.fileResults!!.map { it.rel }, follow.fileResults!!.map { it.rel })
            assertNull(follow.plan)
            val (recovered, _) = Brain.ask(TestUtil.ctx, listOf(ChatMsg("user", question), ChatMsg("assistant", "7".repeat(2000)), ChatMsg("user", "mostrameli")))
            assertEquals(reply.fileResults!!.map { it.rel }, recovered.fileResults!!.map { it.rel })
        } finally { p.aiMode = oldMode; p.localModelPath = oldPath }
    }

    @Test fun followUpKeepsOrderAndDropsFilesThatHaveDisappeared() {
        val original = FileFacts.resolve(question, entries())!!.fileResults!!
        Storage.file(original.first().rel).delete()
        Storage.scanAll()
        val reply = FileFacts.followUp(original, entries())
        assertEquals(original.drop(1).map { it.rel }, reply.fileResults!!.map { it.rel })
        assertNull(reply.plan)
    }

    @Test fun validJsonCannotHideDegenerateOrOversizedReplies() {
        listOf("Ecco i file " + "7".repeat(3000) + " Hungary", "Cartella Download. ".repeat(30), "testo ".repeat(600), "<start_of_turn>model").forEach {
            try { Brain.validateReply(org.json.JSONObject().put("reply", it).toString(), question); fail("Risposta anomala accettata") }
            catch (_: AiUnavailable) { }
        }
        assertEquals("Nessun file è stato modificato.", Brain.validateReply("""{"reply":"Nessun file è stato modificato."}""", "Ciao").reply)
    }

    @Test fun streamingStopsRepeatedCharactersBeforeFinishing() = runBlocking {
        var cancelled = false; var reachedEnd = false
        val chunks = flow {
            emit("{\"reply\":\"Ecco ")
            repeat(100) { emit("77777777") }
            reachedEnd = true
        }
        try { ResponseGuard.collect(chunks, { cancelled = true }); fail("Generazione non fermata") }
        catch (_: AiUnavailable) { }
        assertTrue(cancelled); assertFalse(reachedEnd)
        assertEquals("{\"reply\":\"Ciao\"}", ResponseGuard.collect(flowOf("{\"reply\":", "\"Ciao\"}"), { fail("Cancellazione su risposta valida") }))
    }

    @Test fun timeoutCancelsTheBackendAndDoesNotReturnPartialText() = runBlocking {
        var cancelled = false
        try {
            ResponseGuard.collect(flow { emit("parziale"); awaitCancellation() }, { cancelled = true }, timeoutMs = 100)
            fail("Timeout ignorato")
        } catch (e: AiUnavailable) { assertTrue(e.message!!.contains("troppo tempo")) }
        assertTrue(cancelled)
    }

    @Test fun userCancellationRemainsCancellation() = runBlocking {
        val started = CompletableDeferred<Unit>(); var cancelled = false
        val job = launch {
            ResponseGuard.collect(flow { started.complete(Unit); awaitCancellation() }, { cancelled = true })
            fail("Risposta dopo Stop")
        }
        started.await(); job.cancelAndJoin()
        assertTrue(job.isCancelled); assertTrue(cancelled)
    }
}
