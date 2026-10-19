package com.jarvis.files.core

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup delle chiavi API e delle impostazioni dell'AI in un file cifrato con password
 * (AES-256-GCM, chiave derivata con PBKDF2). Senza la password il file è illeggibile.
 */
object KeyBackup {
    private const val ITERATIONS = 150_000

    private val keys = listOf(
        "online_base", "online_key", "online_model", "vt_key", "user_name", "ai_mode", "local_repo",
    )

    private fun derive(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    /** Crea il file cifrato in Download/Jarvis Backup e lo restituisce. */
    fun export(c: Context, password: String): File {
        val p = Prefs(c)
        val data = JSONObject()
        keys.forEach { k -> p.getString(k).takeIf { it.isNotEmpty() }?.let { data.put(k, it) } }
        data.put("online_tpm", p.onlineTokenLimit)
        data.put("created", System.currentTimeMillis())
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        val enc = cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
        val out = JSONObject().put("app", "Jarvis Files").put("v", 1).put("salt", b64(salt)).put("iv", b64(iv)).put("data", b64(enc))
        val dir = Storage.file("Download/Jarvis Backup").apply { mkdirs() }
        val f = File(dir, "jarvis-chiavi-${Fmt.iso(System.currentTimeMillis())}.jarvisbak")
        f.writeText(out.toString(2))
        Storage.scanMedia(c, listOf(f))
        return f
    }

    class WrongPassword : Exception("Password errata o file danneggiato")

    /** Ripristina dal file scelto; restituisce quante impostazioni sono state recuperate. */
    fun import(c: Context, uri: Uri, password: String): Int {
        val text = c.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val o = JSONObject(text)
        require(o.optString("app") == "Jarvis Files") { "Non è un backup di Jarvis Files" }
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, derive(password, unb64(o.getString("salt"))), GCMParameterSpec(128, unb64(o.getString("iv"))))
            String(cipher.doFinal(unb64(o.getString("data"))), Charsets.UTF_8)
        } catch (e: Exception) {
            throw WrongPassword()
        }
        val data = JSONObject(plain)
        val p = Prefs(c)
        var n = 0
        keys.forEach { k -> if (data.has(k)) { p.putString(k, data.getString(k)); n++ } }
        if (data.has("online_tpm")) p.onlineTokenLimit = data.getInt("online_tpm")
        return n
    }
}
