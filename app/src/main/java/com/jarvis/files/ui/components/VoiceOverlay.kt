package com.jarvis.files.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VOICE_HINTS = listOf("Metti in ordine i Download", "Trovami le foto di montagna", "Libera spazio", "Perché il telefono è lento?")

/**
 * Ascolto vocale a schermo intero: Jarvis pulsa con la voce e mostra le parole in tempo reale.
 * Se il riconoscimento integrato non è disponibile usa quello di Google.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceOverlay(onResult: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var level by remember { mutableFloatStateOf(0f) }
    var text by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("Ti ascolto, signore…") }
    var listening by remember { mutableStateOf(false) }
    var hasMic by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var attempt by remember { mutableStateOf(0) }
    BackHandler(onBack = onClose)

    val fallback = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !t.isNullOrBlank()) onResult(t) else onClose()
    }
    fun launchFallback() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Dimmi cosa fare")
        runCatching { fallback.launch(i) }.onFailure { onClose() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasMic = ok
        if (!ok) launchFallback()
    }
    LaunchedEffect(Unit) { if (!hasMic) permission.launch(Manifest.permission.RECORD_AUDIO) }

    val available = remember { SpeechRecognizer.isRecognitionAvailable(ctx) }
    LaunchedEffect(available, hasMic) { if (hasMic && !available) launchFallback() }

    if (hasMic && available) {
        DisposableEffect(attempt) {
            val sr = SpeechRecognizer.createSpeechRecognizer(ctx)
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true; state = "Ti ascolto, signore…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false; level = 0f; state = "Elaboro…" }
                override fun onError(error: Int) {
                    listening = false; level = 0f
                    state = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Non ho sentito bene. Tocca il microfono e riprova."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Serve la connessione per capire la voce. Riprova."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Manca il permesso del microfono."
                        else -> "Non sono riuscito ad ascoltare (errore $error). Riprova."
                    }
                }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!t.isNullOrBlank()) onResult(t) else state = "Non ho capito. Tocca il microfono e riprova."
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) text = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            runCatching { sr.startListening(i) }.onFailure { state = "Riconoscimento vocale non disponibile." }
            onDispose { runCatching { sr.cancel(); sr.destroy() } }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF20B0C10), Color(0xFA16181F), Color(0xFF0B0C10))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopEnd)) {
            Icon(Icons.Filled.Close, "Chiudi", tint = Color.White)
        }
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ArcReactor(250.dp, active = true, level = level)
            Spacer(Modifier.height(28.dp))
            AnimatedContent(text.ifBlank { state }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "voice") { t ->
                Text(
                    t, color = Color.White, textAlign = TextAlign.Center,
                    fontSize = if (text.isBlank()) 22.sp else 26.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(24.dp))
            if (text.isBlank()) {
                Text("Prova a dire", color = Color(0xFF9A9FAD))
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VOICE_HINTS.forEach { h ->
                        Text(
                            "“$h”", color = Color.White,
                            modifier = Modifier.clip(CircleShape).background(Color(0x26FFFFFF)).clickable { onResult(h) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.size(68.dp).clip(CircleShape)
                    .background(if (listening) Brush.linearGradient(listOf(Color(0xFFFF6A2B), Color(0xFFFF3D2E))) else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF))))
                    .clickable { text = ""; attempt++ },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Mic, "Riascolta", tint = Color.White, modifier = Modifier.size(30.dp)) }
        }
    }
}
