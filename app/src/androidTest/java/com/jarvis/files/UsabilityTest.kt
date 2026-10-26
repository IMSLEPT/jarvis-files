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
    @Test fun homeWorksWithLargeFonts() {
        val saved = Configuration(rule.activity.resources.configuration)
        val large = Configuration(saved).apply { fontScale = 1.5f }
        try {
            rule.runOnUiThread {
                @Suppress("DEPRECATION")
                rule.activity.resources.updateConfiguration(large, rule.activity.resources.displayMetrics)
                vm().tab = Tab.HOME
            }
            rule.activityRule.scenario.recreate()
            rule.waitForIdle()
            rule.onNodeWithText("I tuoi file").assertExists()
            TestUtil.screenshot("regression_home_font_150")
        } finally {
            rule.runOnUiThread {
                @Suppress("DEPRECATION")
                rule.activity.resources.updateConfiguration(saved, rule.activity.resources.displayMetrics)
            }
        }
    }
    @Test fun vaultRequiresUnlockEvenWhenAppLockIsOff() {
        rule.runOnUiThread { vm().tab = Tab.MORE; vm().sub = Sub.VAULT }
        rule.onNodeWithText("Sblocca cartella").assertExists()
        rule.onNodeWithText("Aggiungi file").assertDoesNotExist()
    }
}
