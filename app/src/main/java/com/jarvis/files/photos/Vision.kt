package com.jarvis.files.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Carica una foto ridotta e già raddrizzata secondo l'EXIF. */
object BitmapLoader {
    fun load(file: File, maxSide: Int): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        val rot = runCatching {
            when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rot == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
            .also { if (it != bmp) bmp.recycle() }
    }.getOrNull()
}

/** Embedding dei volti con FaceNet (512 valori, normalizzati). Gira tutto sul telefono. */
class FaceEmbedder(c: Context) : AutoCloseable {
    private val interpreter: Interpreter

    init {
        val fd = c.assets.openFd("facenet_512.tflite")
        val buf = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(buf, Interpreter.Options().apply { numThreads = 4 })
    }

    fun embed(face: Bitmap): FloatArray {
        val size = 160
        val scaled = Bitmap.createScaledBitmap(face, size, size, true)
        val px = IntArray(size * size)
        scaled.getPixels(px, 0, size, 0, 0, size, size)
        if (scaled != face) scaled.recycle()
        val vals = FloatArray(size * size * 3)
        var k = 0
        for (p in px) {
            vals[k++] = ((p shr 16) and 0xFF).toFloat()
            vals[k++] = ((p shr 8) and 0xFF).toFloat()
            vals[k++] = (p and 0xFF).toFloat()
        }
        // Standardizzazione per immagine: (x - media) / dev. standard
        val mean = vals.average().toFloat()
        var sq = 0.0
        for (v in vals) sq += (v - mean) * (v - mean)
        val std = max(sqrt(sq / vals.size).toFloat(), 1f / sqrt(vals.size.toFloat()))
        val input = ByteBuffer.allocateDirect(vals.size * 4).order(ByteOrder.nativeOrder())
        for (v in vals) input.putFloat((v - mean) / std)
        input.rewind()
        val out = Array(1) { FloatArray(512) }
        interpreter.run(input, out)
        return normalize(out[0])
    }

    override fun close() = interpreter.close()

    companion object {
        fun normalize(v: FloatArray): FloatArray {
            var n = 0f
            for (x in v) n += x * x
            n = sqrt(n).coerceAtLeast(1e-6f)
            return FloatArray(v.size) { v[it] / n }
        }

        fun cosine(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            for (i in a.indices) s += a[i] * b[i]
            return s
        }
    }
}

data class DetectedFace(val box: IntArray, val emb: FloatArray)
data class VisionResult(val labels: List<Pair<String, Float>>, val faces: List<DetectedFace>, val dark: Float, val blur: Float)

/** Analisi rapida: oggetti/scene (ML Kit), volti (ML Kit + FaceNet), luminosità e nitidezza. */
class VisionPipeline(c: Context) : AutoCloseable {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build())
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setMinFaceSize(0.08f)
            .build()
    )
    private val embedder = FaceEmbedder(c)

    fun analyze(bmp: Bitmap): VisionResult {
        val img = InputImage.fromBitmap(bmp, 0)
        val labels = runCatching { Tasks.await(labeler.process(img)) }.getOrDefault(emptyList())
            .map { it.text to it.confidence }
        val faces = runCatching { Tasks.await(detector.process(img)) }.getOrDefault(emptyList())
            .filter { it.boundingBox.width() >= 40 && it.boundingBox.height() >= 40 }
            .take(12)
            .mapNotNull { f ->
                val r = f.boundingBox
                val m = (r.width() * 0.15f).toInt()
                val l = max(0, r.left - m); val t = max(0, r.top - m)
                val rr = min(bmp.width, r.right + m); val b = min(bmp.height, r.bottom + m)
                if (rr - l < 20 || b - t < 20) return@mapNotNull null
                val crop = Bitmap.createBitmap(bmp, l, t, rr - l, b - t)
                val emb = embedder.embed(crop)
                crop.recycle()
                DetectedFace(intArrayOf(l, t, rr, b), emb)
            }
        val (dark, blur) = quality(bmp)
        return VisionResult(labels, faces, dark, blur)
    }

    /** Luminosità media (0-255) e varianza del Laplaciano (bassa = sfocata) su una versione 256 px. */
    private fun quality(src: Bitmap): Pair<Float, Float> {
        val w = 256
        val h = max(1, src.height * w / max(1, src.width))
        val s = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        s.getPixels(px, 0, w, 0, 0, w, h)
        if (s != src) s.recycle()
        val g = FloatArray(px.size) { val p = px[it]; 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF) }
        val mean = g.average().toFloat()
        var sum = 0.0; var sum2 = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val lap = 4 * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]
            sum += lap; sum2 += lap * lap; n++
        }
        val variance = if (n > 0) (sum2 / n - (sum / n) * (sum / n)).toFloat() else 0f
        return mean to variance
    }

    override fun close() {
        labeler.close(); detector.close(); embedder.close()
    }
}
