package com.jarvis.files

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.files.core.Storage
import java.io.File

/** Utilità comuni ai test su dispositivo: permessi, rapporto leggibile, screenshot. */
object TestUtil {
    val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val reportDir: File get() = File(Storage.root, "JarvisReport").apply { mkdirs() }

    fun sh(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun grantAll() {
        val pkg = ctx.packageName
        sh("appops set $pkg MANAGE_EXTERNAL_STORAGE allow")
        sh("appops set $pkg GET_USAGE_STATS allow")
        sh("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        sh("pm grant $pkg android.permission.READ_MEDIA_IMAGES")
        sh("pm grant $pkg android.permission.ACCESS_MEDIA_LOCATION")
        repeat(50) { if (Storage.hasAllFilesAccess()) return; Thread.sleep(100) }
    }

    @Synchronized
    fun report(section: String, line: String) {
        File(reportDir, "report.txt").appendText("[$section] $line\n")
        android.util.Log.i("JarvisTest", "[$section] $line")
    }

    fun screenshot(name: String) {
        val bmp: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        File(reportDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun write(rel: String, content: ByteArray, ageMs: Long = 0): File {
        val f = Storage.file(rel)
        f.parentFile?.mkdirs()
        f.writeBytes(content)
        if (ageMs > 0) f.setLastModified(System.currentTimeMillis() - ageMs)
        return f
    }

    fun asset(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }
}
