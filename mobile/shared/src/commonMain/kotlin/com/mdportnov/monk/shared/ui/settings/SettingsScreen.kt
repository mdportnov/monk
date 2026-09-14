package com.mdportnov.monk.shared.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.Alignment
import com.mdportnov.monk.shared.ui.components.Pill
import com.mdportnov.monk.shared.ui.components.PageHeaderSlot
import com.mdportnov.monk.shared.ui.components.PageTitle
import com.mdportnov.monk.shared.ui.components.MorrowMark
import com.mdportnov.monk.shared.ui.components.PoweredBy
import com.mdportnov.monk.shared.ui.home.MonkMark
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.mdportnov.monk.shared.ui.components.HapticSwitch
import com.mdportnov.monk.shared.ui.components.HowItWorksSheet
import com.mdportnov.monk.shared.ui.components.rememberHaptics
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import com.mdportnov.monk.shared.ui.Motion
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.data.nextMidnightMillis
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.ThemeMode
import com.mdportnov.monk.shared.platform.MonkPlatform
import com.mdportnov.monk.shared.ui.LocalHostActions
import com.mdportnov.monk.shared.ui.components.FitText
import com.mdportnov.monk.shared.ui.components.SectionTitle
import com.mdportnov.monk.shared.ui.components.SettingBlock
import com.mdportnov.monk.shared.ui.components.SettingRow
import com.mdportnov.monk.shared.ui.components.SettingsDivider
import com.mdportnov.monk.shared.ui.components.SettingsGroup
import com.mdportnov.monk.shared.ui.components.DayToggleRow
import com.mdportnov.monk.shared.ui.components.TimePickerDialog
import com.mdportnov.monk.shared.ui.components.formatMinute
import com.mdportnov.monk.shared.ui.components.Hint
import com.mdportnov.monk.shared.ui.components.SliderSetting
import com.mdportnov.monk.shared.ui.components.Segments
import com.mdportnov.monk.shared.ui.components.UpdateCard
import com.mdportnov.monk.shared.ui.theme.supportsDynamicColor

@Composable
fun SettingsScreen(store: MonkStore, platform: MonkPlatform, scrollState: ScrollState, contentPadding: PaddingValues) {
    val s = strings
    val config by store.config.collectAsStateWithLifecycle()
    val permissions by platform.permissions.collectAsStateWithLifecycle()
    val host = LocalHostActions.current
    val strict = config.isStrict(nowMillis())
    val paused = config.isPaused(nowMillis())
    var strictCandidate by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var timePick by rememberSaveable { mutableStateOf<String?>(null) }
    val haptic = rememberHaptics()

    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PageHeaderSlot(Modifier.padding(horizontal = 4.dp)) { PageTitle(s.tabSettings) }

        SettingsGroup {
            Surface(onClick = { showHelp = true }, color = MaterialTheme.colorScheme.surfaceContainer) {
                SettingRow(title = s.howTitle, subtitle = s.howStep1.removePrefix("1. "), icon = Icons.AutoMirrored.Outlined.HelpOutline) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionTitle(s.defaults)
        SettingsGroup {
            SliderSetting(
                title = s.delayLength, hint = s.delayLengthHint,
                value = config.defaultDelaySeconds, unit = s.seconds, range = 3..120, enabled = !strict,
                onChange = { v -> store.updateConfig { it.copy(defaultDelaySeconds = v) } },
            )
            SettingsDivider()
            SliderSetting(
                title = s.allowLength, hint = s.allowLengthHint,
                value = config.defaultAllowMinutes, unit = s.minutes, range = 1..60, enabled = !strict,
                onChange = { v -> store.updateConfig { it.copy(defaultAllowMinutes = v) } },
            )
        }

        SectionTitle(s.pauseScreen)
        SettingsGroup {
            SettingRow(title = s.askIntention, subtitle = s.askIntentionHint, icon = Icons.Outlined.Psychology) {
                HapticSwitch(checked = config.askIntention, onCheckedChange = { on -> store.updateConfig { it.copy(askIntention = on) } })
            }
            if (platform.supportsBlocking) {
                SettingsDivider()
                SettingRow(title = s.overlayMode, subtitle = s.overlayModeHint, icon = Icons.Outlined.Layers) {
                    HapticSwitch(checked = config.overlayMode, onCheckedChange = { on -> store.updateConfig { it.copy(overlayMode = on) } })
                }
            }
            SettingsDivider()
            SettingBlock(title = s.pauseMessage, subtitle = s.pauseMessageHint) {
                var message by remember(config.pauseMessage) { mutableStateOf(config.pauseMessage) }
                var justSaved by remember { mutableStateOf(false) }
                val dirty = message.trim() != config.pauseMessage
                val keyboard = LocalSoftwareKeyboardController.current
                val focusManager = LocalFocusManager.current
                val save = {
                    haptic.confirm()
                    store.updateConfig { it.copy(pauseMessage = message.trim()) }
                    justSaved = true
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
                LaunchedEffect(justSaved) { if (justSaved) { kotlinx.coroutines.delay(1800); justSaved = false } }
                OutlinedTextField(
                    value = message,
                    onValueChange = { v -> message = v.take(120); justSaved = false },
                    singleLine = true,
                    placeholder = { Text(s.pauseMessagePlaceholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (dirty) save() else { keyboard?.hide(); focusManager.clearFocus() } }),
                    trailingIcon = if (justSaved) ({ Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.tertiary) }) else null,
                )
                // The commit is explicit: a field that saves on blur gives no sign it did.
                AnimatedVisibility(visible = dirty, enter = Motion.reveal(), exit = Motion.conceal()) {
                    Button(onClick = save, modifier = Modifier.padding(top = 2.dp)) { Text(s.save) }
                }
            }
        }

        SectionTitle(s.protectionHours)
        SettingsGroup {
            SettingRow(title = s.protectionHours, subtitle = s.protectionHoursHint + " " + s.scheduleHint, icon = Icons.Outlined.CalendarMonth) {
                HapticSwitch(
                    checked = config.schedule.enabled,
                    enabled = !strict,
                    onCheckedChange = { on -> store.updateConfig { it.copy(schedule = it.schedule.copy(enabled = on)) } },
                )
            }
            if (config.schedule.enabled) {
                SettingsDivider()
                SettingBlock {
                    // At least one day stays selected: an empty set would silently pause protection forever.
                    DayToggleRow(s.dayShort, config.schedule.days, enabled = !strict, onToggle = { day, on ->
                        store.updateConfig { it.copy(schedule = it.schedule.copy(days = if (on) it.schedule.days + day else it.schedule.days - day)) }
                    })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { timePick = "start" }, enabled = !strict, modifier = Modifier.weight(1f)) { Text("${s.from} ${formatMinute(config.schedule.startMinute)}") }
                        OutlinedButton(onClick = { timePick = "end" }, enabled = !strict, modifier = Modifier.weight(1f)) { Text("${s.to} ${formatMinute(config.schedule.endMinute)}") }
                    }
                    Hint(if (config.schedule.endMinute <= config.schedule.startMinute) s.ruleNextDay else s.ruleSameDay)
                }
            }
        }

        SectionTitle(s.strictTitle)
        SettingsGroup {
            SettingBlock(icon = Icons.Outlined.Lock, title = s.strictTitle, subtitle = s.strictHint) {
                if (strict) {
                    Text(s.strictUntil(formatClock(config.strictUntil)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                } else {
                    var preset by rememberSaveable { mutableStateOf(StrictPreset.Midnight) }
                    var customMinutes by rememberSaveable { mutableStateOf(180) }
                    val h = rememberHaptics()
                    Segments(
                        options = listOf(StrictPreset.Midnight to s.strictUntilMidnight, StrictPreset.Hour to s.strict1h, StrictPreset.Custom to s.strictCustom),
                        selected = preset,
                        onSelect = { preset = it },
                    )
                    AnimatedVisibility(visible = preset == StrictPreset.Custom, enter = Motion.reveal(), exit = Motion.conceal()) {
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
                    val until = when (preset) {
                        StrictPreset.Midnight -> nextMidnightMillis()
                        StrictPreset.Hour -> nowMillis() + 60 * 60_000L
                        StrictPreset.Custom -> nowMillis() + customMinutes * 60_000L
                    }
                    Button(onClick = { h.select(); strictCandidate = until }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.strictStart(formatClock(until)))
                    }
                }
            }
        }

        if (platform.supportsBlocking) {
            SectionTitle(s.system)
            SettingsGroup {
                SettingRow(title = s.notifyWhenOff, subtitle = s.notifyWhenOffHint, icon = Icons.Outlined.NotificationsActive) {
                    HapticSwitch(checked = config.notifyWhenOff, onCheckedChange = { on -> store.updateConfig { it.copy(notifyWhenOff = on) } })
                }
                if (config.notifyWhenOff && !permissions.notificationsGranted) {
                    SettingBlock { OutlinedButton(onClick = host.requestNotificationPermission) { Text(s.notifyPermission) } }
                }
                SettingsDivider()
                SettingRow(title = s.liveStatus, subtitle = s.liveStatusHint, icon = Icons.Outlined.Timer) {
                    HapticSwitch(checked = config.liveStatus, onCheckedChange = { on -> store.updateConfig { it.copy(liveStatus = on) } })
                }
                if (config.liveStatus && !permissions.notificationsGranted) {
                    SettingBlock { OutlinedButton(onClick = host.requestNotificationPermission) { Text(s.notifyPermission) } }
                }
                SettingsDivider()
                SettingRow(title = s.haptics, subtitle = s.hapticsHint, icon = Icons.Outlined.Vibration) {
                    HapticSwitch(checked = config.haptics, onCheckedChange = { on -> store.updateConfig { it.copy(haptics = on) } })
                }
                SettingsDivider()
                val allTiles = permissions.tilesTotal > 0 && permissions.tilesAdded >= permissions.tilesTotal
                when {
                    allTiles -> SettingRow(title = s.quickTiles, subtitle = s.tilesAdded, icon = Icons.Outlined.GridView) {
                        Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    permissions.canRequestTiles -> SettingBlock(icon = Icons.Outlined.GridView, title = s.quickTiles, subtitle = s.quickTilesHint) {
                        OutlinedButton(onClick = platform::requestAddTiles) { Text(if (permissions.tilesAdded > 0) s.addMissingTile else s.addTiles) }
                    }
                    else -> SettingRow(title = s.quickTiles, subtitle = s.tilesManual, icon = Icons.Outlined.GridView)
                }
                SettingsDivider()
                val batteryOk = permissions.batteryUnrestricted && !permissions.backgroundRestricted && !permissions.sleeping
                SettingRow(
                    title = s.battery,
                    subtitle = when {
                        permissions.backgroundRestricted -> s.batteryRestricted
                        permissions.sleeping -> s.batterySleeping
                        !permissions.batteryUnrestricted -> s.batteryOptimized
                        else -> s.batteryUnrestricted
                    },
                    icon = Icons.Outlined.BatteryAlert,
                ) {
                    if (batteryOk) {
                        Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        TextButton(onClick = { if (permissions.backgroundRestricted || permissions.sleeping) platform.openAppInfo() else platform.requestBatteryUnrestricted() }) { Text(s.allow) }
                    }
                }
                if (permissions.samsung) {
                    SettingsDivider()
                    SettingRow(title = s.samsungSleep, subtitle = s.samsungSleepHint, icon = Icons.Outlined.OpenInNew) {
                        TextButton(onClick = platform::openBatterySettings) { Text(s.open) }
                    }
                }
                SettingsDivider()
                SettingRow(
                    title = s.usageAccess,
                    subtitle = if (permissions.usageAccessGranted) s.usageAccessOn else s.usageAccessOff,
                    icon = Icons.Outlined.HourglassEmpty,
                ) {
                    if (permissions.usageAccessGranted) {
                        Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        TextButton(onClick = platform::openUsageAccessSettings) { Text(s.allow) }
                    }
                }
            }
        }

        SectionTitle(s.languageTitle)
        SettingsGroup {
            SettingBlock {
                Segments(
                    options = listOf("system" to s.languageSystem, "en" to "English", "ru" to "Русский"),
                    selected = config.language,
                ) { tag -> store.updateConfig { it.copy(language = tag) } }
            }
        }

        SectionTitle(s.appearance)
        SettingsGroup {
            SettingBlock {
                Segments(
                    options = listOf(ThemeMode.SYSTEM to s.themeSystem, ThemeMode.LIGHT to s.themeLight, ThemeMode.DARK to s.themeDark),
                    selected = config.theme,
                ) { mode -> store.updateConfig { it.copy(theme = mode) } }
            }
            if (supportsDynamicColor()) {
                SettingsDivider()
                SettingRow(title = s.dynamicColor, subtitle = s.dynamicColorHint, icon = Icons.Outlined.Palette) {
                    HapticSwitch(checked = config.dynamicColor, onCheckedChange = { on -> store.updateConfig { it.copy(dynamicColor = on) } })
                }
            }
        }

        SectionTitle(s.data)
        SettingsGroup {
            Surface(onClick = { confirmReset = true }, enabled = !strict, color = MaterialTheme.colorScheme.surfaceContainer) {
                SettingRow(title = s.resetStats, icon = Icons.Outlined.DeleteOutline) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionTitle(s.about)
        SettingsGroup {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MonkMark(40.dp)
                Column(Modifier.weight(1f)) {
                    Text("Monk", style = MaterialTheme.typography.titleMedium)
                    Text(s.aboutTagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                platform.updater?.let { Pill(it.currentVersion, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            SettingsDivider()
            SettingRow(title = s.aboutPrivacy, subtitle = s.aboutPrivacyHint, icon = Icons.Outlined.PhonelinkLock)
            SettingsDivider()
            SettingRow(title = s.aboutAccessibility, subtitle = s.aboutAccessibilityHint, icon = Icons.Outlined.VisibilityOff)
            SettingsDivider()
            Surface(onClick = { platform.openUrl(s.sourceUrl) }, color = MaterialTheme.colorScheme.surfaceContainer) {
                SettingRow(title = s.aboutSource, subtitle = s.aboutSourceHint, icon = Icons.Outlined.Code) {
                    Icon(Icons.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        platform.updater?.let { UpdateCard(it, compact = false) }

        // The other app from the same workshop. The page it opens is where Morrow is described
        // and where its Android build is downloaded; Monk itself knows nothing more about it.
        SectionTitle(s.morrowSection)
        SettingsGroup {
            Surface(onClick = { platform.openUrl(s.morrowUrl) }, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MorrowMark(40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(s.morrowName, style = MaterialTheme.typography.titleMedium)
                        Text(s.morrowHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PoweredBy(onClick = { platform.openUrl(s.madeWithUrl) })
    }

    if (showHelp) HowItWorksSheet(onDismiss = { showHelp = false })
    timePick?.let { which ->
        TimePickerDialog(
            title = if (which == "start") s.from else s.to,
            minuteOfDay = if (which == "start") config.schedule.startMinute else config.schedule.endMinute,
            confirmLabel = s.done, cancelLabel = s.cancel,
            onDismiss = { timePick = null },
            onConfirm = { m ->
                store.updateConfig { it.copy(schedule = if (which == "start") it.schedule.copy(startMinute = m) else it.schedule.copy(endMinute = m)) }
                timePick = null
            },
        )
    }
    strictCandidate?.let { until ->
        AlertDialog(
            onDismissRequest = { strictCandidate = null },
            title = { Text(s.strictConfirmTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.strictConfirmBody(formatClock(until)))
                    if (paused) Hint(s.endsBreakNote)
                    if (!config.enabled) Hint(s.turnsOnNote)
                }
            },
            confirmButton = { TextButton(onClick = { haptic.confirm(); store.enableStrict(until); strictCandidate = null }) { Text(s.confirm) } },
            dismissButton = { TextButton(onClick = { strictCandidate = null }) { Text(s.cancel) } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text(s.resetStatsConfirm) },
            confirmButton = {
                TextButton(onClick = { store.resetStats(); platform.clearScreenTime(); confirmReset = false }) {
                    Text(s.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(s.cancel) } },
        )
    }
}

private enum class StrictPreset { Midnight, Hour, Custom }
