package com.jarvis.files.core

import android.content.Context

/** Contatori per il resoconto settimanale (si azzerano dopo ogni resoconto). */
object Stats {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("jarvis_stats", Context.MODE_PRIVATE)

    fun addFreed(c: Context, bytes: Long) = add(c, "freed", bytes)
    fun addOrganized(c: Context, n: Int) = add(c, "organized", n.toLong())
    fun addThreats(c: Context, n: Int) = add(c, "threats", n.toLong())
    fun addPhotosIndexed(c: Context, n: Int) = add(c, "photos", n.toLong())

    private fun add(c: Context, key: String, v: Long) {
        val s = sp(c)
        s.edit()
            .putLong("week_$key", s.getLong("week_$key", 0) + v)
            .putLong("total_$key", s.getLong("total_$key", 0) + v)
            .apply()
    }

    fun week(c: Context, key: String) = sp(c).getLong("week_$key", 0)
    fun total(c: Context, key: String) = sp(c).getLong("total_$key", 0)

    fun resetWeek(c: Context) {
        sp(c).edit().apply {
            listOf("freed", "organized", "threats", "photos").forEach { putLong("week_$it", 0) }
        }.apply()
    }
}
