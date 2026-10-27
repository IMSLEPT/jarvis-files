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

/** Verifica i collegamenti reali del redesign, inclusi gli strumenti spostati nei menu. */
@RunWith(AndroidJUnit4::class)
class DesignFlowTest {
    init { TestUtil.grantAll(); Prefs(TestUtil.ctx).apply { onboardingDone = true; biometricLock = false } }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]

    @Test fun homeSearchOpensDocumentSearch() {
        rule.runOnUiThread { vm().tab = Tab.HOME; vm().sub = Sub.NONE }
        rule.onNodeWithContentDescription("Cerca file e cartelle").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(Sub.FIND, vm().sub) }
    }

    @Test fun everyGroupedMoreDestinationStillOpens() {
        val destinations = listOf("Sicurezza" to Sub.SECURITY, "Cartella segreta" to Sub.VAULT,
            "Cestino" to Sub.TRASH, "Automazioni" to Sub.AUTOMATIONS, "Trova documenti" to Sub.FIND,
            "Persone" to Sub.PEOPLE, "Batteria" to Sub.BATTERY, "Impostazioni" to Sub.SETTINGS)
        destinations.forEach { (title, destination) ->
            rule.runOnUiThread { vm().tab = Tab.MORE; vm().sub = Sub.NONE }
            rule.onNodeWithText(title).performScrollTo().performClick()
            rule.runOnIdle { assertEquals(destination, vm().sub) }
        }
    }

    @Test fun extraCleaningToolsRemainReachable() {
        rule.runOnUiThread { vm().tab = Tab.CLEAN; vm().sub = Sub.NONE; vm().cleanSection = "CACHE" }
        rule.onNodeWithContentDescription("Altri strumenti di pulizia").performClick()
        rule.onNodeWithText("Grandi e vecchi").performClick()
        rule.runOnIdle { assertEquals("BIG", vm().cleanSection) }
        rule.onNodeWithContentDescription("Altri strumenti di pulizia").performClick()
        rule.onNodeWithText("Screenshot e foto").performClick()
        rule.runOnIdle { assertEquals("SHOTS", vm().cleanSection) }
        TestUtil.screenshot("redesign_pulizia_strumenti")
    }

    @Test fun cacheExplanationExpandsWithoutLaunchingCleanup() {
        rule.runOnUiThread { vm().tab = Tab.CLEAN; vm().sub = Sub.NONE; vm().cleanSection = "CACHE" }
        rule.onNodeWithText("Come funziona").performScrollTo().performClick()
        rule.onNodeWithText("La cache contiene file temporanei.", substring = true).assertExists()
        TestUtil.screenshot("redesign_cache_dettagli")
        rule.onNodeWithText("Come funziona").performScrollTo().performClick()
        rule.onNodeWithText("La cache contiene file temporanei.", substring = true).assertDoesNotExist()
    }
}
