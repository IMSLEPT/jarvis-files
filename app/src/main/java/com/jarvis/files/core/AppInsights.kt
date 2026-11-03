package com.jarvis.files.core

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Process
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class UsageDay(val date: LocalDate, val millis: Long?)
data class InstalledApp(
    val pkg: String, val name: String, val icon: Drawable?, val system: Boolean, val installedAt: Long,
    val appBytes: Long?, val dataBytes: Long?, val cacheBytes: Long?,
    val days: List<UsageDay>?, val lastUsed: Long?, val unused30: Boolean?,
) {
    // Android include già la cache in dataBytes.
    val totalBytes: Long? get() = if (appBytes != null && dataBytes != null) appBytes + dataBytes else null
    val foregroundMs: Long? get() = days?.takeIf { list -> list.any { it.millis != null } }?.sumOf { it.millis ?: 0 }
}
enum class AppOrder(val label: String) { NAME("Tutte"), LARGE("Più pesanti"), MOST("Più usate"), LEAST("Meno usate"), INACTIVE("Inattive 30g") }
data class AppInventory(val apps: List<InstalledApp>, val usageAccess: Boolean, val usageAvailable: Boolean, val loadedAt: Long)

object AppInsights {
    fun duration(ms: Long?): String = when {
        ms == null -> "Non disponibile"
        ms == 0L -> "0 min"
        ms < 60_000 -> "<1 min"
        ms < 3_600_000 -> "${ms / 60_000} min"
        else -> "${ms / 3_600_000} h ${(ms / 60_000) % 60} min"
    }
    fun ordered(apps: List<InstalledApp>, order: AppOrder): List<InstalledApp> = when (order) {
        AppOrder.NAME -> apps.sortedBy { it.name.lowercase(Locale.ITALY) }
        AppOrder.LARGE -> apps.sortedWith(compareByDescending<InstalledApp> { it.totalBytes ?: -1L }.thenBy { it.name })
        AppOrder.MOST -> apps.sortedWith(compareByDescending<InstalledApp> { it.foregroundMs ?: -1L }.thenBy { it.name })
        AppOrder.LEAST -> apps.sortedWith(compareBy<InstalledApp> { it.foregroundMs ?: Long.MAX_VALUE }.thenBy { it.name })
        AppOrder.INACTIVE -> apps.filter { it.unused30 == true }.sortedWith(compareByDescending<InstalledApp> { it.totalBytes ?: -1L }.thenBy { it.name })
    }

    suspend fun load(c: Context, dayCount: Int, now: Long = System.currentTimeMillis()): AppInventory {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dates = (dayCount.coerceIn(1, 30) - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val start = dates.first().atStartOfDay(zone).toInstant().toEpochMilli()
        val cutoff = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        val access = AppCache.hasUsageAccess(c)
        val pm = c.packageManager
        val usm = c.getSystemService(UsageStatsManager::class.java)
        // Un'unica query per tutti i pacchetti; i record giornalieri evitano il raddoppio degli intervalli aggregati.
        val stats = if (access) runCatching { usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, minOf(start, cutoff), now) }.getOrNull() else null
        val usageAvailable = access && stats != null && stats.isNotEmpty()
        val knownDates = stats.orEmpty().map { Instant.ofEpochMilli(it.firstTimeStamp).atZone(zone).toLocalDate() }.toSet()
        val recent = if (access) runCatching { usm.queryAndAggregateUsageStats(today.minusDays(90).atStartOfDay(zone).toInstant().toEpochMilli(), now) }.getOrNull() else null
        val grouped = stats.orEmpty().filter { it.firstTimeStamp < now && it.lastTimeStamp > minOf(start, cutoff) }.groupBy { it.packageName }
        val ssm = c.getSystemService(StorageStatsManager::class.java)
        val apps = pm.getInstalledApplications(0).map { ai ->
            currentCoroutineContext().ensureActive()
            val pkg = ai.packageName
            val records = grouped[pkg].orEmpty()
            val daily = if (usageAvailable) dates.map { date ->
                val sum = records.filter { Instant.ofEpochMilli(it.firstTimeStamp).atZone(zone).toLocalDate() == date }.sumOf { it.totalTimeInForeground.coerceAtLeast(0) }
                UsageDay(date, sum.takeIf { date in knownDates })
            } else null
            val last = maxOf(records.maxOfOrNull { it.lastTimeUsed } ?: 0, recent?.get(pkg)?.lastTimeUsed ?: 0).takeIf { it > 0 && it <= now }
            val install = runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(now)
            val st = if (access) runCatching { ssm.queryStatsForPackage(ai.storageUuid, pkg, Process.myUserHandle()) }.getOrNull() else null
            val monthUsed = records.any { it.lastTimeUsed >= cutoff || it.totalTimeInForeground > 0 && it.firstTimeStamp >= cutoff }
            InstalledApp(pkg, runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg),
                runCatching { pm.getApplicationIcon(ai) }.getOrNull(), ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                install, st?.appBytes, st?.dataBytes, st?.cacheBytes, daily, last,
                if (usageAvailable && last != null) install < cutoff && last < cutoff && !monthUsed else null)
        }
        // Ricontrolla dopo le query: la revoca non deve trasformare dati mancanti in zeri.
        if (access && !AppCache.hasUsageAccess(c)) return AppInventory(apps.map { it.copy(appBytes = null, dataBytes = null, cacheBytes = null, days = null, lastUsed = null, unused30 = null) }, false, false, now)
        return AppInventory(apps, access, usageAvailable, now)
    }
}
