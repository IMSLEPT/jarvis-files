package com.jarvis.files.core

import android.content.Context
import android.os.PowerManager

/**
 * Protegge il telefono durante i lavori pesanti (analisi foto): misura il calore con l'API termica
 * di Android e controlla la batteria.
 */
object PowerGuard {
    /** Temperatura interna: true se conviene mettere in pausa. */
    fun isHot(c: Context): Boolean {
        val pm = c.getSystemService(PowerManager::class.java) ?: return false
        if (pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) return true
        // Margine prima del surriscaldamento (1.0 = limite). NaN se il telefono non lo fornisce.
        val headroom = runCatching { pm.getThermalHeadroom(10) }.getOrDefault(Float.NaN)
        if (!headroom.isNaN() && headroom >= 0.75f) return true
        return DeviceInfo.battery(c).tempC >= 40f
    }

    /** Abbastanza fresco per ripartire (soglia più bassa per non accendere/spegnere di continuo). */
    fun isCool(c: Context): Boolean {
        val pm = c.getSystemService(PowerManager::class.java) ?: return true
        if (pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_LIGHT) return false
        val headroom = runCatching { pm.getThermalHeadroom(10) }.getOrDefault(Float.NaN)
        if (!headroom.isNaN() && headroom >= 0.6f) return false
        return DeviceInfo.battery(c).tempC < 37f
    }
}
