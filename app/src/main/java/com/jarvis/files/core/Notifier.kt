package com.jarvis.files.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jarvis.files.MainActivity
import com.jarvis.files.R

object Notifier {
    const val CH_MAIN = "jarvis_main"
    const val CH_WORK = "jarvis_work"
    const val EXTRA_OPEN = "open"

    fun createChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MAIN, "Avvisi di Jarvis", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_WORK, "Lavori in corso", NotificationManager.IMPORTANCE_LOW))
    }

    fun openIntent(c: Context, target: String): PendingIntent {
        val i = Intent(c, MainActivity::class.java).putExtra(EXTRA_OPEN, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(c, target.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun notify(c: Context, id: Int, title: String, text: String, open: String = "home") {
        if (c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(c, CH_MAIN)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(c, open))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(id, n)
    }

    fun progress(c: Context, title: String, text: String, done: Int, total: Int) =
        NotificationCompat.Builder(c, CH_WORK)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(1), done, total <= 0)
            .build()
}
