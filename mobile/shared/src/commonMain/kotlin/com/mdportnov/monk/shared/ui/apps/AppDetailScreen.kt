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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.ui.components.Counter
import com.mdportnov.monk.shared.ui.components.FitText
import com.mdportnov.monk.shared.ui.components.Hint
import com.mdportnov.monk.shared.data.localMoment
import com.mdportnov.monk.shared.ui.components.LabeledRow
import com.mdportnov.monk.shared.ui.components.MonkCard
import com.mdportnov.monk.shared.ui.components.SectionTitle
import com.mdportnov.monk.shared.ui.components.SettingBlock
import com.mdportnov.monk.shared.ui.components.SettingRow
import com.mdportnov.monk.shared.ui.components.SettingsDivider
import com.mdportnov.monk.shared.ui.components.SettingsGroup
import com.mdportnov.monk.shared.ui.components.SliderSetting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(store: MonkStore, packageName: String, onClose: () -> Unit) {
    val s = strings
    val config by store.config.collectAsStateWithLifecycle()
    val stats by store.stats.collectAsStateWithLifecycle()
    val app = config.app(packageName)
    LaunchedEffect(app == null) { if (app == null) onClose() }
    if (app == null) return
    // Strict mode: anything that softens the rule is frozen; tightening stays allowed.
    val strict = config.isStrict(nowMillis())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.label) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { store.removeApp(packageName); onClose() }, enabled = !strict) {
                        Icon(Icons.Outlined.DeleteOutline, s.remove, tint = if (strict) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
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
                    Hint(s.strictUntil(formatClock(config.strictUntil)))
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
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = app.mode == BlockMode.DELAY,
                            enabled = !strict,
                            onClick = { store.upsertApp(app.copy(mode = BlockMode.DELAY)) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { FitText(s.modeDelay) }
                        SegmentedButton(
                            selected = app.mode == BlockMode.BLOCK,
                            onClick = { store.upsertApp(app.copy(mode = BlockMode.BLOCK)) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { FitText(s.modeBlock) }
                    }
                }
            }

            if (app.mode == BlockMode.DELAY) {
                SectionTitle(s.delayLength)
                SettingsGroup {
                    SettingRow(title = s.useDefault, subtitle = "${config.defaultDelaySeconds} ${s.seconds}") {
                        Switch(
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
                    SettingRow(title = s.useDefault, subtitle = "${config.defaultAllowMinutes} ${s.minutes}") {
                        Switch(
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

                SectionTitle(s.dailyLimit)
                SettingsGroup {
                    SettingRow(title = s.noLimit, subtitle = s.dailyLimitHint) {
                        Switch(
                            checked = app.dailyLimit == null,
                            enabled = !strict,
                            onCheckedChange = { unlimited -> store.upsertApp(app.copy(dailyLimit = if (unlimited) null else 3)) },
                        )
                    }
                    if (app.dailyLimit != null) {
                        SettingsDivider()
                        SliderSetting(
                            title = s.dailyLimit, hint = null, value = app.dailyLimit, unit = s.times,
                            range = 1..20, enabled = !strict,
                            onChange = { store.upsertApp(app.copy(dailyLimit = it)) },
                        )
                    }
                }
            }
        }
        }
    }
}
