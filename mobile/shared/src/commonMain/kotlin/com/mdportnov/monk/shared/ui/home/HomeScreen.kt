package com.mdportnov.monk.shared.ui.home

import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.localMoment
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.platform.MonkPlatform
import com.mdportnov.monk.shared.platform.PermissionStatus
import com.mdportnov.monk.shared.ui.components.LabeledRow
import com.mdportnov.monk.shared.ui.components.MonkCard
import com.mdportnov.monk.shared.ui.components.SectionTitle
import com.mdportnov.monk.shared.ui.theme.MonkColors

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
    var permissions by remember { mutableStateOf(platform.permissions()) }
    LifecycleResumeEffect(Unit) {
        permissions = platform.permissions()
        onPauseOrDispose { }
    }
    val apps = remember(config.apps) { config.apps.sortedBy { it.label.lowercase() } }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header() }
            if (!platform.supportsBlocking) {
                item { UnsupportedCard() }
            } else {
                item { StatusCard(config, permissions, onToggle = { on -> store.updateConfig { it.copy(enabled = on) } }) }
                if (!permissions.accessibilityEnabled) {
                    item { SetupCard(permissions, platform) }
                }
            }
            item { SectionTitle(s.blockedApps, Modifier.padding(top = 8.dp)) }
            if (apps.isEmpty()) {
                item { EmptyApps(onAddApps) }
            }
            items(apps, key = { it.packageName }) { app ->
                AppRow(app, config, onClick = { onOpenApp(app.packageName) })
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

/** The "m" from assets/logo.svg drawn as text — tiny, no vector plumbing needed. */
@Composable
fun MonkMark(size: androidx.compose.ui.unit.Dp) {
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
private fun StatusCard(config: MonkConfig, permissions: PermissionStatus, onToggle: (Boolean) -> Unit) {
    val s = strings
    // Re-evaluated once a minute so "Paused by schedule" flips on its own.
    var moment by remember { mutableStateOf(localMoment()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            moment = localMoment()
        }
    }
    val scheduleActive = config.schedule.isActive(moment.dayIso, moment.minuteOfDay)
    val effective = config.enabled && permissions.accessibilityEnabled
    val subtitle = when {
        !config.enabled -> s.protectionOff
        !permissions.accessibilityEnabled -> s.setupAccessibility
        !scheduleActive -> s.protectionPaused
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
            Switch(checked = config.enabled, onCheckedChange = onToggle)
        }
    }
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
        PermissionRow(
            title = s.setupAccessibility,
            hint = s.setupAccessibilityHint,
            granted = permissions.accessibilityEnabled,
            grantedLabel = s.enabled,
            action = s.enable,
            onAction = platform::openAccessibilitySettings,
        )
        if (permissions.mayNeedRestrictedSettingsUnlock) {
            Text(s.setupRestricted, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = platform::openAppInfo) { Text(s.appInfo) }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    hint: String,
    granted: Boolean,
    grantedLabel: String,
    action: String,
    onAction: () -> Unit,
) {
    LabeledRow(title = title, subtitle = hint) {
        if (granted) {
            Icon(Icons.Outlined.CheckCircle, grantedLabel, tint = MonkColors.Mint)
        } else {
            Button(onClick = onAction) { Text(action) }
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
private fun AppRow(app: BlockedApp, config: MonkConfig, onClick: () -> Unit) {
    val s = strings
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, 40.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                ModeChip(app, config)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
