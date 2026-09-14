package com.mdportnov.monk.shared.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.data.lastDates
import com.mdportnov.monk.shared.data.localMoment
import com.mdportnov.monk.shared.data.nextMidnightMillis
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.Stats
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.platform.MonkPlatform
import com.mdportnov.monk.shared.platform.PermissionStatus
import com.mdportnov.monk.shared.ui.components.Hint
import com.mdportnov.monk.shared.ui.components.LabeledRow
import com.mdportnov.monk.shared.ui.components.MonkCard
import com.mdportnov.monk.shared.ui.components.SectionTitle
import com.mdportnov.monk.shared.ui.components.UpdateCard
import com.mdportnov.monk.shared.ui.theme.MonkColors
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    store: MonkStore,
    platform: MonkPlatform,
    onAddApps: () -> Unit,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings
    val config by store.config.collectAsStateWithLifecycle()
    val stats by store.stats.collectAsStateWithLifecycle()
    val allowances by store.allowances.collectAsStateWithLifecycle()
    var permissions by remember { mutableStateOf(platform.permissions()) }
    LifecycleResumeEffect(Unit) {
        permissions = platform.permissions()
        onPauseOrDispose { }
    }
    // A 30 s heartbeat: pause / strict / allowance countdowns and the schedule flip on their own.
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = nowMillis()
        }
    }
    val apps = remember(config.apps) { config.apps.sortedBy { it.label.lowercase() } }
    val today = localMoment().dateIso

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header() }
            if (!platform.supportsBlocking) {
                item { UnsupportedCard() }
            } else {
                platform.updater?.let { u -> item { UpdateCard(u, compact = true) } }
                item { StatusCard(store, config, permissions, now) }
                if (!permissions.accessibilityEnabled) {
                    item { SetupCard(permissions, platform) }
                }
                item { WeekCard(stats) }
            }
            item { SectionTitle(s.blockedApps, Modifier.padding(top = 8.dp)) }
            if (apps.isEmpty()) {
                item { EmptyApps(onAddApps) }
            }
            items(apps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    config = config,
                    allowedUntil = allowances[app.packageName]?.takeIf { it > now },
                    opensToday = stats.opensToday(today, app.packageName),
                    onEndAllowance = { store.revokeAllowance(app.packageName) },
                    onClick = { onOpenApp(app.packageName) },
                )
            }
        }
        if (apps.isNotEmpty() && platform.supportsBlocking) {
            ExtendedFloatingActionButton(
                onClick = onAddApps,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(s.addApps) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        MonkMark(28.dp)
        Spacer(Modifier.size(10.dp))
        Text("Monk", style = MaterialTheme.typography.headlineMedium)
    }
}

/** The "m_" from assets/logo.svg drawn as text — tiny, no vector plumbing needed. */
@Composable
fun MonkMark(size: Dp) {
    Surface(color = MonkColors.Ink, shape = MaterialTheme.shapes.small, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "m_",
                color = MonkColors.Fog,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * (size.value / 32f)),
            )
        }
    }
}

@Composable
private fun StatusCard(store: MonkStore, config: MonkConfig, permissions: PermissionStatus, now: Long) {
    val s = strings
    val moment = localMoment()
    val scheduleActive = config.schedule.isActive(moment.dayIso, moment.minuteOfDay)
    val strict = config.isStrict(now)
    val paused = config.isPaused(now)
    val effective = config.enabled && permissions.accessibilityEnabled && !paused
    val subtitle = when {
        !config.enabled -> s.protectionOff
        !permissions.accessibilityEnabled -> s.setupAccessibility
        paused -> s.pausedUntil(formatClock(config.pausedUntil))
        !scheduleActive -> s.protectionPaused
        strict -> s.strictUntil(formatClock(config.strictUntil))
        else -> s.protectionOn
    }
    val dotColor = when {
        effective && scheduleActive -> MonkColors.Mint
        effective -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outline
    }
    MonkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = dotColor, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.protection, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (strict) {
                Icon(Icons.Outlined.Lock, s.strictLocked, tint = MaterialTheme.colorScheme.primary)
            } else {
                Switch(checked = config.enabled, onCheckedChange = { on -> store.updateConfig { it.copy(enabled = on, pausedUntil = 0) } })
            }
        }
        if (config.enabled && permissions.accessibilityEnabled && !strict) {
            if (paused) {
                OutlinedButton(onClick = store::resumeProtection) { Text(s.resume) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.pauseFor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PauseChip(s.pause15) { store.pauseProtection(nowMillis() + 15 * 60_000L) }
                    PauseChip(s.pause60) { store.pauseProtection(nowMillis() + 60 * 60_000L) }
                    PauseChip(s.pauseDay) { store.pauseProtection(nextMidnightMillis()) }
                }
            }
        }
    }
}

@Composable
private fun PauseChip(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) })
}

@Composable
private fun WeekCard(stats: Stats) {
    val s = strings
    val week = lastDates(7)
    val days = week.map { stats.day(it) }
    val paused = days.sumOf { it.intercepted }
    val away = days.sumOf { it.turnedAway }
    val streak = stats.walkAwayStreak(lastDates(90))
    MonkCard {
        Text(s.thisWeek, style = MaterialTheme.typography.titleMedium)
        if (paused == 0) {
            Hint(s.noWeekData)
        } else {
            Text(s.weekLine(paused, away), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(s.successRate(away * 100 / paused), MaterialTheme.colorScheme.tertiary)
                if (streak > 0) StatChip(s.streak(streak), MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun StatChip(text: String, color: androidx.compose.ui.graphics.Color) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(disabledContainerColor = color.copy(alpha = 0.16f), disabledLabelColor = color),
        border = null,
    )
}

@Composable
private fun SetupCard(permissions: PermissionStatus, platform: MonkPlatform) {
    val s = strings
    MonkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(s.setupTitle, style = MaterialTheme.typography.titleMedium)
        }
        LabeledRow(title = s.setupAccessibility, subtitle = s.setupAccessibilityHint) {
            if (permissions.accessibilityEnabled) {
                Icon(Icons.Outlined.CheckCircle, s.enabled, tint = MonkColors.Mint)
            } else {
                Button(onClick = platform::openAccessibilitySettings) { Text(s.enable) }
            }
        }
        if (permissions.mayNeedRestrictedSettingsUnlock) {
            Hint(s.setupRestricted)
            TextButton(onClick = platform::openAppInfo) { Text(s.appInfo) }
        }
    }
}

@Composable
private fun UnsupportedCard() {
    val s = strings
    MonkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PhoneIphone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(8.dp))
            Text(s.iosTitle, style = MaterialTheme.typography.titleMedium)
        }
        Text(s.iosBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyApps(onAddApps: () -> Unit) {
    val s = strings
    MonkCard {
        Text(s.noApps, style = MaterialTheme.typography.titleMedium)
        Text(s.noAppsHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onAddApps) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.size(6.dp))
            Text(s.addApps)
        }
    }
}

@Composable
private fun AppRow(
    app: BlockedApp,
    config: MonkConfig,
    allowedUntil: Long?,
    opensToday: Int,
    onEndAllowance: () -> Unit,
    onClick: () -> Unit,
) {
    val s = strings
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, 40.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ModeChip(app, config)
                        val limit = app.dailyLimit
                        if (limit != null) {
                            val exhausted = opensToday >= limit
                            SmallChip(
                                s.limitToday(opensToday, limit),
                                if (exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (allowedUntil != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        s.openUntil(formatClock(allowedUntil)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onEndAllowance) { Text(s.endNow) }
                }
            }
        }
    }
}

@Composable
private fun SmallChip(text: String, color: androidx.compose.ui.graphics.Color) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(disabledContainerColor = color.copy(alpha = 0.12f), disabledLabelColor = color),
        border = null,
    )
}

@Composable
fun ModeChip(app: BlockedApp, config: MonkConfig) {
    val s = strings
    val block = app.mode == BlockMode.BLOCK
    val container = if (block) MaterialTheme.colorScheme.error.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val content = if (block) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(if (block) s.modeBlock else s.pauseChip(config.delayFor(app))) },
        leadingIcon = {
            Icon(if (block) Icons.Outlined.Block else Icons.Outlined.HourglassEmpty, null, Modifier.size(16.dp))
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = content,
            disabledLeadingIconContentColor = content,
        ),
        border = null,
    )
}
