package com.mdportnov.monk.shared.ui.routines

import com.mdportnov.monk.shared.ui.rememberNow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.Emoji
import com.mdportnov.monk.shared.model.Routine
import com.mdportnov.monk.shared.model.RoutineMode
import com.mdportnov.monk.shared.model.RoutineWindow
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.ui.LocalOpenRoute
import com.mdportnov.monk.shared.ui.Motion
import com.mdportnov.monk.shared.ui.Route
import com.mdportnov.monk.shared.ui.TopBarState
import com.mdportnov.monk.shared.ui.components.HapticSwitch
import com.mdportnov.monk.shared.ui.components.Hint
import com.mdportnov.monk.shared.ui.components.SectionTitle
import com.mdportnov.monk.shared.ui.components.Segments
import com.mdportnov.monk.shared.ui.components.SettingBlock
import com.mdportnov.monk.shared.ui.components.SettingRow
import com.mdportnov.monk.shared.ui.components.SettingsDivider
import com.mdportnov.monk.shared.ui.components.SettingsGroup
import com.mdportnov.monk.shared.ui.components.glassTopBarInset
import com.mdportnov.monk.shared.ui.components.rememberHaptics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay

/**
 * One routine, end to end. Every edit goes through the store, which is the only place that knows
 * whether strict mode or a session already running allows it; when it says no, the page says so
 * in a line rather than greying half of itself out — under strict mode tightening is still
 * allowed, and a page of dead controls would hide that.
 */
@Composable
fun RoutineDetailScreen(
    store: MonkStore,
    routineId: String,
    onClose: () -> Unit,
    topBar: TopBarState,
    hazeState: HazeState,
) {
    val s = strings
    val config by store.config.collectAsStateWithLifecycle()
    // Once deleted the page keeps its last state while it slides away.
    val live = config.routine(routineId)
    val last = remember { arrayOfNulls<Routine>(1) }
    LaunchedEffect(live == null) { if (live == null) onClose() }
    val routine = live?.also { last[0] = it } ?: last[0] ?: return

    val now = rememberNow(listOf(config.run?.until, config.pausedUntil))
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) { if (notice != null) { delay(3000); notice = null } }
    var editingWindow by remember { mutableStateOf<RoutineWindow?>(null) }
    var windowIsNew by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var starting by rememberSaveable { mutableStateOf(false) }

    val openRoute = LocalOpenRoute.current
    val running = config.activeRun(now)?.routineId == routine.id
    val haptics = rememberHaptics()
    val covered = store.appsCovered(routine)

    /** Every change in one place, so a refusal is explained the same way wherever it came from. */
    fun save(next: Routine) {
        if (store.upsertRoutine(next)) return
        haptics.reject()
        notice = if (running) s.routineRunningRefused else s.routineStrictRefused
    }

    SideEffect {
        topBar.set(
            title = s.routineName(routine),
            visible = true,
            level = 11,
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, s.back) } },
            actions = {
                if (!routine.builtIn) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.DeleteOutline, s.routineDelete, tint = MaterialTheme.colorScheme.error)
                    }
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RoutineFace(routine, 56.dp, if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(s.routineName(routine), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            // What it covers, always: on the page that edits it, the summary is
                            // the thing being edited and must not be displaced by a countdown.
                            Text(
                                s.routineSummary(routine, covered.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            routineStateLine(store, config, routine, now)?.let { state ->
                                Text(state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HapticSwitch(
                            checked = routine.enabled,
                            onCheckedChange = { on -> save(routine.copy(enabled = on)) },
                        )
                    }

                    AnimatedVisibility(visible = notice != null, enter = Motion.reveal(), exit = Motion.conceal()) {
                        Hint(notice.orEmpty())
                    }
                    // A new routine arrives switched off on purpose, so nobody's evening is
                    // blocked by a default window they never looked at. Say so once, here.
                    if (!routine.enabled) Hint(s.routineOffHint)

                    // Starting it by hand, right where its name is; hidden when it has nothing to act on.
                    if (routine.enabled && !routine.coversNothing && covered.isNotEmpty()) {
                        Button(
                            onClick = { starting = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(if (running) s.routineExtend else s.routineStart)
                        }
                    }

                    SectionTitle(s.routineWhatItDoes)
                    SettingsGroup {
                        SettingBlock(subtitle = if (routine.mode == RoutineMode.BLOCK) s.routineBlockHint else s.routinePauseHint) {
                            Segments(
                                options = listOf(RoutineMode.PAUSE to s.rulePause, RoutineMode.BLOCK to s.ruleBlock),
                                selected = routine.mode,
                            ) { mode -> save(routine.copy(mode = mode)) }
                        }
                    }

                    // The whole watch list is on the page, always. Hiding it behind the "every
                    // app" switch meant the one question people came here with — which apps is
                    // this for — had no visible answer, and the way to change it looked absent.
                    SectionTitle(s.routineAppsTitle)
                    SettingsGroup {
                        SettingRow(title = s.routineAllApps, subtitle = s.routineAllAppsHint, icon = Icons.Outlined.Apps) {
                            HapticSwitch(
                                checked = routine.allApps,
                                onCheckedChange = { all ->
                                    // Coming off "every app" starts from what it covers today, so
                                    // the switch alone never changes what the routine does.
                                    save(routine.copy(allApps = all, packages = if (all) routine.packages else covered.toSet()))
                                },
                            )
                        }
                        if (config.apps.isEmpty()) {
                            SettingsDivider()
                            SettingBlock(subtitle = s.routineNoAppsYet) {
                                OutlinedButton(onClick = { openRoute(Route.AddApps(routine.id)) }) { Text(s.routineAddApp) }
                            }
                        } else {
                            config.apps.sortedBy { it.label.lowercase() }.forEach { app ->
                                key(app.packageName) {
                                    SettingsDivider()
                                    val inScope = routine.allApps || app.packageName in routine.packages
                                    AppPickRow(
                                        label = app.label,
                                        checked = inScope,
                                        note = if (routine.allApps) s.routineByEveryApp else null,
                                        icon = { AppIcon(app.packageName, 28.dp) },
                                    ) { on ->
                                        // Unticking one while "every app" is on is how hand-picking
                                        // starts: keep everything it covers today, minus this one.
                                        save(
                                            if (routine.allApps) routine.copy(allApps = false, packages = covered.toSet() - app.packageName)
                                            else routine.copy(packages = if (on) routine.packages + app.packageName else routine.packages - app.packageName),
                                        )
                                    }
                                }
                            }
                            SettingsDivider()
                            SettingBlock {
                                // Straight from here into the whole phone: needing to put an app on
                                // a general list first and only then into the routine is bookkeeping
                                // nobody asked for.
                                OutlinedButton(onClick = { openRoute(Route.AddApps(routine.id)) }) { Text(s.routineAddApp) }
                                if (routine.coversNothing || (!routine.allApps && covered.isEmpty())) Hint(s.routineNeedsApps)
                            }
                        }
                    }

                    SectionTitle(s.routineHours)
                    SettingsGroup {
                        SettingBlock(icon = Icons.Outlined.Schedule, subtitle = s.routineHoursHint) {
                            if (routine.windows.isEmpty()) Hint(s.routineNoWindows)
                        }
                        routine.windows.sortedBy { it.startMinute }.forEach { window ->
                            key(window.id) {
                                SettingsDivider()
                                Surface(
                                    onClick = { editingWindow = window; windowIsNew = false },
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                ) {
                                    SettingRow(
                                        title = s.windowSummary(window),
                                        trailing = { Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    )
                                }
                            }
                        }
                        SettingsDivider()
                        SettingBlock {
                            val room = routine.windows.size < Routine.MAX_WINDOWS
                            OutlinedButton(
                                onClick = {
                                    editingWindow = RoutineWindow(id = nowMillis(), startMinute = 22 * 60, endMinute = 7 * 60)
                                    windowIsNew = true
                                },
                                enabled = room,
                            ) { Text(s.addWindow) }
                            if (!room) Hint(s.routineWindowsCap(Routine.MAX_WINDOWS))
                        }
                    }

                    SectionTitle(s.pauseFor)
                    SettingsGroup {
                        SettingRow(title = s.routineIgnoresBreaks, subtitle = s.routineIgnoresBreaksHint, icon = Icons.Outlined.Coffee) {
                            HapticSwitch(
                                checked = routine.ignoresBreaks,
                                onCheckedChange = { on -> save(routine.copy(ignoresBreaks = on)) },
                            )
                        }
                    }

                    SectionTitle(s.routineSign)
                    SettingsGroup {
                        SettingBlock(subtitle = s.routineSignHint) {
                            EmojiPicker(selected = routine.emoji, enabled = true) { emoji ->
                                save(routine.copy(emoji = if (emoji == routine.emoji) "" else Emoji.firstCluster(emoji)))
                            }
                        }
                        SettingsDivider()
                        SettingBlock(title = s.routineNameLabel) {
                            var draft by remember(routine.name) { mutableStateOf(routine.name) }
                            var justSaved by remember { mutableStateOf(false) }
                            val dirty = draft.trim() != routine.name
                            val keyboard = LocalSoftwareKeyboardController.current
                            val focus = LocalFocusManager.current
                            val commit = {
                                haptics.confirm()
                                save(routine.copy(name = draft.trim()))
                                justSaved = true
                                keyboard?.hide()
                                focus.clearFocus()
                            }
                            LaunchedEffect(justSaved) { if (justSaved) { delay(1800); justSaved = false } }
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { v -> draft = v.take(Routine.MAX_NAME); justSaved = false },
                                singleLine = true,
                                placeholder = { Text(s.routineNamePlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { if (dirty) commit() else { keyboard?.hide(); focus.clearFocus() } }),
                                trailingIcon = if (justSaved) ({ Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.tertiary) }) else null,
                            )
                            AnimatedVisibility(visible = dirty, enter = Motion.reveal(), exit = Motion.conceal()) {
                                Button(onClick = commit, modifier = Modifier.padding(top = 2.dp)) { Text(s.save) }
                            }
                            if (routine.builtIn && routine.name.isBlank()) Hint(s.routineBuiltIn)
                        }
                    }

                    if (routine.builtIn) {
                        SettingsGroup {
                            Surface(onClick = { confirmReset = true }, color = MaterialTheme.colorScheme.surfaceContainer) {
                                SettingRow(title = s.routineReset, subtitle = s.routineBuiltInHint, icon = Icons.Outlined.Restore) {
                                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    editingWindow?.let { window ->
        WindowEditorDialog(
            initial = window,
            isNew = windowIsNew,
            onDismiss = { editingWindow = null },
            onSave = { next ->
                save(routine.copy(windows = routine.windows.filter { it.id != next.id } + next))
                editingWindow = null
            },
            onDelete = if (windowIsNew) null else ({
                save(routine.copy(windows = routine.windows.filter { it.id != window.id }))
                editingWindow = null
            }),
        )
    }
    if (starting) RoutineStartSheet(store, routine, onDismiss = { starting = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(s.routineDelete) },
            text = { Text(s.routineDeleteBody(s.routineName(routine))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (store.deleteRoutine(routine.id)) onClose()
                    else notice = if (running) s.routineRunningRefused else s.routineStrictRefused
                }) { Text(s.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(s.cancel) } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(s.routineReset) },
            text = { Text(s.routineResetBody(s.routineName(routine))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    if (!store.resetRoutine(routine.id)) notice = if (running) s.routineRunningRefused else s.routineStrictRefused
                }) { Text(s.routineReset) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(s.cancel) } },
        )
    }
}
