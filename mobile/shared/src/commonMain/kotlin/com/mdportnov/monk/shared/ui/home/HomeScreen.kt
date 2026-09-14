package com.mdportnov.monk.shared.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.mdportnov.monk.shared.ui.components.Pill
import com.mdportnov.monk.shared.ui.Motion
import com.mdportnov.monk.shared.ui.Motion.itemMotion
import com.mdportnov.monk.shared.ui.components.Counter
import com.mdportnov.monk.shared.ui.components.GlassActionPill
import com.mdportnov.monk.shared.ui.components.AddAppsPillHeight
import com.mdportnov.monk.shared.ui.components.PageHeaderSlot
import androidx.compose.ui.text.font.FontStyle
import dev.chrisbanes.haze.HazeState
import com.mdportnov.monk.shared.ui.components.FitText
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.mdportnov.monk.shared.ui.components.HowItWorksSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.data.lastDates
import com.mdportnov.monk.shared.data.localMoment
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.ProtectionState
import com.mdportnov.monk.shared.model.RuleMode
import com.mdportnov.monk.shared.model.InstalledApp
import com.mdportnov.monk.shared.model.SuggestedApps
import com.mdportnov.monk.shared.model.Stats
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.platform.MonkPlatform
import com.mdportnov.monk.shared.platform.PermissionStatus
import com.mdportnov.monk.shared.ui.components.Hint
import com.mdportnov.monk.shared.ui.components.LabeledRow
import com.mdportnov.monk.shared.ui.components.MonkCard
import com.mdportnov.monk.shared.ui.components.SectionTitle
import com.mdportnov.monk.shared.ui.components.UpdateCard
import com.mdportnov.monk.shared.model.ScreenTimeReport
import com.mdportnov.monk.shared.ui.stats.rememberScreenTime
import androidx.compose.foundation.clickable
import com.mdportnov.monk.shared.ui.Route
import com.mdportnov.monk.shared.ui.LocalOpenRoute
import com.mdportnov.monk.shared.ui.LocalHostActions
import com.mdportnov.monk.shared.ui.theme.MonkColors
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    store: MonkStore,
    platform: MonkPlatform,
    onAddApps: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenStats: () -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    hazeState: HazeState,
) {
    val s = strings
    val config by store.config.collectAsStateWithLifecycle()
    val stats by store.stats.collectAsStateWithLifecycle()
    val allowances by store.allowances.collectAsStateWithLifecycle()
    val permissions by platform.permissions.collectAsStateWithLifecycle()
    // A 30 s heartbeat: pause / strict / allowance countdowns and the schedule flip on their own.
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = nowMillis()
        }
    }
    // Timers move when the wall clock is set by hand (the service shifts them): re-read the clock too.
    LaunchedEffect(config.focusUntil, config.strictUntil, config.pausedUntil) { now = nowMillis() }
    LifecycleResumeEffect(Unit) {
        platform.refreshPermissions()
        now = nowMillis()
        onPauseOrDispose { }
    }
    val apps = remember(config.apps) { config.apps.sortedBy { it.label.lowercase() } }
    val today = remember(now) { localMoment().dateIso }
    // Screen time of the watched apps for the week card; re-read with the heartbeat so "today" keeps moving.
    val watchedPackages = remember(config.apps) { config.apps.map { it.packageName }.toSet() }
    val screenTime = rememberScreenTime(platform, days = 7, packages = watchedPackages, granted = permissions.usageAccessGranted, tick = now / 60_000)

    // The "Add apps" pill floats over the list; without room of its own it would sit on the last
    // row for good, since a short list cannot be scrolled clear of it.
    val pillShown = apps.isNotEmpty() && platform.supportsBlocking
    val direction = LocalLayoutDirection.current
    val listPadding = remember(contentPadding, pillShown, direction) {
        if (!pillShown) contentPadding else PaddingValues(
            start = contentPadding.calculateStartPadding(direction),
            end = contentPadding.calculateEndPadding(direction),
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + AddAppsPillHeight + 12.dp,
        )
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { Header() }
            if (!platform.supportsBlocking) {
                item(key = "unsupported") { UnsupportedCard() }
            } else {
                platform.updater?.let { u -> item(key = "update") { Box(itemMotion()) { UpdateCard(u, compact = true) } } }
                if (permissions.accessibilityEnabled) {
                    item(key = "status") { Box(itemMotion()) { StatusCard(store, config, permissions, now, showControls = apps.isNotEmpty()) } }
                    if (permissions.backgroundNeedsAttention) item(key = "keepalive") { Box(itemMotion()) { KeepAliveCard(permissions, platform) } }
                    if (apps.isNotEmpty() && config.notifyWhenOff && !permissions.notificationsGranted && !config.notifyPromptDismissed) {
                        item(key = "notify") { Box(itemMotion()) { NotifyCard(onDismiss = { store.updateConfig { it.copy(notifyPromptDismissed = true) } }) } }
                    }
                    if (apps.isNotEmpty()) item(key = "today") { Box(itemMotion()) { TodayCard(config, stats, now, today, onOpenStats) } }
                    if (stats.days.isNotEmpty() || screenTime?.available == true) item(key = "week") { Box(itemMotion()) { WeekCard(stats, screenTime, onOpenStats) } }
                } else {
                    item(key = "setup") { Box(itemMotion()) { SetupCard(permissions, platform) } }
                }
            }
            if (!config.helpDismissed && apps.isEmpty()) {
                item(key = "how") { Box(itemMotion()) { HowItWorksCard(onDismiss = { store.updateConfig { it.copy(helpDismissed = true) } }) } }
            }
            item(key = "apps-title") { Box(itemMotion()) { SectionTitle(s.blockedApps, Modifier.padding(top = 8.dp)) } }
            if (apps.isEmpty()) {
                item(key = "empty") { Box(itemMotion()) { EmptyApps(platform, store, onAddApps) } }
            }
            items(apps, key = { it.packageName }) { app ->
                Box(itemMotion()) {
                val moment = localMoment()
                // A Block window open now wins over the allowance in the policy: no "open until" then.
                val ruleBlocked = app.activeRule(moment.dayIso, moment.minuteOfDay)?.mode == RuleMode.BLOCK
                AppRow(
                    app = app,
                    config = config,
                    allowedUntil = allowances[app.packageName]?.takeIf { it > now && !ruleBlocked },
                    opensToday = stats.opensToday(today, app.packageName),
                    onEndAllowance = { store.revokeAllowance(app.packageName) },
                    onClick = { onOpenApp(app.packageName) },
                )
                }
            }
            val gone = config.archivedApps.filter { it.uninstalledAt != null }
            if (gone.isNotEmpty()) {
                item(key = "gone-title") { Box(itemMotion()) { SectionTitle(s.notOnPhone, Modifier.padding(top = 8.dp)) } }
                items(gone, key = { "gone:" + it.packageName }) { app ->
                    Box(itemMotion()) { UninstalledRow(app, onForget = { store.forgetArchived(app.packageName) }) }
                }
            }
        }
        AnimatedVisibility(
            visible = pillShown,
            enter = Motion.appear(),
            exit = Motion.disappear(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = contentPadding.calculateBottomPadding() - 16.dp, end = 20.dp),
        ) {
            GlassActionPill(text = s.addApps, icon = Icons.Outlined.Add, onClick = onAddApps, hazeState = hazeState)
        }
    }
}

@Composable
private fun Header() {
    PageHeaderSlot(Modifier.padding(horizontal = 4.dp), inPage = true) { HomeHeading() }
}

/** The mark and the wordmark. Drawn in the page; it fades before the status bar, the glass bar takes over with the status row. */
@Composable
fun HomeHeading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MonkMark(30.dp)
        Spacer(Modifier.size(12.dp))
        Text(
            buildAnnotatedString {
                append("monk")
                withStyle(SpanStyle(brush = Brush.linearGradient(listOf(MonkColors.Blue, MonkColors.Violet)))) { append("_") }
            },
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
        )
    }
}

/** The "m_" from assets/logo.svg drawn as text — tiny, no vector plumbing needed. */
@Composable
fun MonkMark(size: Dp) {
    Surface(
        color = MonkColors.Ink,
        shape = RoundedCornerShape(size * 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MonkColors.Fog)) { append("m") }
                    withStyle(SpanStyle(brush = Brush.linearGradient(listOf(MonkColors.Blue, MonkColors.Violet)))) { append("_") }
                },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.52f).sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/**
 * The day at a glance: today's counters, a live countdown when a focus session or a pause is
 * running, and one line to keep the reason in view.
 */
@Composable
private fun TodayCard(config: MonkConfig, stats: Stats, now: Long, today: String, onOpenStats: () -> Unit) {
    val s = strings
    val day = stats.day(today)
    val quote = remember(today, config.language) { Quotes.of(config.language, localMoment().dayOfYear) }
    val moment = localMoment()
    val running: Pair<String, Long>? = when (config.state(now, moment.dayIso, moment.minuteOfDay)) {
        ProtectionState.FOCUS -> s.focus to config.focusUntil
        ProtectionState.BREAK -> s.pauseFor to config.pausedUntil
        else -> null
    }
    MonkCard(onClick = onOpenStats) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.statsToday, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (running != null) {
                // The hero already counts minutes down; here the end time, so the two never repeat.
                Pill(
                    if (running.first == s.focus) s.focusUntil(formatClock(running.second)) else s.pausedUntil(formatClock(running.second)),
                    if (running.first == s.focus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Counter(day.intercepted, s.intercepted)
            Counter(day.turnedAway, s.turnedAway, MaterialTheme.colorScheme.tertiary)
            Counter(day.opened, s.opened, MaterialTheme.colorScheme.primary)
        }
        // The counters only add up once every pause has ended; say so while they do not.
        val pending = day.intercepted - day.turnedAway - day.opened
        if (pending > 0) Hint(s.pendingCount(pending))
        // A pull-quote: set a size up in italics, no rule and no quote marks — the type carries it.
        Text(
            quote,
            style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/** The week in one card; the whole card leads to the Stats tab. Screen time appears once usage access is granted. */
@Composable
private fun WeekCard(stats: Stats, screenTime: ScreenTimeReport?, onOpenStats: () -> Unit) {
    val s = strings
    val today = localMoment().dateIso
    val dates = remember(today) { lastDates(7) }
    val days = remember(stats, dates) { dates.map { stats.day(it) } }
    val paused = days.sumOf { it.intercepted }
    val away = days.sumOf { it.turnedAway }
    val streak = remember(stats, today) { stats.walkAwayStreak(lastDates(90)) }
    MonkCard(onClick = onOpenStats) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.thisWeek, style = MaterialTheme.typography.titleMedium)
                if (paused == 0) Hint(s.noWeekData) else Text(s.weekLine(paused, away), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Sparkline(days.map { it.turnedAway }, days.map { it.opened })
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (screenTime?.available == true) {
            // Its own tap target inside the card: the line leads to the full screen-time page, the card to Stats.
            val open = LocalOpenRoute.current
            Row(
                Modifier.fillMaxWidth().clickable { open(Route.ScreenTime) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.screenTimeTodayLine(s.duration(screenTime.phoneByDate[today] ?: 0L), s.duration(screenTime.watchedByDate(today))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        if (paused > 0) {
            // Two sentences now, not two numbers: let them wrap on a narrow screen.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(s.successRate(away * 100 / paused), MaterialTheme.colorScheme.tertiary)
                if (streak > 0) Pill(s.streak(streak), MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Seven tiny stacked bars: walked away over opened, today on the right. */
@Composable
private fun Sparkline(away: List<Int>, opened: List<Int>) {
    val a = MaterialTheme.colorScheme.tertiary
    val o = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = (away.indices.maxOfOrNull { away[it] + opened[it] } ?: 1).coerceAtLeast(1)
    Canvas(Modifier.width(84.dp).height(36.dp)) {
        val n = away.size
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val r = CornerRadius(2.dp.toPx())
        for (i in 0 until n) {
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), r)
            val hO = size.height * opened[i] / max
            val hA = size.height * away[i] / max
            if (hO > 0) drawRoundRect(o, Offset(x, size.height - hO), Size(w, hO), r)
            if (hA > 0) drawRoundRect(a, Offset(x, size.height - hO - hA), Size(w, hA), r)
        }
    }
}

/** Replaces the status card while the service is off: one job, one button, the exact path. */
@Composable
private fun SetupCard(permissions: PermissionStatus, platform: MonkPlatform) {
    val s = strings
    MonkCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Accessibility, null, tint = MaterialTheme.colorScheme.primary) }
            Column {
                Text(s.setupTitle, style = MaterialTheme.typography.titleMedium)
                Text(s.setupAccessibility, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(s.setupAccessibilityHint, style = MaterialTheme.typography.bodyMedium)
        Steps(s.setupSteps)
        Button(onClick = platform::openAccessibilitySettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(s.openAccessibilitySettings) }
        if (permissions.mayNeedRestrictedSettingsUnlock) {
            Hint(s.setupRestricted)
            TextButton(onClick = platform::openAppInfo) { Text(s.appInfo) }
        }
    }
}

/**
 * Android 13+: the watchdog notification needs a runtime grant nobody is asked for otherwise.
 * Shown once there is something to watch; "Not now" hides it for good, Settings keeps the switch.
 */
@Composable
private fun NotifyCard(onDismiss: () -> Unit) {
    val s = strings
    val host = LocalHostActions.current
    MonkCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary) }
            Column {
                Text(s.notifyWhenOff, style = MaterialTheme.typography.titleMedium)
                Text(s.system, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(s.notifyPromptBody, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = host.requestNotificationPermission, modifier = Modifier.weight(1f)) { FitText(s.notifyPermission) }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { FitText(s.notNow) }
        }
    }
}

/**
 * The OS side of "the service must not die": shown under the status card only while a switch
 * still points the wrong way, in the setup card's clothes. Each row is a live check with the
 * one deep link that flips it.
 */
@Composable
private fun KeepAliveCard(permissions: PermissionStatus, platform: MonkPlatform) {
    val s = strings
    MonkCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.BatteryAlert, null, tint = MaterialTheme.colorScheme.primary) }
            Column {
                Text(s.keepAliveTitle, style = MaterialTheme.typography.titleMedium)
                Text(s.system, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(s.keepAliveHint, style = MaterialTheme.typography.bodyMedium)
        val batteryOk = permissions.batteryUnrestricted && !permissions.backgroundRestricted && !permissions.sleeping
        KeepAliveRow(
            ok = batteryOk,
            title = s.battery,
            detail = when {
                permissions.backgroundRestricted -> s.batteryRestricted
                permissions.sleeping -> s.batterySleeping
                !permissions.batteryUnrestricted -> s.batteryOptimized
                else -> s.batteryUnrestricted
            },
            action = s.allow,
            onAction = { if (permissions.backgroundRestricted || permissions.sleeping) platform.openAppInfo() else platform.requestBatteryUnrestricted() },
        )
        if (permissions.samsung) Hint(s.samsungSleepHint)
    }
}

@Composable
private fun KeepAliveRow(ok: Boolean, title: String, detail: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ok) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Numbered circles with one line each. */
@Composable
private fun Steps(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { i, text ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary) }
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
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
private fun HowItWorksCard(onDismiss: () -> Unit) {
    val s = strings
    var open by rememberSaveable { mutableStateOf(false) }
    MonkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.howTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(s.gotIt) }
        }
        Steps(listOf(s.howStep1, s.howStep2, s.howStep3).map { it.substringAfter(". ") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open = true }) { Text(s.learnMore) }
        }
    }
    if (open) HowItWorksSheet(onDismiss = { open = false })
}

@Composable
private fun EmptyApps(platform: MonkPlatform, store: MonkStore, onAddApps: () -> Unit) {
    val s = strings
    var suggested by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    LaunchedEffect(Unit) { if (platform.supportsBlocking) suggested = SuggestedApps.pick(platform.installedApps()) }
    MonkCard {
        Text(s.noApps, style = MaterialTheme.typography.titleMedium)
        Text(s.noAppsHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (suggested.isNotEmpty()) {
            Text(s.suggested, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                suggested.forEach { app ->
                    AssistChip(
                        onClick = { store.addApp(app.packageName, app.label) },
                        label = { Text(app.label) },
                        leadingIcon = { AppIcon(app.packageName, 18.dp) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (suggested.isNotEmpty()) {
                Button(
                    onClick = { suggested.forEach { store.addApp(it.packageName, it.label) } },
                    modifier = Modifier.weight(1f),
                ) { FitText(s.addSuggested) }
            }
            OutlinedButton(onClick = onAddApps, modifier = Modifier.weight(1f)) {
                FitText(if (suggested.isNotEmpty()) s.chooseManually else s.addApps)
            }
        }
    }
}

@Composable
private fun UninstalledRow(app: BlockedApp, onForget: () -> Unit) {
    val s = strings
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.PhoneIphone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(s.uninstalledHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onForget) { Text(s.forget) }
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
                AppIcon(app.packageName, 44.dp)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ModeChip(app, config)
                        val limit = app.dailyLimit
                        if (limit != null && app.limitApplies) {
                            val exhausted = opensToday >= limit
                            Pill(
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

/** What the app does right now: a rule open at this hour overrides the mode, as it does in the policy. */
@Composable
fun ModeChip(app: BlockedApp, config: MonkConfig) {
    val s = strings
    val moment = localMoment()
    val rule = app.activeRule(moment.dayIso, moment.minuteOfDay)
    val block = if (rule != null) rule.mode == RuleMode.BLOCK else app.mode == BlockMode.BLOCK
    val free = rule?.mode == RuleMode.FREE
    val content = when {
        block -> MaterialTheme.colorScheme.error
        free -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Pill(
        when {
            block -> s.modeBlock
            free -> s.ruleFree
            else -> s.pauseChip(config.delayFor(app))
        },
        content,
        icon = { Icon(if (block) Icons.Outlined.Block else if (free) Icons.Outlined.Schedule else Icons.Outlined.HourglassEmpty, null, Modifier.size(14.dp), tint = content) },
    )
}
