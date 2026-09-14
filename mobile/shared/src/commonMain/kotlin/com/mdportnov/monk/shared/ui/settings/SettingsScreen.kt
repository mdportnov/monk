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
import androidx.compose.material.icons.outlined.CalendarMonth
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
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
import com.mdportnov.monk.shared.ui.components.SliderSetting
import com.mdportnov.monk.shared.ui.components.UpdateCard
import com.mdportnov.monk.shared.ui.theme.supportsDynamicColor

@Composable
fun SettingsScreen(store: MonkStore, platform: MonkPlatform, scrollState: ScrollState, contentPadding: PaddingValues) {
    val s = strings
    val config by store.config.collectAsStateWithLifecycle()
    val permissions by platform.permissions.collectAsStateWithLifecycle()
    val host = LocalHostActions.current
    val strict = config.isStrict(nowMillis())
    var strictCandidate by remember { mutableStateOf<Long?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(s.tabSettings, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))

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
                title = s.delayLength, hint = s.delayLengthHint, icon = Icons.Outlined.HourglassEmpty,
                value = config.defaultDelaySeconds, unit = s.seconds, range = 3..120, enabled = !strict,
                onChange = { v -> store.updateConfig { it.copy(defaultDelaySeconds = v) } },
            )
            SettingsDivider()
            SliderSetting(
                title = s.allowLength, hint = s.allowLengthHint, icon = Icons.Outlined.LockClock,
                value = config.defaultAllowMinutes, unit = s.minutes, range = 1..60, enabled = !strict,
                onChange = { v -> store.updateConfig { it.copy(defaultAllowMinutes = v) } },
            )
        }

        SectionTitle(s.pauseScreen)
        SettingsGroup {
            SettingRow(title = s.askIntention, subtitle = s.askIntentionHint, icon = Icons.Outlined.Psychology) {
                Switch(checked = config.askIntention, onCheckedChange = { on -> store.updateConfig { it.copy(askIntention = on) } })
            }
            SettingsDivider()
            SettingBlock(icon = Icons.Outlined.FormatQuote, title = s.pauseMessage, subtitle = s.pauseMessageHint) {
                var message by remember(config.pauseMessage) { mutableStateOf(config.pauseMessage) }
                OutlinedTextField(
                    value = message,
                    onValueChange = { v -> message = v.take(120) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { f ->
                        if (!f.isFocused && message != config.pauseMessage) store.updateConfig { it.copy(pauseMessage = message.trim()) }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { store.updateConfig { it.copy(pauseMessage = message.trim()) } }),
                )
            }
        }

        SectionTitle(s.scheduleTitle)
        SettingsGroup {
            SettingRow(title = s.scheduleTitle, subtitle = s.scheduleHint, icon = Icons.Outlined.CalendarMonth) {
                Switch(
                    checked = config.schedule.enabled,
                    enabled = !strict,
                    onCheckedChange = { on -> store.updateConfig { it.copy(schedule = it.schedule.copy(enabled = on)) } },
                )
            }
            if (config.schedule.enabled) {
                SettingsDivider()
                SettingBlock {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.dayShort.forEachIndexed { i, label ->
                            val day = i + 1
                            val on = day in config.schedule.days
                            FilterChip(
                                selected = on,
                                // At least one day stays selected: an empty set would silently pause protection forever.
                                enabled = !strict && !(on && config.schedule.days.size == 1),
                                onClick = {
                                    store.updateConfig {
                                        val days = if (on) it.schedule.days - day else it.schedule.days + day
                                        it.copy(schedule = it.schedule.copy(days = days))
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                SliderSetting(
                    title = s.from, hint = null, value = config.schedule.startMinute, unit = "", range = 0..(24 * 60 - 15),
                    enabled = !strict, step = 15, format = ::formatMinute,
                    onChange = { m -> store.updateConfig { it.copy(schedule = it.schedule.copy(startMinute = m)) } },
                )
                SliderSetting(
                    title = s.to, hint = s.scheduleAllDay, value = config.schedule.endMinute, unit = "", range = 0..(24 * 60 - 15),
                    enabled = !strict, step = 15, format = ::formatMinute,
                    onChange = { m -> store.updateConfig { it.copy(schedule = it.schedule.copy(endMinute = m)) } },
                )
            }
        }

        SectionTitle(s.strictTitle)
        SettingsGroup {
            SettingBlock(icon = Icons.Outlined.Lock, title = s.strictTitle, subtitle = s.strictHint) {
                if (strict) {
                    Text(s.strictUntil(formatClock(config.strictUntil)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { strictCandidate = nextMidnightMillis() }) { Text(s.strictUntilMidnight) }
                        OutlinedButton(onClick = { strictCandidate = nowMillis() + 3 * 60 * 60_000L }) { Text(s.strict3h) }
                    }
                }
            }
        }

        if (platform.supportsBlocking) {
            SectionTitle(s.reliability)
            SettingsGroup {
                SettingRow(title = s.overlayMode, subtitle = s.overlayModeHint, icon = Icons.Outlined.Layers) {
                    Switch(checked = config.overlayMode, onCheckedChange = { on -> store.updateConfig { it.copy(overlayMode = on) } })
                }
                SettingsDivider()
                SettingRow(title = s.notifyWhenOff, subtitle = s.notifyWhenOffHint, icon = Icons.Outlined.NotificationsActive) {
                    Switch(checked = config.notifyWhenOff, onCheckedChange = { on -> store.updateConfig { it.copy(notifyWhenOff = on) } })
                }
                if (config.notifyWhenOff && !permissions.notificationsGranted) {
                    SettingBlock { OutlinedButton(onClick = host.requestNotificationPermission) { Text(s.notifyPermission) } }
                }
                SettingsDivider()
                SettingBlock(icon = Icons.Outlined.GridView, title = s.quickTiles, subtitle = s.quickTilesHint) {
                    OutlinedButton(onClick = platform::requestAddTiles) { Text(s.addTiles) }
                }
            }
        }

        SectionTitle(s.languageTitle)
        SettingsGroup {
            SettingBlock(icon = Icons.Outlined.Language, subtitle = s.language) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val langs = listOf("system" to s.languageSystem, "en" to "English", "ru" to "Русский")
                    langs.forEachIndexed { i, (tag, label) ->
                        SegmentedButton(
                            selected = config.language == tag,
                            onClick = { store.updateConfig { it.copy(language = tag) } },
                            shape = SegmentedButtonDefaults.itemShape(i, langs.size),
                        ) { FitText(label) }
                    }
                }
            }
        }

        SectionTitle(s.appearance)
        SettingsGroup {
            SettingBlock(icon = Icons.Outlined.Contrast) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(ThemeMode.SYSTEM to s.themeSystem, ThemeMode.LIGHT to s.themeLight, ThemeMode.DARK to s.themeDark)
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = config.theme == mode,
                            onClick = { store.updateConfig { it.copy(theme = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { FitText(label) }
                    }
                }
            }
            if (supportsDynamicColor()) {
                SettingsDivider()
                SettingRow(title = s.dynamicColor, subtitle = s.dynamicColorHint, icon = Icons.Outlined.Palette) {
                    Switch(checked = config.dynamicColor, onCheckedChange = { on -> store.updateConfig { it.copy(dynamicColor = on) } })
                }
            }
        }

        SectionTitle(s.data)
        SettingsGroup {
            Surface(onClick = { confirmReset = true }, color = MaterialTheme.colorScheme.surfaceContainer) {
                SettingRow(title = s.resetStats, icon = Icons.Outlined.DeleteOutline) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionTitle(s.about)
        platform.updater?.let { UpdateCard(it, compact = false) }
        SettingsGroup {
            SettingBlock { Text(s.aboutText, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(s.howTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s.howStep1)
                    Text(s.howStep2)
                    Text(s.howStep3)
                    Text(s.howStep4, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text(s.gotIt) } },
        )
    }
    strictCandidate?.let { until ->
        AlertDialog(
            onDismissRequest = { strictCandidate = null },
            title = { Text(s.strictConfirmTitle) },
            text = { Text(s.strictConfirmBody(formatClock(until))) },
            confirmButton = { TextButton(onClick = { store.enableStrict(until); strictCandidate = null }) { Text(s.confirm) } },
            dismissButton = { TextButton(onClick = { strictCandidate = null }) { Text(s.cancel) } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text(s.resetStatsConfirm) },
            confirmButton = {
                TextButton(onClick = { store.resetStats(); confirmReset = false }) {
                    Text(s.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(s.cancel) } },
        )
    }
}

fun formatMinute(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
