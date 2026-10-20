package com.jarvis.files.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.files.ui.theme.J

/**
 * Guida per "Accesso ai dati di utilizzo". Sulle app installate da APK Android blocca il permesso
 * ("All'app è stato negato l'accesso") finché non si consentono le impostazioni con limitazioni.
 */
@Composable
fun UsageAccessGuide(onGrant: () -> Unit) {
    val ctx = LocalContext.current
    GlassCard(Modifier.fillMaxWidth(), highlight = true) {
        Text("Serve l'accesso ai dati di utilizzo", color = J.Text, style = MaterialTheme.typography.titleMedium)
        Text(
            "Così Jarvis vede quanto pesa la cache di ogni app e quali app usi di più. Le app installate da file APK " +
                "sono bloccate da Android finché non le sblocchi una volta:",
            color = J.Muted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        listOf(
            "Tocca \"Apri Info app\" qui sotto",
            "In alto a destra tocca i tre puntini ⋮",
            "Scegli \"Consenti impostazioni con limitazioni\" e conferma con l'impronta",
            "Torna qui, tocca \"Concedi accesso\" e attiva Jarvis Files",
        ).forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(J.Blue), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(10.dp))
                Text(step, color = J.Text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Se nel menu ⋮ la voce non c'è: prima tocca \"Concedi accesso\" e prova ad attivare l'interruttore una volta " +
                "(Android mostra l'avviso \"accesso negato\"), poi torna su Info app: la voce compare. " +
                "Se hai attivo \"Protezione avanzata\" di Android lo sblocco non è possibile: la pulizia funziona comunque, " +
                "vedrai solo meno dettagli.",
            color = J.Muted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Apri Info app", icon = Icons.Filled.Info) {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            GradientButton("Concedi accesso", icon = Icons.Filled.LockOpen, onClick = onGrant)
        }
    }
}
