package com.jarvis.files

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.core.*
import com.jarvis.files.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Percorso reale: tasto Invia -> anteprima -> Esegui -> Annulla. Nessuna AI/configurazione finta. */
@RunWith(AndroidJUnit4::class)
class CommandFlowTest {
    init { TestUtil.grantAll(); Prefs(TestUtil.ctx).apply { onboardingDone = true; biometricLock = false; aiMode = AiMode.LOCAL; localModelPath = ""; localDownloadId = -1 } }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]
    private val path = "JarvisCommandFlow/JarvisCommandFlow-test.apk"
    @Before fun setup() { TestUtil.write(path, "apk test".toByteArray()); Storage.scanAll(); rule.runOnUiThread { vm().tab = Tab.CHAT } }
    @After fun cleanup() { Storage.file("JarvisCommandFlow").deleteRecursively(); Vault.list(TestUtil.ctx).filter { it.name.startsWith("JarvisCommandFlow-") }.forEach { it.delete() }; FileIndex.invalidate() }
    private fun send() {
        rule.onNodeWithTag("chat_input").performTextInput("Cancella gli APK in JarvisCommandFlow")
        // The OS keyboard can move the composer after text input. Wait for the real
        // keyboard layout (navigation hidden) before dispatching a coordinate tap.
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Home").fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Invia messaggio").assertIsDisplayed().assertIsEnabled().performClick()
        try {
            rule.waitUntil(20_000) { !vm().busy && vm().messages.any { it.plan != null } }
        } catch (e: Throwable) {
            rule.runOnUiThread { TestUtil.report("SEND", "input=${vm().chatInput}; busy=${vm().busy}; operationBusy=${vm().operationBusy}; messages=${vm().messages.map { it.role + ":" + it.text }}") }
            TestUtil.screenshot("command_send_failure")
            rule.onRoot().printToLog("JarvisTest")
            throw e
        }
        rule.waitForIdle()
        rule.onNodeWithTag("chat_input").assertTextEquals("")
        rule.onNodeWithContentDescription("Invia messaggio").assertIsNotEnabled()
    }
    @Test fun sendExecuteUndoButtonsCompleteRealFileOperationWithoutModel() {
        send()
        assertTrue(Storage.file(path).exists())
        TestUtil.screenshot("command_apk_preview")
        rule.onNodeWithText("Esegui").performScrollTo().performClick()
        rule.waitUntil(20_000) { !vm().operationBusy && !Storage.file(path).exists() }
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Home").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Home").performClick()
        rule.onNodeWithText("Annulla modifiche").performScrollTo().performClick()
        rule.waitUntil(20_000) { !vm().operationBusy && Storage.file(path).exists() }
        assertEquals("apk test", Storage.file(path).readText())
    }
    @Test fun cancelButtonLeavesFilesUntouched() {
        send()
        rule.onNodeWithText("Annulla").performScrollTo().performClick()
        rule.runOnUiThread { assertEquals(PlanState.CANCELLED, vm().messages.last().planState) }
        assertTrue(Storage.file(path).exists())
    }
    @Test fun emptySendIsDisabledAndMicrophoneIsSeparate() {
        rule.onNodeWithContentDescription("Invia messaggio").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Detta un messaggio").assertIsEnabled()
        rule.onNodeWithTag("chat_input").performTextInput("test")
        rule.onNodeWithContentDescription("Invia messaggio").assertIsEnabled()
        rule.onNodeWithContentDescription("Detta un messaggio").assertExists()
    }
    @Test fun settingsButtonOpensAiSettings() {
        rule.onNodeWithContentDescription("Impostazioni AI").performClick()
        rule.onNodeWithText("Cervello di Jarvis").assertIsDisplayed()
    }
    @Test fun vaultImportButtonMovesOnlySelectedFile() {
        rule.runOnUiThread { vm().tab = Tab.FILES; vm().category = null; vm().folder = "JarvisCommandFlow"; vm().sub = Sub.CATEGORY }
        rule.onNodeWithText("JarvisCommandFlow-test.apk").performTouchInput { longClick() }
        rule.onNodeWithText("Cassaforte").assertIsDisplayed().performClick()
        rule.waitUntil(20_000) { !vm().operationBusy && !Storage.file(path).exists() }
        assertTrue(Vault.list(TestUtil.ctx).any { it.name.startsWith("JarvisCommandFlow-") && it.readText() == "apk test" })
    }
    @Test fun allPlanEntriesCanBeReviewedBeyondOldLimit() {
        val ops = (0..404).map { Op.Trash("JarvisCommandFlow/$it.apk", 1) }
        rule.runOnUiThread { vm().messages.add(UiMsg(10000, "assistant", "Elenco", plan = Plan(ops, emptyList()))) }
        rule.onNodeWithText("Controlla tutti i 405 elementi").performScrollTo().performClick()
        rule.onNodeWithText("Cestino  JarvisCommandFlow/404.apk").assertDoesNotExist()
        rule.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToIndex(404)
        rule.onNodeWithText("Cestino  JarvisCommandFlow/404.apk").assertIsDisplayed()
        rule.onNodeWithText("Nessuno").performClick()
        rule.onNodeWithText("Fine").performClick()
        rule.onNodeWithText("Esegui").performScrollTo().assertIsNotEnabled()
    }
    @Test fun clarificationChoiceRescansBeforeBuildingDeletionPreview() {
        rule.waitForIdle()
        rule.waitUntil(10_000) { !vm().scanning }
        Storage.scanAll()
        val fresh = "JarvisCommandFlow/newly-downloaded.apk"
        TestUtil.write(fresh, "fresh apk".toByteArray())
        assertFalse(FileIndex.entries.any { it.rel == fresh })
        rule.runOnUiThread { vm().send("Quali file vuoi controllare?\nScelgo: Cancella gli APK in JarvisCommandFlow") }
        rule.waitUntil(20_000) { !vm().busy && vm().messages.any { it.plan != null } }
        rule.runOnIdle {
            val paths = vm().messages.last { it.plan != null }.plan!!.ops.filterIsInstance<Op.Trash>().map { it.path }
            assertTrue(paths.contains(fresh))
            assertTrue(paths.contains(path))
        }
        assertTrue(Storage.file(fresh).exists())
        assertTrue(Storage.file(path).exists())
    }

    @Test fun keyboardSendActionBuildsPreviewAndClearsDraft() {
        rule.onNodeWithTag("chat_input").performTextInput("Cancella gli APK in JarvisCommandFlow")
        rule.onNodeWithTag("chat_input").performImeAction()
        rule.waitUntil(20_000) { !vm().busy && vm().messages.any { it.plan != null } }
        rule.onNodeWithTag("chat_input").assertTextEquals("")
        rule.onNodeWithContentDescription("Invia messaggio").assertIsNotEnabled()
        assertTrue(Storage.file(path).exists())
    }

    @Test fun largestFilesAndShowThemWorkThroughChatWithoutModel() {
        rule.onNodeWithTag("chat_input").performTextInput("quali sono i file che occupano maggiormente la memoria")
        rule.onNodeWithTag("chat_input").performImeAction()
        rule.waitUntil(20_000) { !vm().busy && vm().messages.lastOrNull()?.fileHits != null }
        var paths = emptyList<String>()
        rule.runOnIdle {
            val answer = vm().messages.last()
            assertFalse(answer.error)
            assertTrue(answer.engine.startsWith("Dati locali"))
            val files = answer.fileHits!!
            assertTrue(files.isNotEmpty())
            assertEquals(files.map { it.size }.sortedDescending(), files.map { it.size })
            paths = files.map { it.rel }
            assertNull(answer.plan)
        }
        TestUtil.screenshot("chat_largest_files")
        rule.runOnUiThread { vm().send("mostrameli") }
        rule.waitUntil(20_000) { !vm().busy && vm().messages.size >= 4 }
        rule.runOnIdle {
            assertEquals(paths, vm().messages.last().fileHits!!.map { it.rel })
            assertFalse(vm().messages.last().error)
            assertNull(vm().messages.last().plan)
        }
    }

    @Test fun openingAllResultsPreservesLargestFirstOrder() {
        repeat(15) { i -> TestUtil.write("JarvisCommandFlow/rank-${i.toString().padStart(2, '0')}.txt", ByteArray((i + 1) * 100), (i + 1L) * 86_400_000) }
        Storage.scanAll()
        val ranked = FileIndex.entries.filter { it.rel.startsWith("JarvisCommandFlow/rank-") }.sortedByDescending { it.size }
        rule.runOnUiThread { vm().messages.add(UiMsg(10001, "assistant", "Elenco file", fileHits = ranked)) }
        rule.onNodeWithText("Vedi tutti i 15 file").performScrollTo().performClick()
        rule.onNodeWithText("Risultati").assertIsDisplayed()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("rank-14.txt").fetchSemanticsNodes().isNotEmpty() }
        val first = rule.onNodeWithText("rank-14.txt").fetchSemanticsNode().boundsInRoot.top
        val second = rule.onNodeWithText("rank-13.txt").fetchSemanticsNode().boundsInRoot.top
        assertTrue(first < second)
        rule.onNodeWithText("Recenti").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("rank-00.txt").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("rank-00.txt").assertIsDisplayed()
        rule.onNodeWithText("Risultati").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("rank-14.txt").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("rank-14.txt").assertIsDisplayed()
        rule.onNodeWithText("Nome").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("rank-00.txt").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("rank-00.txt").assertIsDisplayed()
    }

    /** Ricrea il JSON della domanda vista negli screenshot, senza simulare un motore AI. */
    private fun screenshotQuestion(): Pair<String, com.jarvis.files.ai.FollowUp> {
        val folder = "JarvisCommandFlow/Pictures/Screenshots"
        TestUtil.write("$folder/first.png", ByteArray(100))
        TestUtil.write("$folder/keep.pdf", ByteArray(10))
        rule.waitUntil(10_000) { !vm().scanning }
        Storage.scanAll()
        val request = "Cancella gli screenshot in $folder"
        val prompt = "Conferma l'eliminazione di tutti gli screenshot nella cartella '$folder'"
        val raw = org.json.JSONObject().put("reply", "Conferma")
            .put("questions", org.json.JSONArray().put(org.json.JSONObject()
                .put("q", prompt).put("options", org.json.JSONArray(listOf("Sì", "No"))))).toString()
        val q = com.jarvis.files.ai.Brain.validateReply(raw, request).questions.single()
        rule.runOnUiThread {
            vm().messages.add(UiMsg(10020, "user", request))
            vm().messages.add(UiMsg(10021, "assistant", "Conferma", questions = listOf(q)))
        }
        return folder to q
    }

    @Test fun screenshotYesButtonBuildsFreshPreviewThenExecuteAndUndoWorkWithoutModel() {
        val (folder, _) = screenshotQuestion()
        TestUtil.write("$folder/fresh.png", ByteArray(150))
        assertFalse(FileIndex.entries.any { it.rel == "$folder/fresh.png" })
        rule.onNodeWithText("Sì").performScrollTo().performClick()
        rule.waitUntil(20_000) { !vm().busy && vm().messages.lastOrNull()?.plan != null }
        rule.runOnIdle {
            val answer = vm().messages.last()
            assertFalse(answer.error)
            assertEquals(setOf("$folder/first.png", "$folder/fresh.png"),
                answer.plan!!.ops.filterIsInstance<Op.Trash>().map { it.path }.toSet())
            assertTrue(vm().messages.last { it.role == "user" }.text == "Sì")
        }
        assertTrue(Storage.file("$folder/first.png").exists())
        assertTrue(Storage.file("$folder/fresh.png").exists())
        TestUtil.screenshot("chat_screenshot_preview")
        rule.onNodeWithText("Esegui").performScrollTo().performClick()
        rule.waitUntil(20_000) { !vm().operationBusy && !Storage.file("$folder/first.png").exists() }
        assertFalse(Storage.file("$folder/fresh.png").exists())
        assertTrue(Storage.file("$folder/keep.pdf").exists())
        rule.runOnUiThread { vm().undo() }
        rule.waitUntil(20_000) { !vm().operationBusy && Storage.file("$folder/first.png").exists() }
        assertTrue(Storage.file("$folder/fresh.png").exists())
        assertEquals(100L, Storage.file("$folder/first.png").length())
    }

    @Test fun screenshotNoButtonCancelsWithoutModelOrFileChanges() {
        val (folder, _) = screenshotQuestion()
        rule.onNodeWithText("No").performScrollTo().performClick()
        rule.waitUntil(10_000) { !vm().busy && vm().messages.lastOrNull()?.text?.contains("annullata") == true }
        rule.runOnIdle {
            assertFalse(vm().messages.last().error)
            assertNull(vm().messages.last().plan)
        }
        assertTrue(Storage.file("$folder/first.png").exists())
        assertTrue(Storage.file("$folder/keep.pdf").exists())
    }

    @Test fun retryOfLegacyYesMessageKeepsTheOriginalScopeWithoutModel() {
        val (folder, q) = screenshotQuestion()
        rule.runOnUiThread {
            vm().messages.add(UiMsg(10022, "user", q.question + "\nScelgo: Sì"))
            vm().messages.add(UiMsg(10023, "assistant", "Risposta AI incompleta.", error = true))
        }
        rule.onNodeWithText("Riprova").performScrollTo().performClick()
        rule.waitUntil(20_000) { !vm().busy && vm().messages.lastOrNull()?.plan != null }
        rule.runOnIdle {
            assertFalse(vm().messages.last().error)
            assertEquals(setOf("$folder/first.png"), vm().messages.last().plan!!.ops.filterIsInstance<Op.Trash>().map { it.path }.toSet())
        }
        assertTrue(Storage.file("$folder/first.png").exists())
        assertTrue(Storage.file("$folder/keep.pdf").exists())
    }

    @Test fun directScreenshotCommandDoesNotAskModelForAnotherConfirmation() {
        val folder = "JarvisCommandFlow/Pictures/Screenshots"
        TestUtil.write("$folder/shot.png", ByteArray(200)); Storage.scanAll()
        rule.onNodeWithTag("chat_input").performTextInput("Cancella tutti gli screenshot nella cartella '$folder'")
        rule.onNodeWithTag("chat_input").performImeAction()
        rule.waitUntil(20_000) { !vm().busy && vm().messages.lastOrNull()?.plan != null }
        rule.runOnIdle {
            assertFalse(vm().messages.last().error)
            assertTrue(vm().messages.last().questions.isEmpty())
            assertEquals(setOf("$folder/shot.png"), vm().messages.last().plan!!.ops.filterIsInstance<Op.Trash>().map { it.path }.toSet())
        }
        assertTrue(Storage.file("$folder/shot.png").exists())
    }

}

