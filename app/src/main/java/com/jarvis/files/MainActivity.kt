package com.jarvis.files

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jarvis.files.core.Notifier
import com.jarvis.files.core.Prefs
import com.jarvis.files.ui.JarvisRoot
import com.jarvis.files.ui.MainViewModel
import com.jarvis.files.ui.Sub
import com.jarvis.files.ui.Tab
import com.jarvis.files.ui.theme.JarvisTheme

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()
    private val unlocked = mutableStateOf(false)
    private var lastUnlock = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tema sempre chiaro come nel design: icone di stato scure
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        createPrompt()
        handleIntent(intent)
        setContent {
            JarvisTheme {
                JarvisRoot(vm, locked = Prefs(this).biometricLock && !unlocked.value, onUnlock = { authenticate() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshStatus()
        // Si riblocca se l'app è rimasta in background più di 2 minuti
        if (Prefs(this).biometricLock && System.currentTimeMillis() - lastUnlock > 120_000) {
            unlocked.value = false
            authenticate()
        }
    }

    override fun onPause() {
        super.onPause()
        if (unlocked.value) lastUnlock = System.currentTimeMillis()
    }

    private fun handleIntent(i: Intent?) {
        // File condivisi da altre app ("Condividi → Jarvis Files")
        when (i?.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                (i.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))?.let { vm.shared = listOf(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                i.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.let { vm.shared = it.toList() }
            }
        }
        when (i?.getStringExtra(Notifier.EXTRA_OPEN)) {
            "voice" -> vm.voiceOpen = true
            "files" -> { vm.tab = Tab.FILES; vm.sub = Sub.NONE }
            "sort_download" -> vm.openChatWith("Metti in ordine la cartella Download")
            "security" -> { vm.tab = Tab.MORE; vm.sub = Sub.SECURITY }
            "clean" -> { vm.tab = Tab.CLEAN; vm.sub = Sub.NONE }
            "photos" -> { vm.tab = Tab.PHOTOS; vm.sub = Sub.NONE }
            "chat" -> { vm.tab = Tab.CHAT; vm.sub = Sub.NONE }
            "home" -> { vm.tab = Tab.HOME; vm.sub = Sub.NONE }
        }
    }

    private var prompting = false
    private var prompt: BiometricPrompt? = null

    /** Il BiometricPrompt va creato in onCreate (prima che l'activity sia avviata). */
    private fun createPrompt() {
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                unlocked.value = true
                lastUnlock = System.currentTimeMillis()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
            }
        })
    }

    fun authenticate() {
        if (prompting || unlocked.value) return
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlocked.value = true // nessun blocco schermo configurato: non lasciare l'utente fuori
            return
        }
        prompting = true
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Jarvis Files")
            .setSubtitle("Sblocca con impronta o PIN")
            .setAllowedAuthenticators(authenticators)
            .build()
        runCatching { prompt!!.authenticate(info) }.onFailure { prompting = false }
    }
}
