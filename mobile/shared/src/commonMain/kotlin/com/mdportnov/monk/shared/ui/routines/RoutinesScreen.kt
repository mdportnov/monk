package com.mdportnov.monk.shared.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.Routine
import com.mdportnov.monk.shared.model.RoutineWindow
import com.mdportnov.monk.shared.ui.TopBarState
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.saveable.rememberSaveable
import com.mdportnov.monk.shared.ui.components.DayToggleRow
import com.mdportnov.monk.shared.ui.components.Hint
import com.mdportnov.monk.shared.ui.components.SettingRow
import com.mdportnov.monk.shared.ui.components.TimePickerDialog
import com.mdportnov.monk.shared.ui.components.formatMinute
import com.mdportnov.monk.shared.ui.components.HapticSwitch
import com.mdportnov.monk.shared.ui.components.SectionTitle
import com.mdportnov.monk.shared.ui.components.SettingBlock
import com.mdportnov.monk.shared.ui.components.SettingsDivider
import com.mdportnov.monk.shared.ui.components.SettingsGroup
import com.mdportnov.monk.shared.ui.components.glassTopBarInset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay

/**
 * Every routine in one list: the four that ship with Monk, then whatever the user made. A row
 * says what the routine does and what it is doing right now; the switch decides whether it keeps
 * its own hours at all, and the row itself opens it.
 */
@Composable
fun RoutinesScreen(
    store: MonkStore,
    onClose: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    topBar: TopBarState,
    hazeState: HazeState,
) {
    val s = strings
    val config by store.config.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = nowMillis() } }
    LaunchedEffect(config.run, config.pausedUntil) { now = nowMillis() }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) { if (notice != null) { delay(2600); notice = null } }
    var starting by remember { mutableStateOf<String?>(null) }
    var timePick by rememberSaveable { mutableStateOf<String?>(null) }

    SideEffect {
        topBar.set(
            title = s.routines,
            visible = true,
            level = 10,
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, s.back) } },
            actions = {
                val room = config.routines.size < Routine.MAX_ROUTINES
                IconButton(
                    enabled = room,
                    onClick = {
                        val made = newRoutine(config.routines)
                        // The store is the one that may still say no — strict mode does.
                        if (store.upsertRoutine(made)) onOpenRoutine(made.id) else notice = s.routineStrictRefused
                    },
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        s.newRoutine,
                        tint = if (room) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            Box(Modifier.padding(PaddingValues(top = glassTopBarInset())).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Hint(s.routinesHint)
                    if (notice != null) Hint(notice.orEmpty())
                    if (config.routines.size >= Routine.MAX_ROUTINES) Hint(s.routineCapReached(Routine.MAX_ROUTINES))

                    // The base hours live here rather than in Settings: they and the routines are
                    // one question — when does Monk act — and answering it in two places on two
                    // screens is what made them look like rival settings.
                    // Strict mode does not grey these out: switching the base hours off makes them
                    // every minute of the week, which is a tightening. The store decides, and
                    // refuses with a line, exactly as it does for a routine.
                    fun schedule(next: com.mdportnov.monk.shared.model.Schedule) {
                        if (!store.setSchedule(next)) notice = s.routineStrictRefused
                    }
                    SettingsGroup {
                        SettingRow(title = s.routineBaseHours, subtitle = s.routineBaseHoursHint, icon = Icons.Outlined.CalendarMonth) {
                            HapticSwitch(
                                checked = config.schedule.enabled,
                                onCheckedChange = { on -> schedule(config.schedule.copy(enabled = on)) },
                            )
                        }
                        if (config.schedule.enabled) {
                            SettingsDivider()
                            SettingBlock {
                                // At least one day stays selected: an empty set would silently pause protection forever.
                                DayToggleRow(s.dayShort, config.schedule.days, enabled = true, onToggle = { day, on ->
                                    schedule(config.schedule.copy(days = if (on) config.schedule.days + day else config.schedule.days - day))
                                })
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { timePick = "start" }, modifier = Modifier.weight(1f)) { Text("${s.from} ${formatMinute(config.schedule.startMinute)}") }
                                    OutlinedButton(onClick = { timePick = "end" }, modifier = Modifier.weight(1f)) { Text("${s.to} ${formatMinute(config.schedule.endMinute)}") }
                                }
                                Hint(if (config.schedule.endMinute <= config.schedule.startMinute) s.ruleNextDay else s.ruleSameDay)
                            }
                        } else {
                            SettingsDivider()
                            SettingBlock { Hint(s.scheduleHint) }
                        }
                    }
                    SectionTitle(s.routines)

                    val builtIn = config.routines.filter { it.builtIn }
                    val custom = config.routines.filter { !it.builtIn }

                    SettingsGroup {
                        builtIn.forEachIndexed { i, routine ->
                            key(routine.id) {
                                if (i > 0) SettingsDivider()
                                RoutineRow(store, config, routine, now, onOpen = { onOpenRoutine(routine.id) }, onStart = { starting = routine.id }) {
                                    notice = it
                                }
                            }
                        }
                    }

                    if (custom.isNotEmpty()) {
                        SectionTitle(s.routinesYours)
                        SettingsGroup {
                            custom.forEachIndexed { i, routine ->
                                key(routine.id) {
                                    if (i > 0) SettingsDivider()
                                    RoutineRow(store, config, routine, now, onOpen = { onOpenRoutine(routine.id) }, onStart = { starting = routine.id }) {
                                        notice = it
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    timePick?.let { which ->
        TimePickerDialog(
            title = if (which == "start") s.from else s.to,
            minuteOfDay = if (which == "start") config.schedule.startMinute else config.schedule.endMinute,
            confirmLabel = s.done,
            cancelLabel = s.cancel,
            onDismiss = { timePick = null },
            onConfirm = { m ->
                val next = if (which == "start") config.schedule.copy(startMinute = m) else config.schedule.copy(endMinute = m)
                if (!store.setSchedule(next)) notice = s.routineStrictRefused
                timePick = null
            },
        )
    }
    starting?.let { id ->
        val routine = config.routine(id)
        if (routine == null) starting = null
        else RoutineStartSheet(store, routine, onDismiss = { starting = null })
    }
}

@Composable
private fun RoutineRow(
    store: MonkStore,
    config: com.mdportnov.monk.shared.model.MonkConfig,
    routine: Routine,
    now: Long,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onRefused: (String) -> Unit,
) {
    val s = strings
    val covered = store.appsCovered(routine).size
    val state = routineStateLine(store, config, routine, now)
    val running = config.activeRun(now)?.routineId == routine.id
    Surface(onClick = onOpen, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RoutineFace(routine, 40.dp, if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(s.routineName(routine), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    s.routineSummary(routine, covered),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state != null) {
                    Text(
                        state,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (running || routine.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Starting one by hand is the common act, so it lives in the row rather than a page deeper.
            if (routine.enabled && !running) {
                IconButton(onClick = onStart) { Icon(Icons.Outlined.PlayArrow, s.routineStart, tint = MaterialTheme.colorScheme.primary) }
            }
            HapticSwitch(
                checked = routine.enabled,
                onCheckedChange = { on -> if (!store.setRoutineEnabled(routine.id, on)) onRefused(s.routineStrictRefused) },
            )
        }
    }
}

/**
 * A fresh routine: blocking, every app, one evening window to edit or drop — and switched off,
 * because a routine created with a default window and left half-edited would otherwise start
 * blocking that same evening. Switching it on is the moment the user says it is ready.
 */
private fun newRoutine(existing: List<Routine>): Routine {
    val id = "custom:" + nowMillis().toString()
    val taken = existing.map { it.id }.toSet()
    val unique = if (id in taken) "$id-${existing.size}" else id
    return Routine(
        id = unique,
        emoji = "✨",
        enabled = false,
        windows = listOf(RoutineWindow(id = nowMillis(), startMinute = 20 * 60, endMinute = 22 * 60)),
    )
}
