package com.jarvis.files

import android.app.Application
import com.jarvis.files.automation.Automations
import com.jarvis.files.core.Notifier

class JarvisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Automations.createDefaults(this)
        Automations.scheduleMaintenance(this)
    }
}
