package com.jarvis.files

import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.core.*
import com.jarvis.files.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class UsabilityTest {
    init { TestUtil.grantAll(); Prefs(TestUtil.ctx).apply { onboardingDone = true; biometricLock = false } }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]

    @Test fun homeAssistantButtonOpensChat() {
        rule.runOnUiThread { vm().tab = Tab.HOME; vm().sub = Sub.NONE }
        rule.onNodeWithContentDescription("Chiedi a Jarvis").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Impostazioni AI").assertIsDisplayed()
    }
    @Test fun newChatRemovesConversationAndDraft() {
        rule.runOnUiThread {
            vm().tab = Tab.CHAT
            vm().messages.add(UiMsg(1000, "user", "Bozza di prova"))
            vm().chatInput = "testo da cancellare"
        }
        rule.onNodeWithContentDescription("Nuova chat").performClick()
        rule.runOnUiThread { assertTrue(vm().messages.isEmpty()); assertEquals("", vm().chatInput); assertFalse(vm().busy) }
    }
    @Test fun draftSurvivesTabNavigation() {
        rule.runOnUiThread { vm().tab = Tab.CHAT; vm().chatInput = "messaggio non inviato" }
        rule.runOnUiThread { vm().tab = Tab.HOME }
        rule.waitForIdle()
        rule.runOnUiThread { vm().tab = Tab.CHAT }
        rule.onNodeWithText("messaggio non inviato").assertExists()
    }
    @Test fun noExecutionWhenEveryOperationIsExcluded() {
        rule.runOnUiThread {
            vm().tab = Tab.CHAT
            vm().messages.add(UiMsg(1001, "assistant", "Proposta", plan = Plan(listOf(Op.Mkdir("JarvisRegression/UI")), emptyList())))
        }
        rule.onNodeWithText("Crea cartella  JarvisRegression/UI").performClick()
        rule.onNodeWithText("Esegui").assertIsNotEnabled()
    }
    @Test fun originalHomeRemainsVisibleWithLargeSystemFonts() {
        val oldScale = TestUtil.sh("settings get system font_scale").trim().toFloatOrNull() ?: 1f
        try {
            TestUtil.sh("settings put system font_scale 1.5")
            rule.waitUntil(10_000) { rule.activity.resources.configuration.fontScale >= 1.49f }
            rule.runOnUiThread { vm().tab = Tab.HOME; vm().sub = Sub.NONE }
            rule.onNodeWithContentDescription("Jarvis Files").assertIsDisplayed()
            rule.onNodeWithText("Video").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Immagini").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Audio").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Archivi").performScrollTo().assertIsDisplayed()
            TestUtil.screenshot("regression_home_font_150")
        } finally { TestUtil.sh("settings put system font_scale $oldScale") }
    }
    @Test fun vaultRequiresUnlockEvenWhenAppLockIsOff() {
        rule.runOnUiThread { vm().tab = Tab.MORE; vm().sub = Sub.VAULT }
        rule.onNodeWithText("Sblocca cartella").assertExists()
        rule.onNodeWithText("Aggiungi file").assertDoesNotExist()
    }
    @Test fun categoriesRefreshWhenExternalFilesChangeWithinCacheWindow() {
        val path = "JarvisHomeRefresh/new.pdf"
        try {
            rule.runOnUiThread { vm().tab = Tab.CHAT; vm().sub = Sub.NONE }
            rule.waitUntil(10_000) { !vm().scanning }
            Storage.file("JarvisHomeRefresh").deleteRecursively()
            Storage.scanAll()
            val before = FileIndex.entries.filter { it.category == FileCategory.DOCUMENT }.sumOf { it.size }
            rule.runOnUiThread { vm().refreshHome(force = true) }
            rule.waitUntil(10_000) { vm().homeReady && (vm().home.byCat.toMap()[FileCategory.DOCUMENT] ?: 0L) == before }
            TestUtil.write(path, ByteArray(4096) { 1 })
            Storage.scanAll()
            rule.runOnUiThread { vm().refreshHome() }
            rule.waitUntil(10_000) { (vm().home.byCat.toMap()[FileCategory.DOCUMENT] ?: 0L) == before + 4096L }
        } finally {
            Storage.file("JarvisHomeRefresh").deleteRecursively()
            FileIndex.invalidate()
        }
    }

}

