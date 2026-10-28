package com.jarvis.files

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.ai.*
import com.jarvis.files.core.*
import com.jarvis.files.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderChatFlowTest {
    init { TestUtil.grantAll(); Prefs(TestUtil.ctx).apply { onboardingDone = true; biometricLock = false; aiMode = AiMode.LOCAL; localModelPath = ""; localDownloadId = -1 } }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]
    private val root = "JarvisFolderChatFlow"
    private val download = "Download/JarvisEmptyFolderFlow"
    @Before fun setup() {
        Storage.file(root).deleteRecursively(); Storage.file(download).deleteRecursively()
        Storage.file("$root/Empty").mkdirs(); Storage.file("$root/Deep/A/B/C/D/Leaf").mkdirs()
        TestUtil.write("$root/Hidden/.nomedia", byteArrayOf())
        TestUtil.write("$root/Zero/file.txt", byteArrayOf())
        Storage.file("$download/Empty").mkdirs()
        rule.runOnUiThread { vm().clearChat(); vm().tab = Tab.CHAT; vm().sub = Sub.NONE }
    }
    @After fun cleanup() { Storage.file(root).deleteRecursively(); Storage.file(download).deleteRecursively(); FileIndex.invalidate() }
    private fun send(text: String) {
        rule.onNodeWithTag("chat_input").performTextInput(text)
        rule.onNodeWithTag("chat_input").performImeAction()
        rule.waitUntil(20_000) { !vm().busy && vm().messages.lastOrNull()?.role == "assistant" }
        rule.runOnIdle { assertFalse(vm().messages.last().text, vm().messages.last().error); assertNull(vm().messages.last().plan) }
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Home").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }
    private fun row(path: String) = rule.onNodeWithTag("chat_messages").performScrollToNode(hasTestTag("folder_result_$path"))

    @Test fun exactScreenshotRequestFindsEmptyDownloadDirectoriesWithoutInstalledModel() {
        send("trovami le cartelle vuote dentro la cartella download")
        rule.runOnIdle {
            val msg = vm().messages.last()
            assertEquals("Download", msg.folderHits!!.scope)
            assertTrue(msg.folderHits!!.paths.contains("$download/Empty"))
            assertNull(msg.fileHits); assertTrue(msg.engine.startsWith("Dati locali"))
        }
        // A found folder is a browser action, not a file-open intent or a fabricated JSON result.
        val result = vm().messages.last().folderHits!!
        if (result.paths.indexOf("$download/Empty") >= 8) {
            rule.onNodeWithText("Vedi tutte le ${result.paths.size} cartelle").performScrollTo().performClick()
            rule.onNodeWithTag("folder_results_list").performScrollToNode(hasTestTag("folder_result_$download/Empty"))
        } else row("$download/Empty")
        rule.onNodeWithTag("folder_result_$download/Empty").performClick()
        rule.waitUntil(10_000) { vm().sub == Sub.BROWSER && vm().browserPath == "$download/Empty" }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Cartella vuota").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Cartella vuota").assertExists()
        assertTrue(Storage.file("$download/Empty").isDirectory)
    }

    @Test fun screenshotClarificationRecoversPriorScopeAndRefreshesDirectoryContents() {
        rule.runOnUiThread {
            vm().messages.add(UiMsg(90001, "user", "trovami le cartelle vuote dentro la cartella $root"))
            vm().messages.add(UiMsg(90002, "assistant", "Non ho trovato cartelle vuote."))
        }
        send("si intendo quelle senza file")
        rule.runOnIdle {
            assertEquals(root, vm().messages.last().folderHits!!.scope)
            assertEquals(setOf("$root/Empty", "$root/Deep/A/B/C/D/Leaf"), vm().messages.last().folderHits!!.paths.toSet())
        }
        TestUtil.screenshot("chat_empty_folders")
        TestUtil.write("$root/Empty/arrived.txt", "arrived".toByteArray())
        send("mostramele tutte")
        rule.runOnIdle {
            assertEquals(listOf("$root/Deep/A/B/C/D/Leaf"), vm().messages.last().folderHits!!.paths)
            assertEquals(root, vm().messages.last().folderHits!!.scope)
        }
        assertEquals("arrived", Storage.file("$root/Empty/arrived.txt").readText())
    }

    @Test fun allFolderResultsBeyondTwoHundredAreReachableAndOpenActualFolder() {
        repeat(225) { Storage.file("$root/Many/Item-${it.toString().padStart(3, '0')}").mkdirs() }
        send("se ci sono cartelle vuote nella cartella '$root/Many' mostramele tutte")
        rule.runOnIdle { assertEquals(225, vm().messages.last().folderHits!!.paths.size) }
        rule.onNodeWithText("Vedi tutte le 225 cartelle").performScrollTo().performClick()
        rule.onNodeWithTag("folder_results_list").performScrollToNode(hasTestTag("folder_result_$root/Many/Item-224"))
        rule.onNodeWithTag("folder_result_$root/Many/Item-224").assertIsDisplayed()
        TestUtil.screenshot("all_empty_folders")
        rule.onNodeWithTag("folder_result_$root/Many/Item-224").performClick()
        rule.waitUntil(10_000) { vm().sub == Sub.BROWSER && vm().browserPath == "$root/Many/Item-224" }
        assertTrue(Storage.file("$root/Many/Item-224").isDirectory)
    }

    @Test fun emptyResultDoesNotShowMisleadingNoFilesMessageOrRetry() {
        send("Trova le cartelle vuote in $root/Zero")
        rule.runOnIdle { assertTrue(vm().messages.last().folderHits!!.paths.isEmpty()); assertNull(vm().messages.last().fileHits) }
        rule.onNodeWithText("Nessun file trovato.").assertDoesNotExist()
        rule.onNodeWithText("Riprova").assertDoesNotExist()
        assertTrue(Storage.file("$root/Zero/file.txt").exists())
    }

    @Test fun continuousMessagesKeepOneFolderWithoutRepeatingItsPath() {
        TestUtil.write("$root/Report.pdf", "report".toByteArray())
        TestUtil.write("$download/Report.pdf", "neighbor".toByteArray())
        Storage.scanAll()
        send("Trova la cartella '$root'")
        send("dentro cercami questo file")
        rule.runOnIdle { assertTrue(vm().conversationContext!!.awaitingFileName) }
        send("Report.pdf")
        rule.runOnIdle {
            assertEquals(listOf("$root/Report.pdf"), vm().messages.last().fileHits!!.map { it.rel })
            assertEquals(root, vm().conversationContext!!.scope)
        }
        send("mostrameli")
        rule.runOnIdle { assertEquals(listOf("$root/Report.pdf"), vm().messages.last().fileHits!!.map { it.rel }) }
        TestUtil.screenshot("chat_continuous_context")
        assertEquals("neighbor", Storage.file("$download/Report.pdf").readText())
    }

    @Test fun ambiguousFolderChoiceResumesSearchThroughRealOptionButton() {
        TestUtil.write("$root/One/Bill.pdf", "one".toByteArray())
        TestUtil.write("$root/Two/Bill.pdf", "two".toByteArray())
        Storage.scanAll()
        send("Trova le cartelle in '$root'")
        send("dentro cercami il file Bill.pdf")
        rule.runOnIdle { assertNotNull(vm().conversationContext!!.pending); assertNull(vm().messages.last().fileHits) }
        val message = vm().messages.last()
        assertTrue("$root/One" in message.questions.single().options)
        rule.onNodeWithTag("chat_choice_${message.id}_$root/One").performScrollTo().performClick()
        rule.waitUntil(20_000) { !vm().busy && vm().messages.lastOrNull()?.fileHits != null }
        rule.runOnIdle {
            assertFalse(vm().messages.last().error)
            assertEquals(listOf("$root/One/Bill.pdf"), vm().messages.last().fileHits!!.map { it.rel })
        }
    }

    @Test fun newChatClearsFolderReferences() {
        send("Trova la cartella '$root'")
        rule.onNodeWithContentDescription("Nuova chat").performClick()
        rule.runOnIdle { assertNull(vm().conversationContext); assertTrue(vm().messages.isEmpty()) }
        send("dentro cercami Report.pdf")
        rule.runOnIdle { assertNull(vm().messages.last().fileHits); assertNull(vm().messages.last().plan) }
    }

    @Test fun navigatingWhileReplyStartsCannotChangeTheSubmittedFolder() {
        TestUtil.write("$root/Report.pdf", "report".toByteArray())
        TestUtil.write("$download/Report.pdf", "neighbor".toByteArray())
        Storage.scanAll()
        send("Trova la cartella '$root'")
        rule.runOnUiThread { vm().send("dentro cerca Report.pdf"); vm().openBrowser(download) }
        rule.waitUntil(20_000) { !vm().busy && vm().messages.lastOrNull()?.fileHits != null }
        rule.runOnIdle { assertEquals(listOf("$root/Report.pdf"), vm().messages.last().fileHits!!.map { it.rel }) }
    }
}
