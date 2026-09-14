package com.mdportnov.monk.shared.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.RuleMode
import com.mdportnov.monk.shared.model.TimeRule
import com.mdportnov.monk.shared.ui.components.DayToggleRow
import com.mdportnov.monk.shared.ui.components.Segments
import com.mdportnov.monk.shared.ui.components.Hint
import com.mdportnov.monk.shared.ui.components.TimePickerDialog
import com.mdportnov.monk.shared.ui.components.formatMinute

/** Create or edit one time rule. */
@Composable
fun RuleEditorDialog(
    initial: TimeRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (TimeRule) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val s = strings
    var rule by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) s.ruleNew else s.ruleEdit) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Segments(
                    options = listOf(RuleMode.BLOCK to s.ruleBlock, RuleMode.PAUSE to s.rulePause, RuleMode.FREE to s.ruleFree),
                    selected = rule.mode,
                ) { m -> rule = rule.copy(mode = m) }
                Hint(
                    when (rule.mode) {
                        RuleMode.BLOCK -> s.ruleBlockHint
                        RuleMode.PAUSE -> s.rulePauseHint
                        RuleMode.FREE -> s.ruleFreeHint
                    },
                )
                DayToggleRow(s.dayShort, rule.days, enabled = true, onToggle = { d, on -> rule = rule.copy(days = if (on) rule.days + d else rule.days - d) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.weight(1f)) { Text("${s.from} ${formatMinute(rule.startMinute)}") }
                    OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.weight(1f)) { Text("${s.to} ${formatMinute(rule.endMinute)}") }
                }
                if (rule.crossesMidnight) Hint(s.ruleNextDay) else Hint(s.ruleSameDay)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(rule) }) { Text(s.done) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(s.delete, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(s.cancel) }
            }
        },
    )
    picking?.let { which ->
        TimePickerDialog(
            title = if (which == "start") s.from else s.to,
            minuteOfDay = if (which == "start") rule.startMinute else rule.endMinute,
            confirmLabel = s.done,
            cancelLabel = s.cancel,
            onDismiss = { picking = null },
            onConfirm = { m -> rule = if (which == "start") rule.copy(startMinute = m) else rule.copy(endMinute = m); picking = null },
        )
    }
}

/** "Block · 06:00–09:00 → next day · Mo Tu We" */
@Composable
fun ruleSummary(rule: TimeRule): String {
    val s = strings
    val mode = when (rule.mode) { RuleMode.BLOCK -> s.ruleBlock; RuleMode.PAUSE -> s.rulePause; RuleMode.FREE -> s.ruleFree }
    val days = if (rule.days.size == 7) s.everyDay else rule.days.sorted().joinToString(" ") { s.dayShort[it - 1] }
    val arrow = if (rule.crossesMidnight) " ${s.ruleNextDayShort}" else ""
    return "$mode · ${formatMinute(rule.startMinute)}–${formatMinute(rule.endMinute)}$arrow · $days"
}
