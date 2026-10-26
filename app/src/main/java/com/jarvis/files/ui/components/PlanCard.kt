package com.jarvis.files.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jarvis.files.core.Op
import com.jarvis.files.core.Plan
import com.jarvis.files.ui.theme.J

/** Tutte le operazioni si possono controllare e selezionare, anche con migliaia di file. */
@Composable
fun PlanCard(plan: Plan, onConfirm: (Plan) -> Unit, onCancel: () -> Unit, confirmLabel: String = "Esegui", enabled: Boolean = true) {
    val excluded = remember(plan) { mutableStateListOf<Int>() }
    val selected = Plan(plan.ops.filterIndexed { i, _ -> i !in excluded }, plan.warnings)
    var review by remember(plan) { mutableStateOf(false) }
    fun toggle(i: Int) { if (i in excluded) excluded.remove(i) else excluded.add(i) }
    GlassCard(Modifier.fillMaxWidth(), highlight = true, padding = PaddingValues(16.dp)) {
        Text("CONTROLLA PRIMA DI CONFERMARE", style = MaterialTheme.typography.labelSmall, color = J.Text)
        Spacer(Modifier.height(8.dp))
        Text(selected.summary(), style = MaterialTheme.typography.titleMedium, color = J.Text)
        plan.warnings.forEach { Text(it, color = J.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        plan.ops.take(3).forEachIndexed { i, op -> OperationRow(op, i !in excluded, enabled) { toggle(i) } }
        if (plan.ops.size > 3) GhostButton("Controlla tutti i ${plan.ops.size} elementi", Modifier.fillMaxWidth(), enabled = enabled) { review = true }
        Spacer(Modifier.height(12.dp))
        GradientButton(confirmLabel, Modifier.fillMaxWidth(), Icons.Filled.CheckCircle, enabled && !selected.isEmpty) { onConfirm(selected) }
        Spacer(Modifier.height(8.dp))
        GhostButton("Annulla", Modifier.fillMaxWidth(), Icons.Filled.Close, J.Muted, enabled) { onCancel() }
        if (plan.trashes > 0) Text("Recuperabili dal cestino per 30 giorni. Lo spazio si libera svuotando il cestino.",
            style = MaterialTheme.typography.bodySmall, color = J.Muted, modifier = Modifier.padding(top = 12.dp))
    }
    if (review) Dialog(onDismissRequest = { review = false }) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = J.Card) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(16.dp)) {
                Text("Controlla i file", style = MaterialTheme.typography.titleLarge, color = J.Text)
                Text("${selected.ops.size} di ${plan.ops.size} selezionati", color = J.Muted, modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { excluded.clear() }, enabled = enabled) { Text("Tutti") }
                    TextButton(onClick = { excluded.clear(); excluded.addAll(plan.ops.indices) }, enabled = enabled) { Text("Nessuno") }
                }
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(plan.ops) { i, op -> OperationRow(op, i !in excluded, enabled) { toggle(i) } }
                }
                GradientButton("Fine", Modifier.fillMaxWidth()) { review = false }
            }
        }
    }
}

@Composable
private fun OperationRow(op: Op, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled, Role.Checkbox) { onToggle() }.heightIn(min = 52.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(op.label, color = if (checked) J.Text else J.Muted, style = MaterialTheme.typography.bodySmall)
            if (op is Op.Trash) Text(com.jarvis.files.core.Fmt.size(op.size), color = J.Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
