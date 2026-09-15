package com.mdportnov.monk.shared.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.data.localMoment
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.Routine
import com.mdportnov.monk.shared.model.RoutineMode
import com.mdportnov.monk.shared.model.RoutineWindow
import com.mdportnov.monk.shared.ui.components.DayToggleRow
import com.mdportnov.monk.shared.ui.components.Hint
import com.mdportnov.monk.shared.ui.components.MonkSheet
import com.mdportnov.monk.shared.ui.components.Pill
import com.mdportnov.monk.shared.ui.components.Segments
import com.mdportnov.monk.shared.ui.components.TimePickerDialog
import com.mdportnov.monk.shared.ui.components.formatMinute
import com.mdportnov.monk.shared.ui.components.rememberHaptics

/**
 * A routine's face: its emoji on a tinted disc, or a drawn mark when it has none. The same
 * shape everywhere — list row, chip, status card, pause screen — so one glance identifies it.
 */
@Composable
fun RoutineFace(routine: Routine, size: Dp = 40.dp, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier.size(size).background(tint.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (routine.emoji.isNotEmpty()) {
            Text(routine.emoji, fontSize = (size.value * 0.5f).sp, maxLines = 1)
        } else {
            Icon(Icons.Outlined.AutoAwesome, null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** What a routine is doing this minute, in the words a row uses. Null when it is simply on and idle. */
@Composable
fun routineStateLine(store: MonkStore, config: MonkConfig, routine: Routine, now: Long): String? {
    val s = strings
    val m = localMoment()
    val run = config.activeRun(now)
    if (run?.routineId == routine.id) return s.routineRunsUntil(formatClock(run.until))
    // Nothing for "off": every place this line appears has the switch beside it already.
    if (!routine.enabled) return null
    if (!routine.isOpen(m.dayIso, m.minuteOfDay)) return null
    // Its hours have come round, but something above it has the whole list: the master switch,
    // or a break it does not hold through. Saying "active until 07:00" there is the app telling
    // the user they are protected while the gate lets every app through.
    if (!config.routineHolds(routine, now)) {
        return if (!config.enabled) s.routineHeldOffBySwitch else s.routineHeldOffByBreak
    }
    val until = store.routineEndsAt(routine)
    return if (until == null) s.routineOpenNow else s.routineOpenUntil(formatClock(until))
}

/**
 * Starting a routine by hand, or lengthening the one already going. The sheet is the whole
 * confirmation: it says in as many words that this cannot be stopped, and the button under that
 * sentence is the only way past it — the same bargain a focus session has always offered.
 */
@Composable
fun RoutineStartSheet(store: MonkStore, routine: Routine, onDismiss: () -> Unit) {
    val s = strings
    val haptics = rememberHaptics()
    val now = nowMillis()
    val config = store.config.value
    val live = config.activeRun(now)
    val extending = live?.routineId == routine.id
    val blockedBy = live?.takeIf { it.routineId != routine.id }?.let { config.routine(it.routineId) }
    val covered = store.appsCovered(routine).size
    var choice by rememberSaveable(routine.id) { mutableStateOf(Choice.of(routine.manualMinutes)) }
    var custom by rememberSaveable(routine.id) { mutableStateOf(routine.manualMinutes) }
    val minutes = if (choice == Choice.Custom) custom else choice.minutes
    val until = now + minutes * 60_000L
    // Extending only ever means "longer than it already is"; the floor keeps that honest.
    val tooShort = live != null && extending && until <= live.until
    val nothingToDo = routine.coversNothing || covered == 0
    val canStart = blockedBy == null && !tooShort && !nothingToDo && routine.enabled

    MonkSheet(onDismiss = onDismiss) { hide ->
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RoutineFace(routine, 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(s.routineName(routine), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        s.routineSummary(routine, covered),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                if (routine.mode == RoutineMode.BLOCK) s.routineBlockHint else s.routinePauseHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                blockedBy != null -> Hint(s.routineAlreadyRunning(s.routineName(blockedBy)))
                !routine.enabled -> Hint(s.routineNeedsOn)
                nothingToDo -> Hint(s.routineCoversNothingNote(s.routineName(routine)))
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(s.routineHowLong, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Segments(
                            options = Choice.entries.map { c ->
                                c to if (c == Choice.Custom) s.strictCustom else s.duration(c.minutes * 60_000L)
                            },
                            selected = choice,
                        ) { choice = it }
                        if (choice == Choice.Custom) {
                            var draft by remember(custom) { mutableStateOf(custom.toFloat()) }
                            val h = rememberHaptics()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.forDuration(s.duration(draft.toLong() * 60_000L)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Pill(formatClock(now + draft.toLong() * 60_000L), MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = draft,
                                onValueChange = { v -> val next = ((v / 5f).toInt() * 5).toFloat(); if (next != draft) h.tick(); draft = next },
                                onValueChangeFinished = { h.select(); custom = draft.toInt() },
                                valueRange = Routine.MIN_MANUAL_MINUTES.toFloat()..Routine.MAX_MANUAL_MINUTES.toFloat(),
                            )
                        }
                    }
                    Text(
                        s.routineStartBody(s.routineName(routine), formatClock(until)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (tooShort) Hint(s.routineRunsUntil(formatClock(live.until)))
                    if (config.isPaused(now)) Hint(s.endsBreakNote)
                    if (!config.enabled) Hint(s.turnsOnNote)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptics.confirm()
                        // The store is the one that says no: it knows about strict mode, a
                        // session already running and a routine that covers nothing.
                        if (store.startRoutine(routine.id, until)) hide()
                    },
                    enabled = canStart,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(if (extending) s.routineExtend else s.start) }
                OutlinedButton(onClick = hide, modifier = Modifier.heightIn(min = 48.dp)) { Text(s.cancel) }
            }
        }
    }
}

private enum class Choice(val minutes: Int) {
    M15(15), M30(30), M60(60), M120(120), Custom(0);

    companion object {
        fun of(minutes: Int) = entries.firstOrNull { it.minutes == minutes && it != Custom } ?: Custom
    }
}

/** Create or edit one set of hours. The routine carries the verdict, so a window is only time. */
@Composable
fun WindowEditorDialog(
    initial: RoutineWindow,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (RoutineWindow) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val s = strings
    var window by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) s.windowNew else s.windowEdit) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DayToggleRow(s.dayShort, window.days, enabled = true, onToggle = { d, on ->
                    window = window.copy(days = if (on) window.days + d else window.days - d)
                })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.weight(1f)) { Text("${s.from} ${formatMinute(window.startMinute)}") }
                    OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.weight(1f)) { Text("${s.to} ${formatMinute(window.endMinute)}") }
                }
                Hint(
                    when {
                        window.allDay -> s.scheduleAllDay
                        window.crossesMidnight -> s.ruleNextDay
                        else -> s.ruleSameDay
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(window.repaired()) }) { Text(s.done) } },
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
            minuteOfDay = if (which == "start") window.startMinute else window.endMinute,
            confirmLabel = s.done,
            cancelLabel = s.cancel,
            onDismiss = { picking = null },
            onConfirm = { m ->
                window = if (which == "start") window.copy(startMinute = m) else window.copy(endMinute = m)
                picking = null
            },
        )
    }
}

/**
 * The sign: a grid to tap and nothing to type. A keyboard would work too, but on Android the
 * emoji key is two taps deep and half the keyboards hand back a letter instead, so the grid is
 * the path that always works; whatever is chosen is still run through the cluster scanner.
 */
@Composable
fun EmojiPicker(selected: String, enabled: Boolean, onPick: (String) -> Unit) {
    val h = rememberHaptics()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.mdportnov.monk.shared.model.Emoji.suggestions.forEach { emoji ->
            val on = emoji == selected
            Surface(
                onClick = { h.select(); onPick(emoji) },
                enabled = enabled,
                shape = CircleShape,
                color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(44.dp).then(
                    if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
                ),
            ) {
                Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp, maxLines = 1) }
            }
        }
    }
}

/**
 * One app in a routine's scope. The whole row is the target, and the checkbox only reports — a
 * row that looked tappable but answered only in a 24 dp square is the thing people miss.
 */
@Composable
fun AppPickRow(label: String, checked: Boolean, note: String?, icon: @Composable () -> Unit, onToggle: (Boolean) -> Unit) {
    val h = rememberHaptics()
    Surface(onClick = { h.toggle(!checked); onToggle(!checked) }, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon()
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}

