package com.jarvis.files.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class BatterySnapshot(
    val level: Int,
    val charging: Boolean,
    val tempC: Float,
    val health: String,
    val voltageMv: Int,
    val cycles: Int?,
)

/** Raccoglie informazioni sul telefono per l'assistente e per la schermata Home. */
object DeviceInfo {

    fun battery(c: Context): BatterySnapshot {
        val i: Intent? = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: -1
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Buona"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Surriscaldata"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Esausta"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sovratensione"
            BatteryManager.BATTERY_HEALTH_COLD -> "Troppo fredda"
            else -> "Sconosciuta"
        }
        val cycles = i?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it >= 0 }
        return BatterySnapshot(
            level = level,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            tempC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            health = health,
            voltageMv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            cycles = cycles,
        )
    }

    fun ram(c: Context): Pair<Long, Long> {
        val am = c.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem to mi.totalMem
    }

    fun network(c: Context): String {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return "nessuna connessione"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "rete mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "altro"
        }
    }

    fun securityPatchAgeDays(): Long? = runCatching {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(Build.VERSION.SECURITY_PATCH)!!
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - d.time)
    }.getOrNull()

    /** Testo compatto con lo stato del telefono, da dare all'AI. */
    fun snapshot(c: Context): String {
        val b = battery(c)
        val (ramFree, ramTot) = ram(c)
        val uptimeH = SystemClock.elapsedRealtime() / 3_600_000
        return buildString {
            appendLine("Telefono: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), patch sicurezza ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Memoria: ${Fmt.size(Storage.freeBytes())} liberi su ${Fmt.size(Storage.totalBytes())}")
            appendLine("RAM: ${Fmt.size(ramFree)} libera su ${Fmt.size(ramTot)}")
            appendLine("Batteria: ${b.level}% ${if (b.charging) "in carica" else ""}, ${b.tempC}°C, salute ${b.health}${b.cycles?.let { ", $it cicli" } ?: ""}")
            appendLine("Acceso da: $uptimeH ore · Rete: ${network(c)}")
        }
    }
}
