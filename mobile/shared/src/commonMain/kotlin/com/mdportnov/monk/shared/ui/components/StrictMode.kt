package com.mdportnov.monk.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.data.nextMidnightMillis
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.ui.Motion

/** What the person reached for: a fixed end, or the sheet with a slider. */
enum class StrictPick { Midnight, Hour, Custom }

/**
 * The one way into strict mode, shared by the status card and Settings. A fixed pick goes
 * straight to the confirmation; [StrictPick.Custom] opens a sheet with all three options first.
 * The end time is fixed on confirm, so a dialog left open never locks for less than it said.
 */
@Composable
fun StrictModeFlow(store: MonkStore, config: MonkConfig, now: Long, pick: StrictPick, onDone: () -> Unit) {
    var confirmMinutes by rememberSaveable { mutableStateOf(pick.minutesOrNull()) }
    var choosing by rememberSaveable { mutableStateOf(pick == StrictPick.Custom) }
    if (choosing) {
        StrictChooser(now, onPick = { minutes -> confirmMinutes = minutes }, onDismiss = { choosing = false; if (confirmMinutes == null) onDone() })
    }
    confirmMinutes?.let { minutes ->
        StrictConfirm(store, config, minutes, now, onDone = { confirmMinutes = null; onDone() })
    }
}

@Composable
private fun StrictChooser(now: Long, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val s = strings
    MonkSheet(onDismiss = onDismiss) { hide ->
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(s.strictChooseTitle, style = MaterialTheme.typography.headlineSmall)
            Text(s.strictHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var preset by rememberSaveable { mutableStateOf(StrictPick.Midnight) }
            var customMinutes by rememberSaveable { mutableStateOf(180) }
            val h = rememberHaptics()
            Segments(
                options = listOf(StrictPick.Midnight to s.strictUntilMidnight, StrictPick.Hour to s.strict1h, StrictPick.Custom to s.strictCustom),
                selected = preset,
                onSelect = { preset = it },
            )
            AnimatedVisibility(visible = preset == StrictPick.Custom, enter = Motion.reveal(), exit = Motion.conceal()) {
                Column {
                    var draft by remember(customMinutes) { mutableStateOf(customMinutes.toFloat()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.forDuration(s.duration(draft.toLong() * 60_000L)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Pill(formatClock(nowMillis() + draft.toLong() * 60_000L), MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = draft,
                        onValueChange = { v -> val next = ((v / 15f).toInt() * 15).toFloat(); if (next != draft) h.tick(); draft = next },
                        onValueChangeFinished = { h.select(); customMinutes = draft.toInt() },
                        valueRange = 15f..(24 * 60).toFloat(),
                        steps = 24 * 4 - 2,
                    )
                }
            }
            val minutes = preset.minutesOrNull() ?: customMinutes
            Button(
                onClick = { h.select(); onPick(minutes); hide() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.strictStart(formatClock(strictEnd(minutes, now))))
            }
        }
    }
}

@Composable
private fun StrictConfirm(store: MonkStore, config: MonkConfig, minutes: Int, now: Long, onDone: () -> Unit) {
    val s = strings
    val haptic = rememberHaptics()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.strictConfirmTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.strictConfirmBody(formatClock(strictEnd(minutes, now))))
                if (config.isPaused(now)) Hint(s.endsBreakNote)
                if (config.isOff(now)) Hint(s.turnsOnNote)
            }
        },
        confirmButton = { TextButton(onClick = { haptic.confirm(); store.enableStrict(strictEnd(minutes, nowMillis())); onDone() }) { Text(s.confirm) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.cancel) } },
    )
}

private const val UNTIL_MIDNIGHT = -1

private fun StrictPick.minutesOrNull(): Int? = when (this) {
    StrictPick.Midnight -> UNTIL_MIDNIGHT
    StrictPick.Hour -> 60
    StrictPick.Custom -> null
}

private fun strictEnd(minutes: Int, now: Long) = if (minutes == UNTIL_MIDNIGHT) nextMidnightMillis() else now + minutes * 60_000L
