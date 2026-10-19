package com.jarvis.files.security

import android.Manifest
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.jarvis.files.core.DeviceInfo
import com.jarvis.files.core.FileIndex
import com.jarvis.files.core.Prefs
import com.jarvis.files.core.Stats
import com.jarvis.files.core.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class Severity(val label: String, val weight: Int) { HIGH("Alto", 3), MEDIUM("Medio", 2), LOW("Basso", 1), INFO("Info", 0) }

data class Finding(
    val severity: Severity,
    val title: String,
    val detail: String,
    val pkg: String? = null,
    val file: String? = null,
    val action: Intent? = null,
    val actionLabel: String? = null,
)

/**
 * Controllo sicurezza. Non è un antivirus a firme in tempo reale (Android non lo consente alle app normali):
 * analizza segnali di rischio concreti e, se configurato, verifica le app/APK sospette su VirusTotal.
 */
object SecurityScanner {

    private val playInstallers = setOf("com.android.vending", "com.google.android.packageinstaller.store", "com.google.android.feedback")
    private val trustedStores = playInstallers + setOf("com.sec.android.app.samsungapps", "com.huawei.appmarket", "com.amazon.venezia", "org.fdroid.fdroid")

    suspend fun scan(c: Context, onProgress: (String) -> Unit = {}): List<Finding> = withContext(Dispatchers.IO) {
        val out = ArrayList<Finding>()
        onProgress("Impostazioni del telefono…")
        out += deviceChecks(c)
        onProgress("App installate…")
        out += appChecks(c)
        onProgress("File APK in memoria…")
        out += apkFiles(c)
        val key = Prefs(c).virusTotalKey
        if (key.isNotBlank() && com.jarvis.files.ai.AiRouter.isOnline(c)) {
            onProgress("Verifica su VirusTotal…")
            out += virusTotal(c, key, out)
        }
        Stats.addThreats(c, out.count { it.severity == Severity.HIGH })
        out.sortedByDescending { it.severity.weight }
    }

    private fun deviceChecks(c: Context): List<Finding> {
        val out = ArrayList<Finding>()
        val km = c.getSystemService(KeyguardManager::class.java)
        if (!km.isDeviceSecure) out += Finding(
            Severity.HIGH, "Nessun blocco schermo",
            "Chiunque prenda il telefono può accedere a tutto. Imposta PIN, impronta o sequenza.",
            action = Intent(Settings.ACTION_SECURITY_SETTINGS), actionLabel = "Imposta blocco",
        )
        DeviceInfo.securityPatchAgeDays()?.let { days ->
            if (days > 120) out += Finding(
                Severity.MEDIUM, "Aggiornamenti di sicurezza vecchi",
                "L'ultima patch ha $days giorni. Controlla gli aggiornamenti di sistema.",
                action = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS), actionLabel = "Controlla",
            )
        }
        val adb = Settings.Global.getInt(c.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        if (adb) out += Finding(
            Severity.MEDIUM, "Debug USB attivo",
            "Permette di controllare il telefono via cavo. Disattivalo se non sei uno sviluppatore.",
            action = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), actionLabel = "Opzioni sviluppatore",
        )
        val accessibility = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        accessibility.split(':').filter { it.isNotBlank() }.forEach { svc ->
            val pkg = svc.substringBefore('/')
            if (pkg.startsWith("com.google.") || pkg.startsWith("com.android.")) return@forEach
            out += Finding(
                Severity.MEDIUM, "Accessibilità: ${label(c, pkg)}",
                "Questa app può leggere lo schermo e premere al posto tuo. È normale solo per app che conosci (password manager, automazioni).",
                pkg = pkg, action = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), actionLabel = "Gestisci",
            )
        }
        val dpm = c.getSystemService(DevicePolicyManager::class.java)
        dpm.activeAdmins?.filterNot { it.packageName.startsWith("com.google.") || it.packageName.startsWith("com.android.") }?.forEach { cn ->
            out += Finding(
                Severity.MEDIUM, "Amministratore dispositivo: ${label(c, cn.packageName)}",
                "Può bloccare il telefono o impedire la disinstallazione. Normale solo per app aziendali o antifurto.",
                pkg = cn.packageName, action = Intent(Settings.ACTION_SECURITY_SETTINGS), actionLabel = "Gestisci",
            )
        }
        val listeners = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners").orEmpty()
        listeners.split(':').map { it.substringBefore('/') }.filter { it.isNotBlank() && !it.startsWith("com.google.") && !it.startsWith("com.android.") }
            .distinct().forEach { pkg ->
                out += Finding(Severity.LOW, "Legge le notifiche: ${label(c, pkg)}",
                    "Vede il testo di tutte le notifiche (anche codici SMS). Va bene solo se ti serve (smartwatch, automazioni).",
                    pkg = pkg, action = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), actionLabel = "Gestisci")
            }
        return out
    }

    private fun label(c: Context, pkg: String) =
        runCatching { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private val riskyCombos = listOf(
        setOf(Manifest.permission.READ_SMS, Manifest.permission.INTERNET) to "legge gli SMS (anche codici bancari)",
        setOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.INTERNET) to "intercetta gli SMS in arrivo",
        setOf(Manifest.permission.SYSTEM_ALERT_WINDOW, Manifest.permission.INTERNET) to "può disegnare sopra le altre app",
        setOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.INTERNET) to "legge il registro chiamate",
        setOf(Manifest.permission.REQUEST_INSTALL_PACKAGES) to "può installare altre app",
    )

    private fun appChecks(c: Context): List<Finding> {
        val pm = c.packageManager
        val out = ArrayList<Finding>()
        val pkgs: List<PackageInfo> = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        for (p in pkgs) {
            val ai = p.applicationInfo ?: continue
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            if (p.packageName == c.packageName) continue
            val installer = runCatching { pm.getInstallSourceInfo(p.packageName).installingPackageName }.getOrNull()
            val sideloaded = installer == null || installer !in trustedStores
            val granted = p.requestedPermissions?.filterIndexed { i, _ ->
                (p.requestedPermissionsFlags?.getOrNull(i) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            }?.toSet() ?: emptySet()
            val requested = p.requestedPermissions?.toSet() ?: emptySet()
            val risks = riskyCombos.filter { (perms, _) ->
                perms.all { it in granted || (it == Manifest.permission.INTERNET && it in requested) || (it == Manifest.permission.REQUEST_INSTALL_PACKAGES && it in requested) }
            }.map { it.second }
            val noLauncher = pm.getLaunchIntentForPackage(p.packageName) == null
            val name = pm.getApplicationLabel(ai).toString()
            if (sideloaded) {
                val sev = if (risks.isNotEmpty() || noLauncher) Severity.HIGH else Severity.MEDIUM
                out += Finding(
                    sev, "App installata fuori dal Play Store: $name",
                    buildString {
                        append("Origine: ${installer ?: "file APK o sconosciuta"}.")
                        if (risks.isNotEmpty()) append(" Inoltre ${risks.joinToString(", ")}.")
                        if (noLauncher) append(" Non ha un'icona nel menu: comportamento tipico dei programmi nascosti.")
                        append(" Se non la riconosci, disinstallala.")
                    },
                    pkg = p.packageName,
                    action = Intent(Intent.ACTION_DELETE, Uri.parse("package:${p.packageName}")), actionLabel = "Disinstalla",
                )
            } else if (risks.isNotEmpty() && noLauncher) {
                out += Finding(Severity.MEDIUM, "App nascosta con permessi delicati: $name",
                    "Non ha icona e ${risks.joinToString(", ")}.", pkg = p.packageName,
                    action = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${p.packageName}")), actionLabel = "Dettagli")
            }
        }
        return out
    }

    private fun apkFiles(c: Context): List<Finding> {
        val pm = c.packageManager
        return FileIndex.ensure().filter { it.ext == "apk" }.map { e ->
            val info = runCatching { pm.getPackageArchiveInfo(Storage.file(e.rel).absolutePath, 0) }.getOrNull()
            Finding(
                Severity.LOW, "File di installazione: ${e.name}",
                "Pacchetto ${info?.packageName ?: "illeggibile"}. Installa solo APK di cui ti fidi; se non serve, eliminalo.",
                pkg = info?.packageName, file = e.rel,
            )
        }
    }

    private fun sha256(f: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(1 shl 16); while (true) { val n = i.read(b); if (n <= 0) break; md.update(b, 0, n) } }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** Interroga VirusTotal (chiave gratuita: 4 richieste al minuto) per app esterne al Play Store e APK. */
    private suspend fun virusTotal(c: Context, key: String, found: List<Finding>): List<Finding> {
        val pm = c.packageManager
        val targets = LinkedHashMap<String, String>() // percorso file -> nome
        found.filter { it.title.startsWith("App installata fuori") && it.pkg != null }.forEach { f ->
            runCatching { pm.getApplicationInfo(f.pkg!!, 0).sourceDir }.getOrNull()?.let { targets[it] = f.title.substringAfter(": ") }
        }
        found.filter { it.file != null }.forEach { targets[Storage.file(it.file!!).absolutePath] = it.file.substringAfterLast('/') }
        val out = ArrayList<Finding>()
        for ((i, entry) in targets.entries.take(12).withIndex()) {
            if (i > 0) delay(16_000) // rispetta il limite gratuito
            val hash = sha256(File(entry.key)) ?: continue
            val res = runCatching {
                val conn = (URL("https://www.virustotal.com/api/v3/files/$hash").openConnection() as HttpURLConnection).apply {
                    setRequestProperty("x-apikey", key); connectTimeout = 15_000; readTimeout = 30_000
                }
                if (conn.responseCode == 404) null
                else JSONObject(conn.inputStream.bufferedReader().readText())
                    .getJSONObject("data").getJSONObject("attributes").getJSONObject("last_analysis_stats")
            }
            val stats = res.getOrNull()
            when {
                res.isFailure -> {}
                stats == null -> out += Finding(Severity.INFO, "VirusTotal: ${entry.value}", "File mai analizzato da VirusTotal: nessun dato.")
                stats.optInt("malicious") > 0 -> out += Finding(
                    Severity.HIGH, "MINACCIA rilevata: ${entry.value}",
                    "${stats.optInt("malicious")} antivirus lo segnalano come dannoso. Disinstallalo/eliminalo subito.",
                )
                stats.optInt("suspicious") > 0 -> out += Finding(Severity.MEDIUM, "Sospetto: ${entry.value}", "${stats.optInt("suspicious")} antivirus lo considerano sospetto.")
                else -> out += Finding(Severity.INFO, "Pulito: ${entry.value}", "Nessun antivirus su VirusTotal lo segnala.")
            }
        }
        return out
    }
}
