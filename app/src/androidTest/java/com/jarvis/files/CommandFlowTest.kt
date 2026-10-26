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
        rule.onNodeWithContentDescription("Invia messaggio").performClick()
        rule.waitUntil(20_000) { !vm().busy && vm().messages.any { it.plan != null } }
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
        rule.onNodeWithText("Home").performClick()
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
}
