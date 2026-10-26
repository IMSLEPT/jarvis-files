package com.jarvis.files

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.core.Op
import com.jarvis.files.core.Plan
import com.jarvis.files.core.Prefs
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.Tab
import com.jarvis.files.ui.UiMsg
import com.jarvis.files.ai.FollowUp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Apre ogni schermata reale e salva uno screenshot. */
@RunWith(AndroidJUnit4::class)
class UiTest {
    init {
        TestUtil.grantAll()
        Prefs(TestUtil.ctx).apply { onboardingDone = true; biometricLock = false; aiMode = com.jarvis.files.core.AiMode.AUTO; onlineApiKey = ""; localModelPath = ""; localDownloadId = -1 }
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]

    private fun shot(name: String, wait: Long = 2500) {
        rule.waitForIdle()
        Thread.sleep(wait)
        TestUtil.sh("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        Thread.sleep(300)
        TestUtil.screenshot(name)
        TestUtil.report("UI", "Schermata $name OK")
    }

    private fun go(tab: Tab, sub: Sub = Sub.NONE) = rule.runOnUiThread { vm().tab = tab; vm().sub = sub }

    @Test fun allScreens() {
        TestUtil.sh("cmd uimode night no")
        rule.activityRule.scenario.recreate()
        shot("01_home", 5000)
        rule.onAllNodesWithText("Jarvis").onFirst().performClick()
        shot("02_chat_vuota")
        // Messaggio vero senza AI configurata: deve spiegare cosa fare
        rule.runOnUiThread { vm().send("Metti in ordine la cartella Download") }
        rule.waitUntil(20_000) { rule.onAllNodesWithText("Nessuna AI", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("03_chat_senza_ai")
        // Esempio di risposta con domanda e anteprima (dati finti solo per mostrare l'aspetto)
        rule.runOnUiThread {
            vm().messages.add(UiMsg(900, "user", "Rinomina i PDF in Download"))
            vm().messages.add(
                UiMsg(
                    901, "assistant", "Certo, signore. Ecco il piano: 2 PDF in Download/PDF.",
                    questions = listOf(FollowUp("Che stile di nome preferisce?", listOf("2026-10-19 Bolletta.pdf", "Bolletta 19-10-2026.pdf"))),
                    plan = Plan(listOf(Op.Mkdir("Download/PDF"), Op.Move("Download/a.pdf", "Download/PDF/Bolletta Enel.pdf"), Op.Trash("Download/vecchio.apk", 12_000_000)), emptyList()),
                    engine = "Telefono · gemma-4-E2B-it",
                )
            )
        }
        shot("04_chat_anteprima")
        go(Tab.PHOTOS); shot("05_foto")
        go(Tab.CLEAN); shot("06_pulizia_cache", 5000)
        rule.onAllNodesWithText("WhatsApp").onFirst().performClick(); shot("07_pulizia_whatsapp")
        rule.onAllNodesWithText("File inutili").onFirst().performClick(); shot("08_pulizia_inutili")
        go(Tab.MORE); shot("09_altro")
        go(Tab.MORE, Sub.SECURITY); shot("10_sicurezza", 8000)
        go(Tab.MORE, Sub.AUTOMATIONS); shot("11_automazioni")
        go(Tab.MORE, Sub.BATTERY); shot("12_batteria")
        go(Tab.MORE, Sub.SETTINGS); shot("13_impostazioni")
        go(Tab.MORE, Sub.PEOPLE); shot("14_persone", 5000)
        go(Tab.MORE, Sub.TRASH); shot("15_cestino")
        go(Tab.MORE, Sub.VAULT); shot("16_cassaforte")
        go(Tab.MORE, Sub.FIND); shot("17_trova")
        go(Tab.FILES); shot("18_file")
        rule.runOnUiThread { vm().category = com.jarvis.files.core.FileCategory.DOCUMENT; vm().folder = null; vm().sub = Sub.CATEGORY }
        shot("18b_categoria")
        go(Tab.HOME)
        rule.runOnUiThread { vm().voiceOpen = true }
        shot("19_voce", 3000)
        rule.runOnUiThread { vm().voiceOpen = false }
        // Stesse schermate principali in tema scuro
        TestUtil.sh("cmd uimode night yes")
        rule.activityRule.scenario.recreate()
        go(Tab.HOME); shot("20_home_scuro", 4000)
        go(Tab.CHAT); shot("21_chat_scura")
        go(Tab.CLEAN); shot("22_pulizia_scura")
        TestUtil.sh("cmd uimode night no")
    }
}

