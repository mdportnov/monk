package com.mdportnov.monk.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import com.mdportnov.monk.shared.ui.Motion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Seven 40 dp circles, one per weekday: no check icon, so a two-letter label never wraps. */
@Composable
fun DayToggleRow(
    labels: List<String>,
    selected: Set<Int>,
    enabled: Boolean,
    onToggle: (dayIso: Int, on: Boolean) -> Unit,
    keepAtLeastOne: Boolean = true,
) {
    val h = rememberHaptics()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { i, label ->
            val day = i + 1
            val on = day in selected
            val canToggle = enabled && !(keepAtLeastOne && on && selected.size == 1)
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, Motion.color, label = "dayBg")
            val fg by animateColorAsState(if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, Motion.color, label = "dayFg")
            Surface(onClick = { h.toggle(!on); onToggle(day, !on) }, enabled = canToggle, shape = CircleShape, color = bg, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = if (enabled) 1f else 0.5f))
                }
            }
        }
    }
}

/** A proper clock-face picker; minute of day in, minute of day out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    minuteOfDay: Int,
    confirmLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
    )
}

fun formatMinute(minute: Int): String {
    val h = (minute / 60) % 24
    val m = minute % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
