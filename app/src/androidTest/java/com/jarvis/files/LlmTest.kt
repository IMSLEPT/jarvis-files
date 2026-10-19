package com.jarvis.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.files.ai.Brain
import com.jarvis.files.ai.ChatMsg
import com.jarvis.files.ai.LocalAi
import com.jarvis.files.core.AiMode
import com.jarvis.files.core.FileOps
import com.jarvis.files.core.Prefs
import com.jarvis.files.core.Storage
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Prova con il modello vero (Gemma 4 nel telefono). Gira solo se il workflow ha copiato il modello
 * in /sdcard/JarvisModel; altrimenti viene saltato.
 */
@RunWith(AndroidJUnit4::class)
class LlmTest {
    @Test fun realModel() = runBlocking {
        TestUtil.grantAll()
        val model = File(Storage.root, "JarvisModel").listFiles()?.firstOrNull { it.name.endsWith(".litertlm") }
        assumeTrue("Modello non presente: test saltato", model != null)
        val ctx = TestUtil.ctx
        Prefs(ctx).apply {
            localModelPath = model!!.absolutePath
            localDownloadId = -1
            aiMode = AiMode.LOCAL
            localUseGpu = false
        }
        Storage.file("JarvisTest").deleteRecursively()
        TestUtil.write("JarvisTest/Download/bolletta_enel_marzo_2026.pdf", "x".toByteArray())
        TestUtil.write("JarvisTest/Download/fattura_tim_settembre.pdf", "x".toByteArray())
        TestUtil.write("JarvisTest/Download/IMG_20260812_101500.jpg", ByteArray(3000))
        TestUtil.write("JarvisTest/Download/Screenshot_20260901.png", ByteArray(3000))
        TestUtil.write("JarvisTest/Download/whatsapp_audio_123.opus", ByteArray(3000))
        Storage.file("JarvisTest/Download/PDF").mkdirs()
        Storage.scanAll()

        suspend fun ask(q: String): com.jarvis.files.ai.BrainReply {
            val t0 = System.currentTimeMillis()
            val (r, engine) = Brain.ask(ctx, listOf(ChatMsg("user", q)))
            TestUtil.report("LLM", "[$engine, ${(System.currentTimeMillis() - t0) / 1000}s] \"$q\" → risposta=\"${r.reply}\" " +
                "domande=${r.questions.map { it.question + " " + it.options }} piano=${r.plan?.ops?.map { it.label }} " +
                "avvisi=${r.plan?.warnings} foto=${r.photoSearch} file=${r.fileQuery}")
            return r
        }

        val r1 = ask("Metti i PDF della cartella JarvisTest/Download nella sottocartella JarvisTest/Download/PDF")
        val r2 = ask("Riordina la cartella JarvisTest/Download creando sottocartelle per tipo di file")
        val r3 = ask("Rinomina i file in JarvisTest/Download")
        val r4 = ask("Perché il mio telefono è lento?")
        val r5 = ask("Trovami le foto di montagna")
        val r6 = ask("Trova la bolletta della luce")

        // Esegue davvero il primo piano e controlla il risultato
        r1.plan?.let { plan ->
            val res = FileOps.execute(ctx, plan, "Test LLM")
            TestUtil.report("LLM", "Esecuzione piano 1: ${res.done} ok, ${res.failed} falliti. PDF in PDF/: ${Storage.file("JarvisTest/Download/PDF").list()?.toList()}")
        }

        // Visione: già verificata (descrizione corretta di una foto), esclusa qui perché nell'emulatore
        // senza GPU richiede circa 40 minuti. Si attiva con il file /sdcard/JarvisModel/vision.
        if (File(Storage.root, "JarvisModel/vision").exists()) {
            val img = File(ctx.cacheDir, "obama.jpg").apply { writeBytes(TestUtil.asset("obama.jpg")) }
            val small = File(ctx.cacheDir, "obama_small.jpg")
            small.outputStream().use { com.jarvis.files.photos.BitmapLoader.load(img, 512)!!.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            val t0 = System.currentTimeMillis()
            val cap = LocalAi.describeImage(ctx, small, "Descrivi questa foto in italiano in massimo 25 parole.")
            TestUtil.report("LLM", "Visione [${(System.currentTimeMillis() - t0) / 1000}s] descrizione=$cap")
        }

        Storage.file("JarvisTest").deleteRecursively()
        assertTrue("il modello deve proporre spostamenti per i PDF", (r1.plan?.moves ?: 0) >= 2)
        assertTrue(r4.reply.length > 20)
        assertTrue(r5.photoSearch != null || r5.reply.isNotBlank())
        assertTrue(r2.plan != null || r2.questions.isNotEmpty())
        assertTrue(r3.plan != null || r3.questions.isNotEmpty())
        assertTrue(r6.fileQuery != null || r6.reply.isNotBlank())
    }
}
