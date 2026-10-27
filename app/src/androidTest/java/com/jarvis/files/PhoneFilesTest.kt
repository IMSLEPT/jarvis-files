package com.jarvis.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.core.*
import kotlinx.coroutines.CancellationException
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneFilesTest {
    private val root = "JarvisPhoneBrowser"
    @Before fun setup() {
        TestUtil.grantAll(); Storage.file(root).deleteRecursively()
        Storage.file("$root/Source/Empty").mkdirs()
        Storage.file("$root/Destination").mkdirs()
        TestUtil.write("$root/Source/note.txt", "contenuto originale".toByteArray())
        TestUtil.write("$root/Source/.hidden", "nascosto".toByteArray())
        TestUtil.write("$root/Source/Deep/A/B/C/D/E/inside.pdf", ByteArray(50))
        FileIndex.update(emptyList())
    }
    @After fun cleanup() { Storage.file(root).deleteRecursively(); FileIndex.invalidate() }
    private fun execute(p: Plan) = FileOps.execute(TestUtil.ctx, p, "PhoneFilesTest")
    private inline fun rejects(block: () -> Unit) { try { block(); fail("Operazione non rifiutata") } catch (_: IllegalArgumentException) { } }

    @Test fun browsingIncludesEmptyAndHiddenFoldersWithoutUsingIndexOrDepthLimit() {
        val nodes = PhoneFiles.list("$root/Source")
        assertTrue(nodes.any { it.name == "Empty" && it.directory && it.childCount == 0 })
        assertTrue(nodes.any { it.name == ".hidden" })
        assertFalse(PhoneFiles.list("$root/Source", hidden = false).any { it.name == ".hidden" })
        assertEquals("inside.pdf", PhoneFiles.list("$root/Source/Deep/A/B/C/D/E").single().name)
        assertTrue(FileIndex.entries.isEmpty())
        assertTrue(nodes.takeWhile { it.directory }.size == 2)
    }

    @Test fun searchIsImmediateChildrenOnlyAndNeverClaimsAnInaccessibleFolderIsEmpty() {
        assertEquals(listOf("note.txt"), PhoneFiles.list("$root/Source", query = "NOTE").map { it.name })
        assertTrue(PhoneFiles.list("$root/Source", query = "inside.pdf").isEmpty())
        rejects { PhoneFiles.list("Android/data") }
        rejects { PhoneFiles.list("../") }
        assertNotNull(PhoneFiles.restriction("Android/obb"))
        assertNotNull(PhoneFiles.restriction(Storage.TRASH_DIR))
    }

    @Test fun createRenameAndUndoKeepExactNamesAndContent() {
        assertEquals(1, execute(PhoneFiles.mkdirPlan("$root/Source", "Nuova cartella")).done)
        assertTrue(Storage.file("$root/Source/Nuova cartella").isDirectory)
        assertEquals(1 to 0, Undo.undo(TestUtil.ctx))
        assertFalse(Storage.file("$root/Source/Nuova cartella").exists())
        assertEquals(1, execute(PhoneFiles.renamePlan("$root/Source/note.txt", "Nota 🧪.md")).done)
        assertEquals("contenuto originale", Storage.file("$root/Source/Nota 🧪.md").readText())
        assertEquals(1 to 0, Undo.undo(TestUtil.ctx))
        assertTrue(Storage.file("$root/Source/note.txt").exists())
        rejects { PhoneFiles.mkdirPlan("$root", "../escape") }
        rejects { PhoneFiles.renamePlan("$root/Source/note.txt", "Empty") }
        rejects { PhoneFiles.renamePlan("Download", "RenamedDownload") }
    }

    @Test fun folderCopyIncludesEmptyAndHiddenChildrenWithoutOverwritingAndUndoIsRecoverable() {
        val c = TestUtil.ctx
        Storage.file("$root/Destination/Source").mkdirs()
        TestUtil.write("$root/Destination/Source/keep.txt", "keep".toByteArray())
        val r = PhoneFiles.paste(c, FileClipboard(listOf("$root/Source"), false), "$root/Destination")
        assertEquals(1, r.result.done); assertEquals(0, r.result.failed)
        val copied = "$root/Destination/Source (1)"
        assertTrue(Storage.file("$copied/Empty").isDirectory)
        assertEquals("nascosto", Storage.file("$copied/.hidden").readText())
        assertEquals(50L, Storage.file("$copied/Deep/A/B/C/D/E/inside.pdf").length())
        assertEquals("keep", Storage.file("$root/Destination/Source/keep.txt").readText())
        assertEquals(1 to 0, Undo.undo(c))
        assertFalse(Storage.file(copied).exists())
        val item = Trash.items(c).first { it.original == copied }
        assertTrue(Trash.restore(c, item.id))
        assertEquals("contenuto originale", Storage.file("$copied/note.txt").readText())
        assertTrue(Storage.file("$root/Source/note.txt").exists())
    }

    @Test fun moveAndTrashDirectoriesAreUndoableWithoutOverwritingNeighbors() {
        Storage.file("$root/Destination/Empty").mkdirs()
        val r = PhoneFiles.paste(TestUtil.ctx, FileClipboard(listOf("$root/Source/Empty"), true), "$root/Destination")
        assertEquals(1, r.result.done)
        assertTrue(Storage.file("$root/Destination/Empty (1)").isDirectory)
        assertEquals(1 to 0, Undo.undo(TestUtil.ctx))
        assertTrue(Storage.file("$root/Source/Empty").isDirectory)
        assertEquals(1, execute(PhoneFiles.trashPlan(listOf("$root/Source/Deep"))).done)
        assertFalse(Storage.file("$root/Source/Deep").exists())
        assertEquals(1 to 0, Undo.undo(TestUtil.ctx))
        assertEquals(50L, Storage.file("$root/Source/Deep/A/B/C/D/E/inside.pdf").length())
    }

    @Test fun sameFolderCutIsNoOpAndSelfCopyAndSelfMoveAreRejected() {
        val r = PhoneFiles.paste(TestUtil.ctx, FileClipboard(listOf("$root/Source/note.txt"), true), "$root/Source")
        assertEquals(0, r.result.done); assertEquals(0, r.result.failed)
        assertTrue(Storage.file("$root/Source/note.txt").exists())
        rejects { PhoneFiles.movePlan(listOf("$root/Source"), "$root/Source/Deep") }
        val copied = PhoneFiles.paste(TestUtil.ctx, FileClipboard(listOf("$root/Source"), false), "$root/Source/Deep")
        assertEquals(1, copied.result.failed)
        assertFalse(Storage.file("$root/Source/Deep/Source").exists())
    }

    @Test fun duplicateCopyNamesKeepLongUnicodeExtensionsAndCancellationCleansStaging() {
        val name = "🧪".repeat(50) + ".pdf"
        TestUtil.write("$root/Source/$name", ByteArray(100))
        val clip = FileClipboard(listOf("$root/Source/$name"), false)
        assertEquals(1, PhoneFiles.paste(TestUtil.ctx, clip, "$root/Destination").result.done)
        assertEquals(1, PhoneFiles.paste(TestUtil.ctx, clip, "$root/Destination").result.done)
        val copies = PhoneFiles.list("$root/Destination").filter { it.name.endsWith(".pdf") }
        assertEquals(2, copies.size)
        assertTrue(copies.all { it.name.toByteArray(Charsets.UTF_8).size <= 255 })
        try {
            PhoneFiles.paste(TestUtil.ctx, FileClipboard(listOf("$root/Source"), false), "$root/Destination") {
                if (Storage.file(PhoneFiles.TEMP_DIR).list()?.isNotEmpty() == true) throw CancellationException("test cancellation")
            }
            fail("Cancellazione ignorata")
        } catch (_: CancellationException) { }
        assertFalse(Storage.file("$root/Destination/Source").exists())
        assertTrue(!Storage.file(PhoneFiles.TEMP_DIR).exists() || Storage.file(PhoneFiles.TEMP_DIR).list()!!.isEmpty())
        assertTrue(Storage.file("$root/Source/note.txt").exists())
    }

    @Test fun protectedRootsCannotBeTraversedCopiedOrMutatedThroughBrowser() {
        listOf("Android/data", "Android/obb", Storage.TRASH_DIR, PhoneFiles.TEMP_DIR).forEach { path ->
            assertNotNull(PhoneFiles.restriction(path))
            rejects { PhoneFiles.list(path) }
            rejects { PhoneFiles.trashPlan(listOf(path)) }
            rejects { PhoneFiles.paste(TestUtil.ctx, FileClipboard(listOf(path), false), "$root/Destination") }
        }
        rejects { PhoneFiles.movePlan(listOf("Download"), "$root/Destination") }
        rejects { PhoneFiles.mkdirPlan("$root", "../../outside") }
    }
    @Test fun interruptedProcessStagingIsCleanedWithoutTouchingTheOriginal() {
        val stale = PhoneFiles.TEMP_DIR + "/" + java.util.UUID.randomUUID().toString()
        TestUtil.write("$stale/partial.bin", ByteArray(500))
        val result = PhoneFiles.paste(TestUtil.ctx, FileClipboard(listOf("$root/Source/note.txt"), false), "$root/Destination")
        assertEquals(1, result.result.done)
        assertFalse(Storage.file(stale).exists())
        assertEquals("contenuto originale", Storage.file("$root/Source/note.txt").readText())
        assertEquals("contenuto originale", Storage.file("$root/Destination/note.txt").readText())
    }

}
