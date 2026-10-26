package com.jarvis.files

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.jarvis.files.automation.Automations
import com.jarvis.files.core.Notifier

class JarvisApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        com.jarvis.files.core.Prefs(this).applyLocalDefaultOnce()
        Notifier.createChannels(this)
        Automations.createDefaults(this)
        Automations.scheduleMaintenance(this)
    }

    /** Anteprime: anche il primo fotogramma dei video, con cache su disco per scorrere veloce. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}

