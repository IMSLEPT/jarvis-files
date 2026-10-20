package com.jarvis.files.core

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings

enum class CacheSafety(val label: String) {
    SAFE("Sicura da svuotare"),
    CAREFUL("Attenzione"),
}

data class AppCacheInfo(
    val pkg: String,
    val name: String,
    val icon: Drawable?,
    val cacheBytes: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val safety: CacheSafety,
    val why: String,
)

data class AppUsage(val pkg: String, val name: String, val foregroundMs: Long)

/**
 * Cache delle app. Android non permette a un'app normale di svuotare la cache delle altre una per una:
 * si può (1) chiedere al sistema di svuotarle tutte insieme con conferma, (2) aprire la scheda della singola app.
 */
object AppCache {

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Svuota tutte le cache (il sistema chiede conferma). Richiede l'accesso a tutti i file. */
    fun clearAllIntent() = Intent(StorageManager.ACTION_CLEAR_APP_CACHE)

    fun appDetailsIntent(pkg: String) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // Categorie in cui la cache contiene contenuti offline o costosi da riscaricare
    private val careful = listOf(
        Regex("spotify|music|deezer|tidal|amazon\\.mp3|soundcloud|audible|podcast|pocketcasts") to "Musica/podcast: potresti perdere brani scaricati in cache e dovrai riscaricarli.",
        Regex("maps|waze|here|osmand|sygic|navigat") to "Mappe: le mappe in cache verranno riscaricate (consumo dati).",
        Regex("netflix|primevideo|disney|youtube|raiplay|mediaset|dazn|now\\.|infinity|twitch") to "Video: la riproduzione ripartirà da zero e userà dati.",
        Regex("photos|gallery|lightroom|snapseed") to "Foto: miniature e anteprime verranno rigenerate (lento la prima volta).",
        Regex("kindle|books|readera|moon\\+") to "Libri: alcuni contenuti offline potrebbero essere riscaricati.",
        Regex("game|games|supercell|king\\.|mihoyo|hoyoverse|epicgames|roblox|mojang|ea\\.|gameloft") to "Gioco: potrebbe dover riscaricare dati aggiuntivi (anche GB).",
        Regex("telegram") to "Telegram: i media già visti verranno riscaricati dalle chat.",
    )

    fun classify(pkg: String, cache: Long): Pair<CacheSafety, String> {
        careful.firstOrNull { it.first.containsMatchIn(pkg.lowercase()) }?.let { return CacheSafety.CAREFUL to it.second }
        if (cache > 2L * 1024 * 1024 * 1024) return CacheSafety.CAREFUL to "Cache molto grande: probabilmente contiene contenuti offline."
        return CacheSafety.SAFE to "File temporanei: si ricreano da soli, nessun dato personale perso."
    }

    fun list(c: Context): List<AppCacheInfo> {
        val pm = c.packageManager
        val ssm = c.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        return pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 || pm.getLaunchIntentForPackage(it.packageName) != null }
            .mapNotNull { ai ->
                // Senza "accesso ai dati di utilizzo" le dimensioni non sono leggibili: l'app resta in elenco con -1
                val st = runCatching { ssm.queryStatsForPackage(ai.storageUuid, ai.packageName, user) }.getOrNull()
                if (st == null && pm.getLaunchIntentForPackage(ai.packageName) == null) return@mapNotNull null
                val cache = st?.cacheBytes ?: -1L
                val (safety, why) = classify(ai.packageName, cache)
                AppCacheInfo(
                    ai.packageName, pm.getApplicationLabel(ai).toString(), runCatching { pm.getApplicationIcon(ai) }.getOrNull(),
                    cache, st?.appBytes ?: -1L, st?.dataBytes ?: -1L, safety, why,
                )
            }
            .sortedWith(compareByDescending<AppCacheInfo> { it.cacheBytes }.thenBy { it.name.lowercase() })
    }

    /** Tempo di utilizzo delle app nelle ultime 24 ore (indicatore del consumo di batteria). */
    fun usage(c: Context, hours: Int = 24): List<AppUsage> {
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val stats = usm.queryAndAggregateUsageStats(end - hours * 3_600_000L, end)
        val pm = c.packageManager
        return stats.values.filter { it.totalTimeInForeground > 60_000 && it.packageName != c.packageName }
            .map { s ->
                val name = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString() }.getOrDefault(s.packageName)
                AppUsage(s.packageName, name, s.totalTimeInForeground)
            }
            .sortedByDescending { it.foregroundMs }
    }
}
