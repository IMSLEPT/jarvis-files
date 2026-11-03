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
    @Test fun moreOpensSearchableInventoryAndPerAppChart() {
        rule.runOnUiThread { vm().tab = Tab.MORE; vm().sub = Sub.NONE }
        rule.onNodeWithText("App installate").performScrollTo().performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("apps_search").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("apps_search").performScrollTo().performTextInput("com.jarvis.files")
        rule.onNodeWithTag("apps_list").performScrollToNode(hasTestTag("app_com.jarvis.files"))
        rule.onNodeWithTag("app_com.jarvis.files").performClick()
        rule.onNodeWithText("Utilizzo giornaliero").performScrollTo().assertIsDisplayed()
        TestUtil.screenshot("apps_usage_chart")
        rule.onNodeWithText("Spazio occupato").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertEquals(Sub.APPS, vm().sub) }
    }
}
