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

@RunWith(AndroidJUnit4::class)
class PhoneBrowserFlowTest {
    init { TestUtil.grantAll(); Prefs(TestUtil.ctx).apply { onboardingDone = true; biometricLock = false } }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]
    private val root = "JarvisBrowserFlow"
    @Before fun setup() {
        Storage.file(root).deleteRecursively()
        Storage.file("$root/Empty").mkdirs(); Storage.file("$root/Destination").mkdirs()
        TestUtil.write("$root/note.txt", "testo browser".toByteArray())
        TestUtil.write("$root/.hidden", ByteArray(10))
        TestUtil.write("$root/Deep/A/B/C/D/E/file.pdf", ByteArray(20))
        rule.runOnUiThread { vm().openBrowser(root) }
        row("$root/note.txt").assertIsDisplayed()
    }
    @After fun cleanup() { Storage.file(root).deleteRecursively(); FileIndex.invalidate() }
    private fun ready() {
        val ready = hasTestTag("browser_list") and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Cartella caricata")
        rule.waitUntil(10_000) { rule.onAllNodes(ready).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun row(path: String): SemanticsNodeInteraction {
        ready()
        rule.onNodeWithTag("browser_list").performScrollToNode(hasTestTag("browser_row_" + path))
        return rule.onNodeWithTag("browser_row_" + path)
    }
    private fun actions(name: String) {
        ready()
        rule.onNodeWithTag("browser_list").performScrollToNode(hasContentDescription("Azioni per $name"))
        rule.onNodeWithContentDescription("Azioni per $name").performClick()
    }
    private fun name(value: String) {
        rule.onNodeWithTag("browser_name").performTextReplacement(value)
        rule.onNodeWithText("Salva").performClick()
        rule.waitUntil(20_000) { !vm().operationBusy }
    }

    @Test fun mainStorageEntryAndHomeShortcutOpenRealBrowser() {
        rule.runOnUiThread { vm().tab = Tab.FILES; vm().sub = Sub.NONE }
        rule.onNodeWithText("Memoria interna").performScrollTo().performClick()
        rule.onNodeWithTag("phone_browser").assertIsDisplayed()
        rule.runOnIdle { assertEquals("", vm().browserPath) }
        row(root).performScrollTo().performClick()
        row("$root/Empty").performScrollTo().performClick()
        rule.onNodeWithText("Cartella vuota").assertIsDisplayed()
        rule.onNodeWithContentDescription("Cartella superiore").performClick()
        row("$root/note.txt").assertIsDisplayed()
        rule.runOnUiThread { vm().tab = Tab.HOME; vm().sub = Sub.NONE }
        rule.onNodeWithText("Tutte le cartelle").performScrollTo().performClick()
        rule.onNodeWithTag("phone_browser").assertIsDisplayed()
        rule.runOnIdle { assertEquals("", vm().browserPath) }
    }

    @Test fun hiddenFilesDeepFoldersAndBreadcrumbsAreNavigable() {
        row("$root/.hidden").assertIsDisplayed()
        var path = root
        listOf("Deep", "A", "B", "C", "D", "E").forEach { segment -> path += "/$segment"; row(path).performScrollTo().performClick() }
        row("$path/file.pdf").assertIsDisplayed()
        rule.onNodeWithText("Memoria interna").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("", vm().browserPath) }
        TestUtil.screenshot("browser_internal_storage")
    }

    @Test fun createRenameTrashAndUndoWorkFromActualButtons() {
        rule.onNodeWithContentDescription("Nuova cartella").performClick(); name("New folder")
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("browser_row_$root/New folder").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(Storage.file("$root/New folder").isDirectory)
        actions("note.txt"); rule.onNodeWithText("Rinomina").performClick(); name("Renamed.txt")
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("browser_row_$root/Renamed.txt").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("testo browser", Storage.file("$root/Renamed.txt").readText())
        actions("Renamed.txt"); rule.onNodeWithText("Nel cestino").performClick()
        assertTrue(Storage.file("$root/Renamed.txt").exists())
        rule.onNodeWithText("Sposta nel cestino").performClick()
        rule.waitUntil(20_000) { !vm().operationBusy && !Storage.file("$root/Renamed.txt").exists() }
        rule.onNodeWithText("Annulla ultima modifica").performClick()
        rule.waitUntil(20_000) { !vm().operationBusy && Storage.file("$root/Renamed.txt").exists() }
        assertEquals("testo browser", Storage.file("$root/Renamed.txt").readText())
        TestUtil.screenshot("browser_manual_actions")
    }

    @Test fun copyAndMoveUseDestinationNavigationAndNeverOverwrite() {
        TestUtil.write("$root/Destination/note.txt", "existing".toByteArray())
        actions("note.txt"); rule.onNodeWithText("Copia").performClick()
        row("$root/Destination").performScrollTo().performClick()
        rule.onNodeWithText("Incolla qui").performClick()
        rule.waitUntil(20_000) { !vm().operationBusy && Storage.file("$root/Destination/note (1).txt").exists() }
        assertEquals("existing", Storage.file("$root/Destination/note.txt").readText())
        assertEquals("testo browser", Storage.file("$root/Destination/note (1).txt").readText())
        assertTrue(Storage.file("$root/note.txt").exists())
        rule.onNodeWithContentDescription("Cartella superiore").performClick()
        actions("note.txt"); rule.onNodeWithText("Sposta").performClick()
        row("$root/Empty").performScrollTo().performClick()
        rule.onNodeWithText("Incolla qui").performClick()
        rule.waitUntil(20_000) { !vm().operationBusy && Storage.file("$root/Empty/note.txt").exists() }
        assertFalse(Storage.file("$root/note.txt").exists())
        rule.onNodeWithText("Annulla ultima modifica").performClick()
        rule.waitUntil(20_000) { !vm().operationBusy && Storage.file("$root/note.txt").exists() }
    }

    @Test fun searchFiltersOnlyCurrentFolderAndCanBeCleared() {
        rule.onNodeWithTag("browser_search").performTextInput("note")
        rule.onNodeWithTag("browser_search").performImeAction()
        row("$root/note.txt").assertIsDisplayed()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("browser_row_$root/Empty").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription("Cancella ricerca cartella").performClick()
        row("$root/Empty").assertIsDisplayed()
    }
}
