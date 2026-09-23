package com.mdportnov.monk.shared.ui.home

import com.mdportnov.monk.shared.ui.rememberNow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.data.localMoment
import com.mdportnov.monk.shared.data.nextMidnightMillis
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.data.clockAfterWallMinutes
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.ProtectionState
import com.mdportnov.monk.shared.model.Routine
import com.mdportnov.monk.shared.ui.LocalOpenRoute
import com.mdportnov.monk.shared.ui.Route
import com.mdportnov.monk.shared.ui.routines.RoutineStartSheet
import com.mdportnov.monk.shared.platform.PermissionStatus
import com.mdportnov.monk.shared.ui.Motion
import com.mdportnov.monk.shared.ui.rememberFrameClock
import com.mdportnov.monk.shared.ui.components.CountdownConfirm
import com.mdportnov.monk.shared.ui.components.rememberHaptics
import com.mdportnov.monk.shared.ui.theme.MonkColors
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.ui.HeaderAnchor
import com.mdportnov.monk.shared.ui.LocalHeaderAnchor
import com.mdportnov.monk.shared.ui.components.PageTitle
import com.mdportnov.monk.shared.ui.components.smoothstep
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The state of protection as the emotional centre of Home: a full-bleed aurora whose colour is
 * the mood of the state (mint = calm and on, blue = strict, violet = deep focus, amber = a warm
 * break, ash = off), the state or the time left set big, a countdown ring around the glyph, and
 * the break / focus actions as tonal chips cut from the same surface. Every state change is one
 * morph: the aurora re-colours, the glyph melts, the words fade through.
 */
@Composable
internal fun StatusCard(store: MonkStore, config: MonkConfig, permissions: PermissionStatus, now: Long, showControls: Boolean) {
    val s = strings
    val moment = localMoment()
    val scheduleActive = config.schedule.isActive(moment.dayIso, moment.minuteOfDay)
    val strict = config.isStrict(now)
    val paused = config.isPaused(now)
    // The chosen length, not an end time: the end is worked out when the breath is over, so a
    // dialog left open (or restored after the app was away) never starts a shorter break.
    var breakCandidate by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmOff by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) { if (notice != null) { delay(2600); notice = null } }
    val haptics = rememberHaptics()
    val lockedCount = config.apps.count { it.locked }

    val running = config.activeRun(now) != null
    val look = lookFor(config, now)
    val palette = paletteFor(look.mood)
    val tick = rememberTicker(active = look.until != null)
    // As the card slides under the glass bar its header hands over to the bar's condensed row.
    val anchor = LocalHeaderAnchor.current

    Box(
        Modifier
            .fillMaxWidth()
            .animateContentSize(Motion.contentSize)
            .clip(MaterialTheme.shapes.large)
            .aurora(palette),
    ) {
        CompositionLocalProvider(LocalContentColor provides palette.content) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Once the card starts sliding under the bar, the bar draws these four elements
                // itself, each travelling from exactly here: the words and the switch hand over at
                // once (same pixels), the glyph's glow and ring dissolve under the lifted icon.
                val handedOver = Modifier.graphicsLayer { alpha = if ((anchor?.progress() ?: 0f) > 0.02f) 0f else 1f }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // The lamp dissolves a little faster than the icon leaves, so the icon lifts out of it.
                    Box(Modifier.graphicsLayer { alpha = 1f - smoothstep(0f, 0.3f, anchor?.progress() ?: 0f) }) {
                        StateGlyph(look.icon, look.emoji, palette, look.until, look.total, tick, iconModifier = handedOver)
                    }
                    Column(Modifier.weight(1f).then(handedOver), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(look.eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = palette.muted)
                        AnimatedContent(
                            targetState = look.title ?: COUNTDOWN,
                            transitionSpec = { Motion.fadeThrough() },
                            contentAlignment = Alignment.CenterStart,
                            label = "title",
                        ) { title ->
                            if (title == COUNTDOWN && look.until != null) {
                                CountdownText(look.until, tick, palette.content)
                            } else {
                                TitleText(title, palette.content)
                            }
                        }
                    }
                    Box(handedOver) {
                        StateControl(
                            locked = strict || running,
                            enabled = config.enabled,
                            palette = palette,
                            onLockedTap = {
                                haptics.reject()
                                val run = config.activeRun(now)
                                val routine = config.runningRoutine(now)
                                notice = if (run != null && routine != null) {
                                    "${s.routineName(routine)} · ${s.routineRunsUntil(formatClock(run.until))} · ${s.cannotStop}"
                                } else {
                                    s.strictNoChange(formatClock(config.strictUntil))
                                }
                            },
                            onToggle = { on ->
                                haptics.toggle(on)
                                when {
                                    on -> store.switchOn()
                                    // The breath is skipped only when there is genuinely nothing
                                    // to soften: no routine in force, no session, no break.
                                    config.state(now, moment.dayIso, moment.minuteOfDay) == ProtectionState.SCHEDULED_OFF -> store.switchOff()
                                    else -> confirmOff = true
                                }
                            },
                        )
                    }
                }
                AnimatedContent(targetState = look.detail, transitionSpec = { Motion.fadeThrough() }, label = "detail") { detail ->
                    if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                }
                val routinesArmed = look.mood == Mood.Scheduled && config.routines.any { it.enabled && !it.coversNothing }
                // A break outside the base hours is not the headline, but it is still running and
                // the user still has to be able to see it and end it.
                val breakElsewhere = paused && look.mood != Mood.Break
                if (lockedCount > 0 || routinesArmed || breakElsewhere || (strict && look.mood != Mood.Strict)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (breakElsewhere) Tag(Icons.Outlined.Coffee, s.pausedUntil(formatClock(config.pausedUntil)), palette)
                        if (routinesArmed) Tag(Icons.Outlined.AutoAwesome, s.scheduleRoutinesStillWork, palette)
                        if (strict && look.mood != Mood.Strict) Tag(Icons.Outlined.Lock, s.strictShort, palette)
                        if (lockedCount > 0) Tag(Icons.Outlined.Lock, s.lockedCount(lockedCount), palette)
                    }
                }
                if (!config.enabled && lockedCount > 0) Text(s.lockedStayOn, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                if (look.mood == Mood.Scheduled && config.enabled) Text(s.scheduleSwitchWarning, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                AnimatedVisibility(visible = notice != null, enter = Motion.reveal(), exit = Motion.conceal()) {
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(palette.content.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Lock, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                        Text(notice.orEmpty(), style = MaterialTheme.typography.bodySmall, color = palette.content)
                    }
                }
                // Only the break lives on the card now. Routines have their own card under this
                // one: a strip of chips could not say which of them is on, what it covers or when
                // it ends, and those are the three things anyone looks for.
                if (showControls && config.enabled && permissions.accessibilityEnabled && !running) {
                    if (paused) {
                        AuroraChip(s.resume, palette, icon = Icons.Outlined.Shield, modifier = Modifier.fillMaxWidth()) { store.resumeProtection() }
                    } else if (!strict && config.canStartBreak(now, moment.dayIso, moment.minuteOfDay)) {
                        ActionStrip(
                            Icons.Outlined.Coffee, s.pauseFor, s.breakWhat, palette,
                            listOf(
                                ChipAction(s.pause5) { breakCandidate = 5 },
                                ChipAction(s.pause15) { breakCandidate = 15 },
                                ChipAction(s.pause60) { breakCandidate = 60 },
                                ChipAction(s.pauseDay) { breakCandidate = BREAK_UNTIL_MIDNIGHT },
                            ),
                        )
                    } else if (!strict && scheduleActive && !paused) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Coffee, null, tint = palette.muted, modifier = Modifier.size(16.dp))
                            Text(s.breakCooldown(formatClock(config.nextBreakAt(now))), style = MaterialTheme.typography.bodySmall, color = palette.muted)
                        }
                    }
                }
            }
        }
    }
    breakCandidate?.let { minutes ->
        fun until() = if (minutes == BREAK_UNTIL_MIDNIGHT) nextMidnightMillis() else nowMillis() + minutes * 60_000L
        // A routine started from a tile, or the cooldown, can take the break away mid-breath.
        val canBreak = config.canStartBreak(now, moment.dayIso, moment.minuteOfDay)
        LaunchedEffect(canBreak) { if (!canBreak) breakCandidate = null }
        // A break weakens protection for the whole list, so it costs the same breath as "off".
        // Routines that hold through one are named before the breath, not discovered after it.
        val holds = config.routinesThroughBreak(now, moment.dayIso, moment.minuteOfDay)
        CountdownConfirm(
            title = s.breakConfirmTitle,
            body = s.breakConfirmBody(formatClock(until())),
            confirmText = s.startBreak,
            note = listOfNotNull(
                s.lockedStayOn.takeIf { lockedCount > 0 },
                holds.firstOrNull()?.let { s.breakKeepsRoutine(s.routineName(it)) },
            ).joinToString(" ").ifBlank { null },
            onConfirm = { store.pauseProtection(until()); breakCandidate = null },
            onDismiss = { breakCandidate = null },
        )
    }
    if (confirmOff) {
        CountdownConfirm(
            title = s.offConfirmTitle,
            body = s.offConfirmBody,
            confirmText = s.switchOff,
            note = s.lockedStayOn.takeIf { lockedCount > 0 },
            onConfirm = { store.switchOff(); confirmOff = false },
            onDismiss = { confirmOff = false },
        )
    }
}

private const val COUNTDOWN = " countdown"

private const val BREAK_UNTIL_MIDNIGHT = -1

private enum class Mood { On, Strict, Routine, Break, Off, Scheduled }

/**
 * [title] is the big word; null means the time left takes its place. [emoji] stands in for
 * [icon] when a routine has one, so the thing in force wears its own face on the card, in the
 * bar and on the pause screen alike.
 */
private class Look(
    val mood: Mood,
    val icon: ImageVector,
    val eyebrow: String,
    val title: String?,
    val detail: String?,
    val until: Long? = null,
    val total: Long? = null,
    val emoji: String = "",
)

/** One look per [ProtectionState]; the state itself is the model's call, so every surface agrees. */
@Composable
private fun lookFor(config: MonkConfig, now: Long): Look {
    val s = strings
    val moment = localMoment()
    return when (config.state(now, moment.dayIso, moment.minuteOfDay)) {
        ProtectionState.OFF -> Look(Mood.Off, Icons.Outlined.PowerSettingsNew, s.protection, s.protectionOff, s.offNudge)
        ProtectionState.ROUTINE -> routineLook(config, now, moment.dayIso, moment.minuteOfDay)
        ProtectionState.BREAK -> Look(Mood.Break, Icons.Outlined.Coffee, s.pauseFor, null, "${s.until(formatClock(config.pausedUntil))} · ${s.comesBackItself}", config.pausedUntil, config.pauseStartedAt.takeIf { it > 0 }?.let { config.pausedUntil - it })
        ProtectionState.SCHEDULED_OFF -> Look(
            Mood.Scheduled, Icons.Outlined.Schedule, s.protection, s.protectionOff,
            config.schedule.minutesToNextChange(moment.dayIso, moment.minuteOfDay)
                ?.let { s.scheduleBackAt(formatClock(clockAfterWallMinutes(it))) } ?: s.scheduleOffNudge,
        )
        ProtectionState.STRICT -> Look(Mood.Strict, Icons.Outlined.Lock, s.protection, s.protectionOn, s.strictUntil(formatClock(config.strictUntil)))
        ProtectionState.ON -> Look(Mood.On, Icons.Outlined.Shield, s.protection, s.protectionOn, if (config.apps.isEmpty()) null else s.appsWatched(config.apps.size))
    }
}

/**
 * A routine in force. Started by hand it counts down and says it cannot be stopped; open on its
 * own hours it names the hour it closes, because that is the only promise it made. Either way
 * the routine's own name is the big word and its emoji is the glyph.
 */
@Composable
private fun routineLook(config: MonkConfig, now: Long, dayIso: Int, minuteOfDay: Int): Look {
    val s = strings
    val routine = config.leadingRoutine(now, dayIso, minuteOfDay)
        ?: return Look(Mood.On, Icons.Outlined.Shield, s.protection, s.protectionOn, null)
    val run = config.activeRun(now)?.takeIf { it.routineId == routine.id }
    val name = s.routineName(routine)
    if (run != null) {
        return Look(
            Mood.Routine, Icons.Outlined.AutoAwesome, s.routineEyebrow, null,
            "$name · ${s.until(formatClock(run.until))} · ${s.cannotStop}",
            run.until,
            run.startedAt.takeIf { it > 0 }?.let { run.until - it },
            routine.emoji,
        )
    }
    // Wall-clock minutes, resolved through the zone, so a window ending after a DST change says
    // the hour it will actually end at rather than the one arithmetic would suggest.
    val endsIn = routine.openUntilMinutes(dayIso, minuteOfDay)
    val detail = if (endsIn == null) s.routineOpenNow else s.routineOpenUntil(formatClock(clockAfterWallMinutes(endsIn)))
    return Look(Mood.Routine, Icons.Outlined.AutoAwesome, s.routineEyebrow, name, detail, emoji = routine.emoji)
}

/** The switch, or a locked switch that explains itself when touched instead of a dead icon. */
@Composable
private fun StateControl(locked: Boolean, enabled: Boolean, palette: Palette, onLockedTap: () -> Unit, onToggle: (Boolean) -> Unit) {
    if (locked) {
        IconButton(onClick = onLockedTap) { Icon(Icons.Outlined.Lock, strings.strictLocked, tint = palette.accent) }
    } else {
        // On is instant; off goes through the same pause the app itself asks for.
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.base,
                checkedTrackColor = palette.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = palette.content.copy(alpha = 0.7f),
                uncheckedTrackColor = palette.content.copy(alpha = 0.08f),
                uncheckedBorderColor = palette.content.copy(alpha = 0.3f),
            ),
        )
    }
}

/**
 * The status card's header as the glass bar draws it: the same glyph icon, eyebrow, state word
 * (or time left) and switch, measured at the card's own sizes. Each element travels on its own
 * straight line from exactly where it sits in the card to its slot in the bar, scaling on the
 * way, so the header flows into the bar instead of being swapped for a smaller one. It lives
 * in the bar and watches the store itself, so it stays true after the card has scrolled out of
 * composition. Without a status card on the page it is the wordmark.
 */
@Composable
internal fun CompactStatus(store: MonkStore, anchor: HeaderAnchor) {
    val hasCard by remember(anchor) { derivedStateOf { anchor.cardTop?.invoke() != null } }
    if (!hasCard) {
        PageTitle("monk_")
        return
    }
    val config by store.config.collectAsStateWithLifecycle()
    val now = rememberNow(listOf(config.run?.until, config.strictUntil, config.pausedUntil))
    val look = lookFor(config, now)
    val state = remember(config, now) { localMoment().let { config.state(now, it.dayIso, it.minuteOfDay) } }
    val palette = paletteFor(look.mood)
    SideEffect { anchor.tint = palette.accent }
    val accent by animateColorAsState(palette.accent, Motion.standard(Motion.Long), label = "accent")
    val tick = rememberTicker(active = look.until != null)
    val locked = config.isStrict(now) || config.activeRun(now) != null
    val haptics = rememberHaptics()
    var confirmOff by remember { mutableStateOf(false) }
    val lockedCount = config.apps.count { it.locked }
    val s = strings
    val timed = look.title == null && look.until != null
    // What the bar says once condensed: a proper title, and a live line under it where useful.
    val moment = localMoment()
    val leading = config.leadingRoutine(now, moment.dayIso, moment.minuteOfDay)
    val compactTitle = when (look.mood) {
        Mood.On -> s.compactOn
        Mood.Off -> s.compactOff
        Mood.Scheduled -> s.compactBySchedule
        Mood.Strict -> s.compactUntil(s.strictShort, formatClock(config.strictUntil))
        Mood.Routine -> {
            val name = leading?.let { s.routineName(it) } ?: s.routineEyebrow
            val until = config.activeRun(now)?.until
                ?: leading?.openUntilMinutes(moment.dayIso, moment.minuteOfDay)?.let { clockAfterWallMinutes(it) }
            if (until == null) name else s.compactUntil(name, formatClock(until))
        }
        Mood.Break -> s.compactUntil(s.pauseFor, formatClock(config.pausedUntil))
    }
    val compactDetail: String? = when {
        timed -> null // the countdown itself is the detail
        look.mood == Mood.On || look.mood == Mood.Strict -> config.apps.size.takeIf { it > 0 }?.let { s.compactApps(it) }
        else -> null
    }
    val big = if (timed) MaterialTheme.typography.displayMedium.fontSize.value else MaterialTheme.typography.headlineLarge.fontSize.value
    val small = MaterialTheme.typography.labelSmall.fontSize.value
    val eyebrowSize = MaterialTheme.typography.labelMedium.fontSize.value
    val titleSize = MaterialTheme.typography.titleMedium.fontSize.value
    val statusTop = with(LocalDensity.current) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
    MorphRow(
        anchor = anchor,
        statusTop = statusTop,
        eyebrowScale = small / eyebrowSize,
        titleScale = titleSize / big,
        hasDetail = timed || compactDetail != null,
        icon = {
            AnimatedContent(targetState = look.icon to look.emoji, transitionSpec = { Motion.fadeThrough() }, label = "icon") { (ic, face) ->
                if (face.isNotEmpty()) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Text(face, fontSize = 22.sp, maxLines = 1) }
                } else {
                    Icon(ic, null, tint = accent, modifier = Modifier.size(28.dp))
                }
            }
        },
        eyebrow = { Text(look.eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = palette.muted, maxLines = 1) },
        compactTitle = { Text(compactTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = palette.content, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        title = {
            AnimatedContent(targetState = look.title ?: COUNTDOWN, transitionSpec = { Motion.fadeThrough() }, contentAlignment = Alignment.CenterStart, label = "title") { title ->
                if (title == COUNTDOWN && look.until != null) CountdownText(look.until, tick, palette.content)
                else TitleText(title, palette.content, fill = false)
            }
        },
        compactDetail = {
            val style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
            if (timed) Text(countdown(look.until - tick.value), style = style, color = palette.muted, maxLines = 1)
            else if (compactDetail != null) Text(compactDetail, style = style, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        control = {
            StateControl(
                locked = locked,
                enabled = config.enabled,
                palette = palette,
                onLockedTap = { haptics.reject() },
                onToggle = { on ->
                    haptics.toggle(on)
                    when {
                        on -> store.switchOn()
                        // Same rule as the card: the breath is skipped only when nothing is in force.
                        state == ProtectionState.SCHEDULED_OFF -> store.switchOff()
                        else -> confirmOff = true
                    }
                },
            )
        },
    )
    if (confirmOff) {
        CountdownConfirm(
            title = s.offConfirmTitle,
            body = s.offConfirmBody,
            confirmText = s.switchOff,
            note = s.lockedStayOn.takeIf { lockedCount > 0 },
            onConfirm = { store.switchOff(); confirmOff = false },
            onDismiss = { confirmOff = false },
        )
    }
}

/**
 * Lays the header out twice — as the card's row (glyph box 64 dp, 16 dp gaps, the column of
 * eyebrow and title taking the rest, the control at the end) and as the bar's line (28 dp icon
 * · a stack of overline over compact title · the control at 0.8×, centred) — and places every
 * element at the lerp of the two by the anchor's progress, in its own graphics layer, so a
 * scroll moves a handful of layers and measures nothing. The eyebrow becomes the small overline
 * and the big state word grows into the compact title: each pair shares one path and one size track
 * (the text grows or shrinks along it) and crosses over in the middle, so the words change
 * without anything jumping. Coordinates are the root's; the bar's content box starts at [statusTop].
 */
@Composable
private fun MorphRow(
    anchor: HeaderAnchor,
    statusTop: Float,
    eyebrowScale: Float,
    titleScale: Float,
    hasDetail: Boolean,
    icon: @Composable () -> Unit,
    eyebrow: @Composable () -> Unit,
    compactTitle: @Composable () -> Unit,
    title: @Composable () -> Unit,
    compactDetail: @Composable () -> Unit,
    control: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(icon, eyebrow, compactTitle, title, compactDetail, control),
        modifier = Modifier.graphicsLayer { alpha = if (anchor.progress() > 0.02f) 1f else 0f },
    ) { lists, constraints ->
        val iconM = lists[0]; val eyeM = lists[1]; val ctitleM = lists[2]; val titleM = lists[3]; val detailM = lists[4]; val ctlM = lists[5]
        val barW = constraints.maxWidth
        val barH = 56.dp.roundToPx()
        val glyph = 64.dp.roundToPx()
        val gap = 16.dp.roundToPx()
        val rowW = (anchor.cardWidth - 2 * anchor.cardPad).toInt().coerceAtLeast(0)
        val ic = iconM.first().measure(Constraints())
        val ctl = ctlM.first().measure(Constraints())
        val ctlScale = if (ctl.height > 40.dp.roundToPx()) 0.72f else 0.8f
        val colW = (rowW - glyph - 2 * gap - ctl.width).coerceAtLeast(0)
        val eye = eyeM.first().measure(Constraints(maxWidth = colW))
        val ti = titleM.first().measure(Constraints(maxWidth = colW))
        val g = 12.dp.toPx()
        // The compact stack must fit the bar beside the icon and the control, margins included.
        val stackMax = (barW - 2 * 24.dp.toPx() - ic.width - g - g - ctl.width * ctlScale).toInt().coerceAtLeast(0)
        val ct = ctitleM.first().measure(Constraints(maxWidth = stackMax))
        val de = detailM.firstOrNull()?.measure(Constraints(maxWidth = stackMax))
        val colH = eye.height + 2.dp.roundToPx() + ti.height
        val rowH = maxOf(glyph, colH, ctl.height)
        val ltr = layoutDirection == LayoutDirection.Ltr
        // Card positions, relative to the header row's top-start.
        val aIcon = Offset((glyph - ic.width) / 2f, (rowH - ic.height) / 2f)
        val colTop = (rowH - colH) / 2f
        val aEye = Offset((glyph + gap).toFloat(), colTop)
        val aTitle = Offset((glyph + gap).toFloat(), colTop + eye.height + 2.dp.toPx())
        val aCtl = Offset((rowW - ctl.width).toFloat(), (rowH - ctl.height) / 2f)
        // Bar positions: one centred line.
        val deH = if (hasDetail && de != null) de.height else 0
        val stackW = maxOf(ct.width, de?.width ?: 0).toFloat()
        val stackH = ct.height + deH
        val lineW = ic.width + g + stackW + g + ctl.width * ctlScale
        var x = (barW - lineW) / 2f
        val cy = barH / 2f
        val bIcon = Offset(x, cy - ic.height / 2f); x += ic.width + g
        val bDetail = Offset(x, cy - stackH / 2f)
        val bTitle = Offset(x, cy - stackH / 2f + deH); x += stackW + g
        val bCtl = Offset(x, cy - ctl.height * ctlScale / 2f)
        layout(barW, barH) {
            fun Placeable.morph(a: Offset, b: Offset, startScale: Float, endScale: Float, fade: Float) {
                val w = width
                placeWithLayer(0, 0) {
                    val p = anchor.progress()
                    val row = anchor.rowTop() ?: return@placeWithLayer
                    val rowX = anchor.cardX + anchor.cardPad
                    val s = lerp(startScale, endScale, p)
                    val ax = if (ltr) rowX + a.x else rowX + rowW - a.x - w * startScale
                    val bx = if (ltr) b.x else barW - b.x - w * endScale
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = s
                    scaleY = s
                    // Once the card has scrolled past, its row is at −∞ and there is nothing left to
                    // travel from: the line belongs to the bar outright. Lerping from infinity would
                    // give NaN and place the whole row nowhere — an empty bar.
                    val settled = !row.isFinite()
                    translationX = if (settled) bx else lerp(ax, bx, p)
                    translationY = if (settled) b.y else lerp(row + a.y - statusTop, b.y, p)
                    alpha = when {
                        fade > 0f -> smoothstep(0.45f, 0.75f, p)
                        fade < 0f -> 1f - smoothstep(0.35f, 0.65f, p)
                        else -> 1f
                    }
                }
            }
            ic.morph(aIcon, bIcon, 1f, 1f, 0f)
            ctl.morph(aCtl, bCtl, 1f, ctlScale, 0f)
            // Eyebrow → the small overline (apps count or countdown): small to small, same slot.
            eye.morph(aEye, bDetail, 1f, eyebrowScale, -1f)
            de?.morph(aEye, bDetail, 1f / eyebrowScale, 1f, if (hasDetail) 1f else -1f)
            // Big state word → compact title: "On" grows into "Protection on" along one size track.
            ti.morph(aTitle, bTitle, 1f, titleScale, -1f)
            ct.morph(aTitle, bTitle, 1f / titleScale, 1f, 1f)
        }
    }
}

/** The colours of one mood: the ground, three aurora blobs, the accent, and text on top of it all. */
private class Palette(val base: Color, val a: Color, val b: Color, val c: Color, val accent: Color, val content: Color, val muted: Color)

private val Amber = Color(0xFFE0AF68)
private val Peach = Color(0xFFFF9E64)
private val Cyan = Color(0xFF7DCFFF)

@Composable
private fun paletteFor(mood: Mood): Palette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scheme = MaterialTheme.colorScheme
    return if (dark) {
        val ink = MonkColors.Fog
        val soft = ink.copy(alpha = 0.59f)
        when (mood) {
            Mood.On -> Palette(Color(0xFF101B22), MonkColors.Mint.copy(alpha = 0.36f), MonkColors.Blue.copy(alpha = 0.28f), MonkColors.Violet.copy(alpha = 0.20f), MonkColors.Mint, ink, soft)
            Mood.Strict -> Palette(Color(0xFF10162A), MonkColors.Blue.copy(alpha = 0.39f), MonkColors.Violet.copy(alpha = 0.27f), Cyan.copy(alpha = 0.13f), MonkColors.Blue, ink, soft)
            Mood.Routine -> Palette(Color(0xFF130C2A), MonkColors.Violet.copy(alpha = 0.41f), MonkColors.Blue.copy(alpha = 0.27f), Cyan.copy(alpha = 0.11f), MonkColors.Violet, ink, soft)
            Mood.Break -> Palette(Color(0xFF221410), Amber.copy(alpha = 0.35f), Peach.copy(alpha = 0.25f), MonkColors.Rose.copy(alpha = 0.11f), Amber, ink, soft)
            Mood.Off -> Palette(Color(0xFF15171E), Color(0xFF3A4358).copy(alpha = 0.32f), Color(0xFF2B3244).copy(alpha = 0.28f), MonkColors.Violet.copy(alpha = 0.04f), Color(0xFFA9B1C3), ink, soft)
            Mood.Scheduled -> Palette(Color(0xFF14171F), Color(0xFF3A4358).copy(alpha = 0.32f), MonkColors.Blue.copy(alpha = 0.10f), MonkColors.Violet.copy(alpha = 0.06f), Color(0xFFA9B1C3), ink, soft)
        }
    } else {
        val ink = Color(0xFF12151C)
        val soft = ink.copy(alpha = 0.66f)
        when (mood) {
            Mood.On -> Palette(Color(0xFFEAF5EC), MonkColors.Mint.copy(alpha = 0.40f), MonkColors.Blue.copy(alpha = 0.26f), MonkColors.Violet.copy(alpha = 0.18f), Color(0xFF3E7A1F), ink, soft)
            Mood.Strict -> Palette(Color(0xFFE8EEFC), MonkColors.Blue.copy(alpha = 0.40f), MonkColors.Violet.copy(alpha = 0.26f), Cyan.copy(alpha = 0.22f), Color(0xFF3D63C9), ink, soft)
            Mood.Routine -> Palette(Color(0xFFEDE6FB), MonkColors.Violet.copy(alpha = 0.48f), MonkColors.Blue.copy(alpha = 0.30f), Cyan.copy(alpha = 0.18f), Color(0xFF7455B8), ink, soft)
            Mood.Break -> Palette(Color(0xFFFCF1E3), Amber.copy(alpha = 0.46f), Peach.copy(alpha = 0.34f), MonkColors.Rose.copy(alpha = 0.14f), Color(0xFFB4691A), ink, soft)
            Mood.Off -> Palette(Color(0xFFEDEFF3), Color(0xFFB9C0D0).copy(alpha = 0.50f), Color(0xFFD6DBE6).copy(alpha = 0.50f), MonkColors.Violet.copy(alpha = 0.06f), scheme.onSurfaceVariant, ink, soft)
            Mood.Scheduled -> Palette(Color(0xFFECEFF5), Color(0xFFB9C0D0).copy(alpha = 0.50f), MonkColors.Blue.copy(alpha = 0.14f), MonkColors.Violet.copy(alpha = 0.08f), scheme.onSurfaceVariant, ink, soft)
        }
    }
}

private const val TWO_PI = (2 * PI).toFloat()

/**
 * The aurora: three soft blobs of the mood's colours drifting slowly over the ground, drawn on
 * every frame from one clock read in the draw phase, so nothing recomposes. Colours tween on the
 * standard curve when the mood changes, which is what makes a state change feel like weather
 * turning rather than a swap.
 */
@Composable
private fun Modifier.aurora(p: Palette): Modifier {
    val spec = Motion.standard<Color>(Motion.Long * 2)
    val base by animateColorAsState(p.base, spec, label = "base")
    val a by animateColorAsState(p.a, spec, label = "a")
    val b by animateColorAsState(p.b, spec, label = "b")
    val c by animateColorAsState(p.c, spec, label = "c")
    val phase by rememberFrameClock(24_000)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sheen = if (dark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.55f)
    val edge = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.9f)
    return drawBehind {
        val t = phase * TWO_PI
        val w = size.width
        val h = size.height
        drawRect(base)
        val breath = 1f + 0.05f * sin(t * 3f)
        blob(a, Offset(w * (0.18f + 0.10f * sin(t)), h * (0.35f + 0.20f * cos(t * 0.8f))), w * 0.55f * breath)
        blob(b, Offset(w * (0.86f + 0.08f * cos(t * 1.3f)), h * (0.15f + 0.25f * sin(t + 1f))), w * 0.50f)
        blob(c, Offset(w * (0.58f + 0.16f * sin(t * 0.6f + 2f)), h * (1.05f + 0.12f * cos(t * 1.1f))), w * 0.62f)
        drawRect(Brush.verticalGradient(0f to sheen, 0.45f to Color.Transparent), size = Size(w, h))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, edge, Color.Transparent)), size = Size(w, 1.dp.toPx()))
    }
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
}

/** One clock at one second, alive only while something is timed; readers subscribe in draw or in a leaf Text. */
@Composable
private fun rememberTicker(active: Boolean): State<Long> {
    val tick = remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(active) {
        while (active) {
            tick.longValue = nowMillis()
            delay(1000 - tick.longValue % 1000)
        }
    }
    return tick
}

private fun countdown(remaining: Long): String {
    val total = (remaining.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    fun Long.pad() = toString().padStart(2, '0')
    return if (h > 0) "$h:${m.pad()}:${sec.pad()}" else "${m.pad()}:${sec.pad()}"
}

/** The state word, one line, shrinking before it wraps: "Выключена" at 1.3× still fits beside the switch. */
@Composable
private fun TitleText(text: String, color: Color, fill: Boolean = true) {
    val style = MaterialTheme.typography.headlineLarge
    BasicText(
        text,
        modifier = if (fill) Modifier.fillMaxWidth() else Modifier,
        style = style.copy(color = color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}

@Composable
private fun CountdownText(until: Long, tick: State<Long>, color: Color) {
    val style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    Text(countdown(until - tick.value), style = style, color = color, maxLines = 1)
}

/**
 * 64 dp glyph: a soft glow in the accent, a ring that empties while something is timed, the
 * icon melting from one state to the next — a lit lamp, not a slide.
 */
@Composable
private fun StateGlyph(icon: ImageVector, emoji: String, p: Palette, until: Long?, total: Long?, tick: State<Long>, iconModifier: Modifier = Modifier) {
    val accent by animateColorAsState(p.accent, Motion.standard(Motion.Long), label = "accent")
    val ring by animateFloatAsState(if (until != null && total != null && total > 0) 1f else 0f, Motion.standard(Motion.Long), label = "ring")
    val pulse by rememberFrameClock(5_200)
    val trackAlpha = if (p.content.luminance() > 0.5f) 0.16f else 0.12f
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(64.dp).drawBehind {
                val scale = 0.9f + 0.1f * (0.5f + 0.5f * sin(pulse * TWO_PI))
                val r = size.minDimension / 2 * scale
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0f)), center, r), r, center)
                if (ring > 0f) {
                    val stroke = 4.dp.toPx()
                    val inset = stroke / 2 + 2.dp.toPx()
                    val fraction = if (until != null && total != null && total > 0) ((until - tick.value).toFloat() / total).coerceIn(0f, 1f) else 0f
                    val rect = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    drawArc(p.content.copy(alpha = trackAlpha * ring), -90f, 360f, false, topLeft, rect, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(accent.copy(alpha = ring), -90f, 360f * fraction, false, topLeft, rect, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            },
        )
        AnimatedContent(
            // Keyed on the emoji as well: two routines share one fallback icon, and the glyph
            // must still melt from one face to the next when the one in force changes.
            targetState = icon to emoji,
            transitionSpec = {
                (fadeIn(Motion.enter()) + scaleIn(tween(Motion.Long, easing = EaseOutBack), initialScale = 0.4f)) togetherWith
                    (fadeOut(Motion.exit()) + scaleOut(Motion.exit(), targetScale = 0.6f))
            },
            label = "icon",
        ) { (ic, face) ->
            if (face.isNotEmpty()) {
                Box(iconModifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Text(face, fontSize = 22.sp, maxLines = 1)
                }
            } else {
                Icon(ic, null, tint = accent, modifier = iconModifier.size(28.dp))
            }
        }
    }
}

/** A small tag on the surface: "Strict", "2 locked". */
@Composable
private fun Tag(icon: ImageVector, text: String, p: Palette) {
    Row(
        Modifier.background(p.accent.copy(alpha = 0.16f), MaterialTheme.shapes.extraSmall).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = p.accent, modifier = Modifier.size(13.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = p.content)
    }
}

/** A tonal chip cut from the aurora: the content colour at low alpha, a hairline, a press that sinks. */
@Composable
private fun AuroraChip(text: String, p: Palette, modifier: Modifier = Modifier, icon: ImageVector? = null, emoji: String = "", onClick: () -> Unit) {
    val h = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.95f else 1f, Motion.standard(Motion.Short), label = "sink")
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .graphicsLayer { scaleX = sink; scaleY = sink }
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(p.content.copy(alpha = if (pressed) 0.20f else 0.11f))
            .border(1.dp, p.content.copy(alpha = 0.14f), shape)
            .clickable(interactionSource = interaction, indication = null) { h.select(); onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (emoji.isNotEmpty()) Text(emoji, fontSize = 15.sp, maxLines = 1)
        else if (icon != null) Icon(icon, null, tint = p.content, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = p.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One chip in an action strip: a word, sometimes a face in front of it, and what it does. */
private class ChipAction(val label: String, val emoji: String = "", val onClick: () -> Unit)

/**
 * "[icon] Break ⓘ  [5 min] [15 min] [1 hour] [Until tomorrow]" — one labelled row of chips.
 * Tapping the label opens a rich tooltip that says what the row does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionStrip(icon: ImageVector, label: String, explanation: String, p: Palette, actions: List<ChipAction>) {
    val tooltip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
            tooltip = { RichTooltip(title = { Text(label) }) { Text(explanation) } },
            state = tooltip,
        ) {
            Row(
                Modifier.heightIn(min = 28.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { scope.launch { tooltip.show() } },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, null, tint = p.muted, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = p.muted)
                Icon(Icons.Outlined.Info, null, tint = p.muted.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { action -> AuroraChip(action.label, p, emoji = action.emoji, onClick = action.onClick) }
        }
    }
}
