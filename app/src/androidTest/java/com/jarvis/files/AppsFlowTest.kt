package com.jarvis.files

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.core.Prefs
import com.jarvis.files.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppsFlowTest {
    init { TestUtil.grantAll(); Prefs(TestUtil.ctx).apply { onboardingDone = true; biometricLock = false } }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]
    @Test fun organizerChoiceButtonCarriesContextToExecutablePreview() {
        val folder = "JarvisOrganizeUi"
        com.jarvis.files.core.Storage.file(folder).deleteRecursively()
        try {
            TestUtil.write("$folder/a.pdf", "test".toByteArray())
            rule.waitUntil(30_000) { !vm().scanning }
            rule.runOnUiThread { vm().openChatWith("Riordina tutti i file nella cartella '$folder'") }
            rule.waitUntil(30_000) { !vm().busy && vm().messages.lastOrNull()?.questions?.isNotEmpty() == true }
            rule.onNodeWithText("Per tipo di file").performScrollTo().performClick()
            rule.waitUntil(30_000) { !vm().busy && vm().messages.lastOrNull()?.plan != null }
            rule.runOnIdle {
                assertTrue(vm().messages.last().questions.isEmpty())
                assertFalse(vm().messages.last().error)
                assertEquals(1, vm().messages.last().plan!!.moves)
            }
            assertTrue(com.jarvis.files.core.Storage.file("$folder/a.pdf").exists())
            TestUtil.screenshot("organizer_chat_preview")
        } finally { com.jarvis.files.core.Storage.file(folder).deleteRecursively(); com.jarvis.files.core.FileIndex.invalidate() }
    }
    @Test fun moreOpensSearchableInventoryAndPerAppChart() {
        rule.runOnUiThread { vm().tab = Tab.MORE; vm().sub = Sub.NONE }
        rule.onNodeWithText("App installate").performScrollTo().performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("app · profilo corrente", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("apps_search").performScrollTo().performTextInput("com.jarvis.files")
        rule.onNodeWithTag("apps_list").performScrollToNode(hasTestTag("app_com.jarvis.files"))
        rule.onNodeWithTag("app_com.jarvis.files").performClick()
        rule.onNodeWithText("Utilizzo giornaliero").performScrollTo().assertIsDisplayed()
        TestUtil.screenshot("apps_usage_chart")
        rule.onNodeWithText("Spazio occupato").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertEquals(Sub.APPS, vm().sub) }
    }
}
