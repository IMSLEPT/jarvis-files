package com.jarvis.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AppInsightsTest {
    private fun app(pkg: String, bytes: Long?, ms: Long?, inactive: Boolean? = false) = InstalledApp(pkg, pkg, null, false, 0,
        bytes, bytes, bytes, ms?.let { listOf(UsageDay(LocalDate.now(), it)) }, null, inactive)
    @Test fun classificationsIncludeZerosButKeepUnknownValuesLastAndCacheIsNotDoubleCounted() {
        val a = app("large", 1000, 60000); val b = app("unused", 10, 0, true); val c = app("unknown", null, null, null)
        val apps = listOf(c, b, a)
        assertEquals(2000L, a.totalBytes)
        assertEquals(listOf("large", "unused", "unknown"), AppInsights.ordered(apps, AppOrder.LARGE).map { it.pkg })
        assertEquals(listOf("unused", "large", "unknown"), AppInsights.ordered(apps, AppOrder.LEAST).map { it.pkg })
        assertEquals(listOf("large", "unused", "unknown"), AppInsights.ordered(apps, AppOrder.MOST).map { it.pkg })
        assertEquals(listOf("unused"), AppInsights.ordered(apps, AppOrder.INACTIVE).map { it.pkg })
    }
    @Test fun revokedPermissionStillListsEveryInstalledPackageWithoutInventingZeros() = runBlocking {
        TestUtil.sh("appops set ${TestUtil.ctx.packageName} GET_USAGE_STATS deny")
        try {
            val inventory = AppInsights.load(TestUtil.ctx, 7)
            assertFalse(inventory.usageAccess)
            assertTrue(inventory.apps.any { it.pkg == TestUtil.ctx.packageName })
            assertEquals(TestUtil.ctx.packageManager.getInstalledApplications(0).map { it.packageName }.toSet(), inventory.apps.map { it.pkg }.toSet())
            assertTrue(inventory.apps.all { it.days == null && it.foregroundMs == null && it.totalBytes == null && it.unused30 == null })
        } finally { TestUtil.grantAll() }
    }
    @Test fun realInventoryProvidesRequestedDatesAndSeparatesMissingHistory() = runBlocking {
        TestUtil.grantAll()
        val data = AppInsights.load(TestUtil.ctx, 30)
        assertTrue(data.apps.isNotEmpty())
        data.apps.mapNotNull { it.days }.forEach { days ->
            assertEquals(30, days.size)
            assertEquals(days.map { it.date }.distinct(), days.map { it.date })
            assertTrue(days.all { it.millis == null || it.millis >= 0 })
        }
        val missing = app("missing", 5, 1).copy(days = listOf(UsageDay(LocalDate.now(), null)))
        assertNull(missing.foregroundMs)
    }
}
