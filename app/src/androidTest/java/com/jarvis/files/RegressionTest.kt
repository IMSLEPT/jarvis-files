package com.jarvis.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.ai.Brain
import com.jarvis.files.ai.BrainReply
import com.jarvis.files.ai.FileQuery
import com.jarvis.files.ai.FollowUp
import com.jarvis.files.core.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegressionTest {
    private val ctx get() = TestUtil.ctx
    @Before fun setup() {
        TestUtil.grantAll()
        Storage.file("JarvisRegression").deleteRecursively()
        TestUtil.write("JarvisRegression/Download/a.pdf", "original".toByteArray())
        TestUtil.write("JarvisRegression/Download2/b.pdf", "neighbor".toByteArray())
        Storage.scanAll()
    }
    @After fun cleanup() { Storage.file("JarvisRegression").deleteRecursively(); FileIndex.invalidate() }

    @Test fun brandLetterCounterRemainsTransparent() {
        val drawable = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.jarvis_wordmark_brand)!!.mutate()
        val bitmap = android.graphics.Bitmap.createBitmap(288, 108, android.graphics.Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 288, 108)
        drawable.draw(android.graphics.Canvas(bitmap))
        // Actual pixels in the "a": clear counter, opaque left stroke. A background-colored patch also fails.
        assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(82, 56)))
        assertTrue(android.graphics.Color.alpha(bitmap.getPixel(66, 56)) > 200)
    }
    @Test fun emptyGroupFilterCannotTargetEntirePhone() {
        val p = FileOps.buildPlan(JSONArray("""[{"type":"delete_where","where":{}}]"""))
        assertTrue(p.isEmpty)
        assertTrue(p.warnings.isNotEmpty())
    }
    @Test fun cannotMoveStorageRootOrSystemFolders() {
        val p = FileOps.buildPlan(JSONArray("""[
            {"type":"move","from":"","to":"Download/backup"},
            {"type":"rename","path":"DCIM","new_name":"old photos"}
        ]"""))
        assertTrue(p.isEmpty)
    }
    @Test fun pathsCannotEscapeRootOrMatchRootByPrefix() {
        assertNull(Storage.sanitize("/sdcard2/file.pdf"))
        assertNull(Storage.sanitize("/storage/emulated/01/file.pdf"))
        assertNull(Storage.sanitize("/etc/passwd"))
        assertNull(Storage.sanitize("Download/../DCIM"))
        assertNull(Storage.sanitize("Android/data/app"))
        assertEquals("Download/a.pdf", Storage.sanitize("/sdcard/Download/a.pdf"))
    }
    @Test fun executeValidatesPlansFromEverySource() {
        val result = FileOps.execute(ctx, Plan(listOf(Op.Trash("Download", 0)), emptyList()), "Regression")
        assertEquals(0, result.done)
        assertEquals(1, result.failed)
    }
    @Test fun moveNeverOverwritesExistingFile() {
        val src = Storage.file("JarvisRegression/Download/a.pdf")
        val dst = Storage.file("JarvisRegression/Download2/b.pdf")
        assertFalse(FileOps.moveFile(src, dst))
        assertEquals("original", src.readText())
        assertEquals("neighbor", dst.readText())
    }
    @Test fun repeatedTrashRestoreCreatesUniqueNamesAndRefreshesIndex() {
        val rel = "JarvisRegression/Download/a.pdf"
        val id = Trash.put(ctx, rel)!!
        TestUtil.write(rel, "replacement".toByteArray())
        TestUtil.write("JarvisRegression/Download/a (ripristinato 1).pdf", "previous restore".toByteArray())
        assertTrue(Trash.restore(ctx, id))
        assertEquals("replacement", Storage.file(rel).readText())
        assertEquals("previous restore", Storage.file("JarvisRegression/Download/a (ripristinato 1).pdf").readText())
        assertEquals("original", Storage.file("JarvisRegression/Download/a (ripristinato 2).pdf").readText())
        assertEquals(0L, FileIndex.scannedAt)
    }
    @Test fun folderSearchDoesNotIncludeSimilarFolderNames() {
        val q = FileQuery(emptyList(), listOf("pdf"), "JarvisRegression/Download", null, null)
        assertEquals(listOf("a.pdf"), Brain.searchFiles(q).map { it.name })
    }
    @Test fun jsonNullIsNotARealFolderOrPhotoSearch() {
        val q = Brain.parseFileQuery(JSONObject("""{"folder":null,"from":null,"to":null}"""))
        assertNull(q.folder); assertNull(q.fromIso); assertNull(q.toIso)
        assertNull(Brain.parse("""{"reply":"ciao","photo_search":null}""").photoSearch)
    }
    @Test fun clarificationCannotOfferPrematureExecution() {
        val reply = Brain.clean(BrainReply("Quale nome?", listOf(FollowUp("Nome?", listOf("A", "B"))), Plan(listOf(Op.Mkdir("JarvisRegression/New")), emptyList())), "Rinomina i PDF")
        assertNull(reply.plan)
    }
    @Test fun photoMentionsInOrganizationDoNotTriggerPhotoSearch() {
        val reply = Brain.clean(BrainReply("Riordino", photoSearch = "foto"), "Riordina le foto in Download")
        assertNull(reply.photoSearch)
    }
    @Test fun largeFilesWithMatchingEndsAreNotDuplicates() {
        val a = Storage.file("JarvisRegression/large-a.mp4")
        val b = Storage.file("JarvisRegression/large-b.mp4")
        listOf(a, b).forEach { f -> java.io.RandomAccessFile(f, "rw").use { it.setLength(201L * 1024 * 1024) } }
        java.io.RandomAccessFile(b, "rw").use { it.seek(100L * 1024 * 1024); it.write(1) }
        Storage.scanAll()
        assertTrue(Cleaner.findDuplicates().none { g -> g.files.any { it.rel.startsWith("JarvisRegression/large-") } })
    }
    @Test fun activeDownloadIsNotSuggestedAsJunk() {
        TestUtil.write("JarvisRegression/active.crdownload", "downloading".toByteArray())
        Storage.scanAll()
        assertTrue(Cleaner.junk(ctx).none { it.rel == "JarvisRegression/active.crdownload" })
    }
    @Test fun partialUndoCanBeRetriedWithoutLosingJournal() {
        val original = "JarvisRegression/Download/a.pdf"
        val moved = "JarvisRegression/Download/moved.pdf"
        FileOps.execute(ctx, Plan(listOf(Op.Move(original, moved)), emptyList()), "Regression")
        TestUtil.write(original, "new content".toByteArray())
        assertEquals(1, Undo.undo(ctx).second)
        assertNotNull(Undo.info(ctx))
        Storage.file(original).delete()
        assertEquals(0, Undo.undo(ctx).second)
        assertEquals("original", Storage.file(original).readText())
    }

    @Test fun emptyIndexIsCachedUntilItIsInvalidated() {
        FileIndex.update(emptyList())
        assertTrue(FileIndex.ensure().isEmpty())
        FileIndex.invalidate()
        assertTrue(FileIndex.ensure().any { it.rel == "JarvisRegression/Download/a.pdf" })
    }

    @Test fun longRenameKeepsExtensionAndExecutesWithUnicode() {
        val path = "JarvisRegression/Download/a.pdf"
        listOf("a".repeat(300), "🧪".repeat(100)).forEach { name ->
            val actions = JSONArray().put(JSONObject().put("type", "rename").put("path", path).put("new_name", name))
            val plan = FileOps.buildPlan(actions)
            val moved = plan.ops.filterIsInstance<Op.Move>().single()
            val filename = Storage.file(moved.to).name
            assertTrue(filename.endsWith(".pdf"))
            assertTrue(filename.toByteArray(Charsets.UTF_8).size <= 255)
            assertEquals(0, FileOps.execute(ctx, plan, "Rename regression").failed)
            assertEquals("original", Storage.file(moved.to).readText())
            assertEquals(0, Undo.undo(ctx).second)
            assertEquals("original", Storage.file(path).readText())
        }
    }

    @Test fun longVaultNamesKeepExtensionAndHandleCollisions() {
        val name = "JarvisRegression-vault-" + "a".repeat(224) + ".pdf"
        val path = "JarvisRegression/$name"
        val exported = mutableListOf<java.io.File>()
        try {
            listOf("first", "second").forEach { text ->
                TestUtil.write(path, text.toByteArray())
                assertTrue(Vault.importPath(ctx, path))
            }
            val files = Vault.list(ctx).filter { it.name.startsWith("JarvisRegression-vault-") }
            assertEquals(2, files.size)
            assertEquals(setOf("first", "second"), files.map { it.readText() }.toSet())
            files.forEach { file ->
                assertTrue(file.name.endsWith(".pdf"))
                assertTrue(file.name.toByteArray(Charsets.UTF_8).size <= 255)
                val out = Vault.export(ctx, file)
                assertNotNull(out)
                exported.add(out!!)
                assertTrue(out.name.endsWith(".pdf"))
            }
        } finally {
            exported.forEach { it.delete() }
            Vault.list(ctx).filter { it.name.startsWith("JarvisRegression-vault-") }.forEach { it.delete() }
        }
    }

}

