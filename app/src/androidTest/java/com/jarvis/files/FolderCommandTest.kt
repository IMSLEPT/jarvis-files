package com.jarvis.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.ai.*
import com.jarvis.files.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderCommandTest {
    private val root = "JarvisFolderCommands"
    private val scope = "$root/Download"
    private val empty = setOf("$scope/Empty", "$scope/.hidden-empty", "$scope/Parent/Leaf", "$scope/A/B/C/D/E/Deep", "$scope/Folder with spaces/Empty")
    private fun ask(text: String, previous: FolderMatches? = null) = FolderCommands.resolve(listOf(ChatMsg("user", text)), previous)!!
    @Before fun setup() {
        TestUtil.grantAll(); Storage.file(root).deleteRecursively()
        empty.forEach { assertTrue(Storage.file(it).mkdirs()) }
        TestUtil.write("$scope/HiddenContent/.nomedia", byteArrayOf())
        TestUtil.write("$scope/Zero/empty.txt", byteArrayOf())
        TestUtil.write("$scope/Populated/content.txt", "keep".toByteArray())
        Storage.file("$root/Download2/Neighbor").mkdirs()
        FileIndex.update(emptyList()) // Empty directories never occur in this file-only index.
    }
    @After fun cleanup() { Storage.file(root).deleteRecursively(); FileIndex.invalidate() }

    @Test fun exactScreenshotDownloadRequestChecksActualDirectoriesNotFileIndex() {
        val result = ask("trovami le cartelle vuote dentro la cartella $scope")
        assertEquals(empty, result.folderResults!!.paths.toSet())
        assertNull(result.fileResults); assertNull(result.fileQuery); assertNull(result.plan)
        assertTrue(result.reply.contains("file nascosti"))
    }

    @Test fun globalScreenshotRequestShowsAllAccessibleMatchesIncludingDeepAndHidden() {
        val result = ask("se ci sono cartelle vuote mostramele tutte").folderResults!!
        assertTrue(result.paths.containsAll(empty))
        assertTrue(result.paths.contains("$root/Download2/Neighbor"))
        assertTrue(result.paths.none { it == "$scope/Zero" || it == "$scope/HiddenContent" || it == "$scope/Parent" })
    }

    @Test fun zeroByteFilesHiddenEntriesAndSubfoldersNeverMakeTheirParentEmpty() {
        val result = FolderCommands.scan(scope)
        assertEquals(empty, result.paths.toSet())
        assertFalse(result.paths.contains("$scope/A"))
        assertFalse(result.paths.contains("$scope/Parent"))
        assertEquals("keep", Storage.file("$scope/Populated/content.txt").readText())
    }

    @Test fun clarificationKeepsScopeAndRefreshesFilesWithoutCallingModel() = runBlocking {
        val original = ask("Trova le cartelle vuote in $scope").folderResults!!
        TestUtil.write("$scope/Empty/new.txt", "fresh".toByteArray())
        Storage.file("$scope/New").mkdirs()
        val (reply, engine) = Brain.ask(TestUtil.ctx, listOf(ChatMsg("user", "si intendo quelle senza file")), previousFolders = original)
        assertEquals(scope, reply.folderResults!!.scope)
        assertFalse(reply.folderResults!!.paths.contains("$scope/Empty"))
        assertTrue(reply.folderResults!!.paths.contains("$scope/New"))
        assertFalse(reply.folderResults!!.paths.any { it.startsWith("$root/Download2/") })
        assertTrue(engine.startsWith("Dati locali")); assertNull(reply.plan)
    }

    @Test fun olderFailedConversationRecoversOriginalScopedRequest() {
        val history = listOf(ChatMsg("user", "trovami le cartelle vuote dentro la cartella $scope"),
            ChatMsg("assistant", "Non ho trovato cartelle vuote."), ChatMsg("user", "si intendo quelle senza file"))
        val reply = FolderCommands.resolve(history)!!
        assertEquals(empty, reply.folderResults!!.paths.toSet())
        assertEquals(scope, reply.folderResults!!.scope)
    }

    @Test fun contextCannotBeInventedOrTakenAcrossAnUnrelatedRequest() {
        assertNull(ask("si intendo quelle senza file").folderResults)
        val history = listOf(ChatMsg("user", "Trova le cartelle vuote in $scope"),
            ChatMsg("user", "Trova gli APK"), ChatMsg("assistant", "Elenco APK"), ChatMsg("user", "mostramele tutte"))
        assertNull(FolderCommands.resolve(history)!!.folderResults)
        assertFalse(FolderCommands.handles("mostra l'elenco"))
    }

    @Test fun emptyExistingScopeMissingScopeAndFilePathAreDifferentOutcomes() {
        Storage.file("$root/CompletelyEmpty").mkdirs()
        val found = ask("Trova le cartelle vuote nella cartella '$root/CompletelyEmpty'")
        assertNotNull(found.folderResults); assertTrue(found.folderResults!!.paths.isEmpty())
        assertNull(ask("Trova le cartelle vuote in $root/Missing").folderResults)
        assertNull(ask("Trova le cartelle vuote in $scope/Populated/content.txt").folderResults)
        assertFalse(ask("Trova le cartelle vuote in $root/Missing").reply.contains("Non ho trovato cartelle vuote"))
    }

    @Test fun currentLevelConstraintAndQuotedSpacedPathsArePreserved() {
        val direct = ask("Elenca le cartelle vuote solo direttamente in '$scope'").folderResults!!
        assertFalse(direct.recursive)
        assertEquals(setOf("$scope/Empty", "$scope/.hidden-empty"), direct.paths.toSet())
        assertEquals(listOf("$scope/Folder with spaces/Empty"), ask("Mostrami le cartelle vuote in '$scope/Folder with spaces'").folderResults!!.paths)
    }

    @Test fun unsupportedFiltersMutationsAndMixedRequestsCannotBroadenSearch() {
        listOf("Trova le cartelle vuote in $scope tranne Parent", "Trova le cartelle vuote del 2025",
            "Trova le cartelle vuote e cancella gli APK", "Non cercare cartelle vuote", "Elimina le cartelle vuote in $scope").forEach { text ->
            val reply = ask(text)
            assertNull(text, reply.folderResults); assertNull(text, reply.plan)
        }
        assertTrue(Storage.file("$scope/Empty").isDirectory)
    }

    @Test fun protectedOrEscapingScopeNeverProducesAnEmptyResult() {
        listOf("Android/data", "Android/obb", Storage.TRASH_DIR, PhoneFiles.TEMP_DIR, "../Download", "/etc").forEach {
            val reply = ask("Trova le cartelle vuote in '$it'")
            assertNull(it, reply.folderResults)
        }
    }

    @Test fun cancellationStopsTraversalAndLeavesEverythingUntouched() {
        var steps = 0
        try { FolderCommands.scan(scope) { if (++steps == 3) throw CancellationException("stop") }; fail("Must cancel") }
        catch (_: CancellationException) { }
        assertEquals(3, steps)
        assertTrue(empty.all { Storage.file(it).isDirectory })
        assertEquals("keep", Storage.file("$scope/Populated/content.txt").readText())
    }

    @Test fun allResultsRemainAvailableBeyondOldChatAndIndexLimits() {
        repeat(225) { Storage.file("$scope/Many/${it.toString().padStart(3, '0')}").mkdirs() }
        val result = ask("Mostrami tutte le cartelle vuote in $scope").folderResults!!
        assertEquals(230, result.paths.size)
        assertTrue(result.paths.contains("$scope/Many/224"))
        assertEquals(result.paths.size, result.paths.distinct().size)
    }
}
