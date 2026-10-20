package com.jarvis.files.system

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.jarvis.files.MainActivity
import com.jarvis.files.core.Notifier

/** Pulsante nel pannello rapido di Android: apre Jarvis in ascolto vocale. */
class JarvisTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val i = Intent(this, MainActivity::class.java).putExtra(Notifier.EXTRA_OPEN, "voice")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 7, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(i)
        }
    }
}
