package com.jarvis.files.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.files.core.Op
import com.jarvis.files.core.Plan
import com.jarvis.files.ui.theme.J

/** Anteprima di un piano: l'utente può escludere singole operazioni prima di confermare. */
@Composable
fun PlanCard(plan: Plan, onConfirm: (Plan) -> Unit, onCancel: () -> Unit, confirmLabel: String = "Esegui", enabled: Boolean = true) {
    val excluded = remember(plan) { mutableStateListOf<Int>() }
    val selectedPlan = Plan(plan.ops.filterIndexed { i, _ -> i !in excluded }, plan.warnings)
    var expanded by remember(plan) { mutableStateOf(plan.ops.size <= 8) }
    GlassCard(Modifier.fillMaxWidth(), highlight = true) {
        Text("ANTEPRIMA", style = MaterialTheme.typography.labelSmall, color = J.Cyan)
        Spacer(Modifier.height(4.dp))
        Text(selectedPlan.summary(), style = MaterialTheme.typography.titleMedium, color = J.Text)
        plan.warnings.take(3).forEach {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.Warning, null, tint = J.Amber, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = J.Amber, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        val shown = if (expanded) plan.ops.withIndex().take(400) else plan.ops.withIndex().take(6)
        shown.forEach { (i, op) ->
            val off = i in excluded
            Row(
                Modifier.fillMaxWidth().clickable(enabled = enabled) { if (off) excluded.remove(i) else excluded.add(i) }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (off) Icons.Filled.RadioButtonUnchecked else Icons.Filled.CheckCircle, null,
                    tint = if (off) J.Muted else J.Electric, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Icon(
                    when (op) { is Op.Mkdir -> Icons.Filled.CreateNewFolder; is Op.Move -> Icons.Filled.DriveFileMove; is Op.Trash -> Icons.Filled.DeleteOutline },
                    null, tint = if (op is Op.Trash) J.Red else J.Cyan, modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(op.label, style = MaterialTheme.typography.bodySmall, color = if (off) J.Muted else J.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!expanded && plan.ops.size > 6) {
            Text("Mostra tutte le ${plan.ops.size} operazioni", color = J.Cyan, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { expanded = true }.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton(confirmLabel, Modifier.fillMaxWidth(), Icons.Filled.CheckCircle, enabled = enabled && !selectedPlan.isEmpty) {
                onConfirm(selectedPlan)
            }
            GhostButton("Annulla", icon = Icons.Filled.Close, color = J.Muted, onClick = onCancel)
        }
        if (plan.trashes > 0) {
            Text("I file eliminati restano nel cestino di Jarvis per 30 giorni.", style = MaterialTheme.typography.bodySmall,
                color = J.Muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

