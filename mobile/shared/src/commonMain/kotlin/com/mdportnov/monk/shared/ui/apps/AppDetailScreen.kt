package com.mdportnov.monk.shared.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mdportnov.monk.shared.model.TimeRule
import com.mdportnov.monk.shared.ui.LocalOpenRoute
import com.mdportnov.monk.shared.ui.Route
import com.mdportnov.monk.shared.ui.TopBarState
import com.mdportnov.monk.shared.ui.routines.RoutineFace
import com.mdportnov.monk.shared.ui.routines.routineStateLine
import androidx.compose.runtime.SideEffect
import dev.chrisbanes.haze.HazeState
import com.mdportnov.monk.shared.ui.components.glassTopBarInset
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.mdportnov.monk.shared.ui.components.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import kotlinx.coroutines.delay
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.everAppliesUnder
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.ui.components.Counter
import com.mdportnov.monk.shared.ui.components.FitText
import com.mdportnov.monk.shared.ui.components.Hint
import com.mdportnov.monk.shared.data.localMoment
import com.mdportnov.monk.shared.ui.components.LabeledRow
import com.mdportnov.monk.shared.ui.components.MonkCard
import com.mdportnov.monk.shared.ui.components.SectionTitle
import com.mdportnov.monk.shared.ui.components.SettingBlock
import com.mdportnov.monk.shared.ui.components.Segments
import com.mdportnov.monk.shared.ui.components.SettingRow
import com.mdportnov.monk.shared.ui.components.SettingsDivider
import com.mdportnov.monk.shared.ui.components.SettingsGroup
import com.mdportnov.monk.shared.ui.components.SliderSetting
import com.mdportnov.monk.shared.ui.components.formatMinute

@Composable
fun AppDetailScreen(store: MonkStore, packageName: String, onClose: () -> Unit, topBar: TopBarState, hazeState: HazeState) {
    val s = strings
    val config by store.config.collectAsStateWithLifecycle()
    val stats by store.stats.collectAsStateWithLifecycle()
    // Once removed, the page keeps showing its last state while it slides away.
    val live = config.app(packageName)
    val last = remember { arrayOfNulls<com.mdportnov.monk.shared.model.BlockedApp>(1) }
    LaunchedEffect(live == null) { if (live == null) onClose() }
    val app = live?.also { last[0] = it } ?: last[0] ?: return
    // The page outlives a minute: without a clock of its own every routine line below froze at
    // the instant it was opened.
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = nowMillis() } }
    LaunchedEffect(config.run, config.pausedUntil, config.enabled) { now = nowMillis() }
    // Strict mode or a per-app lock: anything that softens the rule is frozen.
    val strict = config.isStrict(now) || app.locked
    var editing by remember { mutableStateOf<TimeRule?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var confirmLock by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    val strictGlobal = config.isStrict(now)
    val openRoute = LocalOpenRoute.current
    SideEffect {
        topBar.set(
            title = app.label,
            visible = true,
            level = 10,
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, s.back) } },
            actions = {
                // Removal is the one door a lock leaves open: it is the whole point of the lock.
                IconButton(onClick = { confirmRemove = true }, enabled = !strictGlobal) {
                    Icon(Icons.Outlined.DeleteOutline, s.remove, tint = if (strictGlobal) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    Box(Modifier.fillMaxSize()) {
        val padding = PaddingValues(top = glassTopBarInset())
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName, 56.dp)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleLarge)
                    Text(packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (strict) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Hint(if (app.locked) s.lockedNote else s.strictUntil(formatClock(config.strictUntil)))
                }
            }

            val today = stats.day(localMoment().dateIso).byApp[packageName]
            if (today != null && today.intercepted > 0) {
                SectionTitle(s.todayForApp)
                MonkCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Counter(today.intercepted, s.intercepted)
                        Counter(today.turnedAway, s.turnedAway, MaterialTheme.colorScheme.tertiary)
                        Counter(today.opened, s.opened, MaterialTheme.colorScheme.primary)
                    }
                }
            }

            SectionTitle(s.modeTitle)
            SettingsGroup {
                SettingBlock(subtitle = if (app.mode == BlockMode.BLOCK) s.modeBlockHint else s.modeDelayHint) {
                    Segments(
                        options = listOf(BlockMode.DELAY to s.modeDelay, BlockMode.BLOCK to s.modeBlock),
                        selected = app.mode,
                        // Tightening (Pause → Block) stays possible under a lock; loosening does not.
                        enabled = !strict || app.mode == BlockMode.DELAY,
                    ) { mode -> if (!strict || mode == BlockMode.BLOCK) store.upsertApp(app.copy(mode = mode)) }
                }
            }

            // The pause length and the allowance matter wherever a pause screen can show, the
            // limit wherever the app can open at all: a Block app with a Pause or Free window included.
            if (app.hasPauseScreen) {
                SectionTitle(s.delayLength)
                SettingsGroup {
                    SettingRow(title = s.useDefault, subtitle = s.currently("${config.defaultDelaySeconds} ${s.seconds}")) {
                        HapticSwitch(
                            checked = app.delaySeconds == null,
                            enabled = !strict,
                            onCheckedChange = { useDefault ->
                                store.upsertApp(app.copy(delaySeconds = if (useDefault) null else config.defaultDelaySeconds))
                            },
                        )
                    }
                    if (app.delaySeconds != null) {
                        SettingsDivider()
                        SliderSetting(
                            title = s.delayLength, hint = s.delayLengthHint, value = app.delaySeconds, unit = s.seconds,
                            range = 3..120, enabled = !strict,
                            onChange = { store.upsertApp(app.copy(delaySeconds = it)) },
                        )
                    }
                }

                SectionTitle(s.allowLength)
                SettingsGroup {
                    SettingRow(title = s.useDefault, subtitle = s.currently("${config.defaultAllowMinutes} ${s.minutes}")) {
                        HapticSwitch(
                            checked = app.allowMinutes == null,
                            enabled = !strict,
                            onCheckedChange = { useDefault ->
                                store.upsertApp(app.copy(allowMinutes = if (useDefault) null else config.defaultAllowMinutes))
                            },
                        )
                    }
                    if (app.allowMinutes != null) {
                        SettingsDivider()
                        SliderSetting(
                            title = s.allowLength, hint = s.allowLengthHint, value = app.allowMinutes, unit = s.minutes,
                            range = 1..60, enabled = !strict,
                            onChange = { store.upsertApp(app.copy(allowMinutes = it)) },
                        )
                    }
                }
            }

            if (app.canOpen) {
                SectionTitle(s.dailyLimit)
                SettingsGroup {
                    SettingRow(title = s.noLimit, subtitle = s.dailyLimitHint) {
                        HapticSwitch(
                            checked = app.dailyLimit == null,
                            enabled = !strict,
                            onCheckedChange = { unlimited -> store.upsertApp(app.copy(dailyLimit = if (unlimited) null else 3)) },
                        )
                    }
                    if (app.dailyLimit != null) {
                        SettingsDivider()
                        SliderSetting(
                            title = s.dailyLimit, hint = null, value = app.dailyLimit, unit = s.times,
                            range = 1..20, enabled = !strict, format = { "$it${s.times}" },
                            onChange = { store.upsertApp(app.copy(dailyLimit = it)) },
                        )
                    }
                }
            }

            SectionTitle(s.rulesTitle)
            SettingsGroup {
                SettingBlock(icon = Icons.Outlined.Schedule, subtitle = s.rulesHint) {
                    if (app.rules.isEmpty()) Hint(s.noRules)
                    // Rules are the app's own layer, and that layer is switched off outside the
                    // base hours. Without this the page reads as though a 22:00 rule would fire.
                    if (config.schedule.enabled) {
                        Hint(s.rulesInsideBaseHours("${formatMinute(config.schedule.startMinute)}–${formatMinute(config.schedule.endMinute)}"))
                    }
                }
                app.rules.sortedBy { it.startMinute }.forEach { rule -> key(rule.id) {
                    // The answer is a walk over every minute of the week; it changes only when the
                    // rule or the base hours do, and it must not be recomputed on every frame.
                    val dead = remember(rule, config.schedule) { !rule.everAppliesUnder(config.schedule) }
                    SettingsDivider()
                    Surface(onClick = { if (!strict) { editing = rule; editingIsNew = false } }, color = MaterialTheme.colorScheme.surfaceContainer, enabled = !strict) {
                        SettingRow(
                            title = ruleSummary(rule),
                            subtitle = if (dead) s.ruleOutsideBaseHours else null,
                            trailing = if (strict) null else ({ Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }),
                        )
                    }
                } }
                // Outside the loop over rules: with no rule yet there is nothing to iterate, and
                // the one way to make the first one would have been missing.
                if (!strict) {
                    SettingsDivider()
                    SettingBlock {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                editing = TimeRule(id = nowMillis(), startMinute = 6 * 60, endMinute = 9 * 60)
                                editingIsNew = true
                            }) { Text(s.addRule) }
                            if (app.rules.isNotEmpty()) {
                                TextButton(onClick = { store.upsertApp(app.copy(rules = emptyList())) }) { Text(s.resetRules, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }

            // The routines this app belongs to, read-only: a rule set above can be overruled from
            // here, and without this the page would quietly lie about what the app does at 23:00.
            // Shown even when nothing covers this app: otherwise the one page about this app
            // would be the one place that never mentions the other half of what decides it.
            val inRoutines = config.routines.filter { it.covers(packageName) && !it.coversNothing }
            SectionTitle(s.routineCoveredBy)
            SettingsGroup {
                SettingBlock(icon = Icons.Outlined.AutoAwesome, subtitle = s.routineCoveredHint) {
                    if (inRoutines.isEmpty()) {
                        Hint(s.routineNotCovered)
                        OutlinedButton(onClick = { openRoute(Route.Routines) }) { Text(s.manageRoutines) }
                    }
                }
                inRoutines.forEach { routine ->
                    key(routine.id) {
                        SettingsDivider()
                        Surface(onClick = { openRoute(Route.RoutineDetail(routine.id)) }, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                RoutineFace(routine, 36.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(s.routineName(routine), style = MaterialTheme.typography.bodyLarge)
                                    // This list has no switch beside it, so a routine that is off
                                    // has to say so here — otherwise the hint above ("the stricter
                                    // wins") would be read as a promise it is not keeping.
                                    val line = if (!routine.enabled) s.routineOff
                                    else routineStateLine(store, config, routine, now)
                                        ?: s.routineSummary(routine, store.appsCovered(routine).size)
                                    Text(
                                        line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (routine.enabled) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            SectionTitle(s.lockTitle)
            SettingsGroup {
                SettingRow(title = s.lockTitle, subtitle = s.lockHint, icon = Icons.Outlined.Lock) {
                    HapticSwitch(checked = app.locked, enabled = !app.locked, onCheckedChange = { on -> if (on) confirmLock = true })
                }
            }
        }
        }
        }
    }

    editing?.let { rule ->
        RuleEditorDialog(
            initial = rule,
            isNew = editingIsNew,
            schedule = config.schedule,
            onDismiss = { editing = null },
            onSave = { store.upsertRule(packageName, it); editing = null },
            onDelete = if (editingIsNew) null else ({ store.removeRule(packageName, rule.id); editing = null }),
        )
    }
    if (confirmLock) {
        AlertDialog(
            onDismissRequest = { confirmLock = false },
            title = { Text(s.lockConfirmTitle) },
            text = { Text(s.lockConfirmBody) },
            confirmButton = { TextButton(onClick = { store.upsertApp(app.copy(locked = true)); confirmLock = false }) { Text(s.lock) } },
            dismissButton = { TextButton(onClick = { confirmLock = false }) { Text(s.cancel) } },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(s.removeAppTitle) },
            text = { Text(s.removeAppBody(app.rules.size)) },
            confirmButton = {
                // Only leave if it actually went: strict mode refuses, and closing the page on a
                // refusal would read as "removed" while the app is still watched.
                TextButton(onClick = { confirmRemove = false; if (store.removeApp(packageName)) onClose() }) { Text(s.remove, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(s.cancel) } },
        )
    }
}
