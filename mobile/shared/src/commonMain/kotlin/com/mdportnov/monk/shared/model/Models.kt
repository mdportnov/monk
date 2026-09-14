package com.mdportnov.monk.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class BlockMode {
    /** The app never opens while protection is on. */
    BLOCK,

    /** A breathing pause first; the app opens only after the countdown and an explicit tap. */
    DELAY,
}

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Why the user opened the app anyway. Stored by name in stats. */
@Serializable
enum class Intention { REPLY, LOOKUP, BORED, HABIT }

/** What a time rule does to the app while its window is open. */
@Serializable
enum class RuleMode { BLOCK, PAUSE, FREE }

/**
 * A weekly time window on one app. [endMinute] <= [startMinute] means the window runs into the
 * next day; the weekday is checked at the window's start, so a Friday 22:00–02:00 rule covers
 * Saturday 01:00. Overlapping rules resolve to the strictest: BLOCK > PAUSE > FREE.
 */
@Serializable
data class TimeRule(
    val id: Long,
    val mode: RuleMode = RuleMode.BLOCK,
    /** ISO day numbers, Monday = 1 … Sunday = 7. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val startMinute: Int,
    val endMinute: Int,
) {
    val crossesMidnight get() = endMinute <= startMinute
    fun isActive(dayIso: Int, minuteOfDay: Int) = TimeWindow.isActive(days, startMinute, endMinute, dayIso, minuteOfDay)
}

@Serializable
data class BlockedApp(
    val packageName: String,
    val label: String,
    val mode: BlockMode = BlockMode.DELAY,
    /** null = use [MonkConfig.defaultDelaySeconds]. */
    val delaySeconds: Int? = null,
    /** null = use [MonkConfig.defaultAllowMinutes]. */
    val allowMinutes: Int? = null,
    /** Opens per day after which Pause turns into Block until midnight. null = unlimited. */
    val dailyLimit: Int? = null,
    /** Time rules that override [mode] while their window is open. */
    val rules: List<TimeRule> = emptyList(),
    /** Set when the OS removed the package while it was on the list; cleared when it comes back. */
    val uninstalledAt: Long? = null,
    /** Nothing about this app can be softened; the only way out is removing the app. */
    val locked: Boolean = false,
) {
    /** The strictest rule open right now, or null. */
    fun activeRule(dayIso: Int, minuteOfDay: Int): TimeRule? =
        rules.filter { it.isActive(dayIso, minuteOfDay) }.minByOrNull { it.mode.ordinal }
}

/** Weekly window arithmetic shared by the global schedule and per-app rules. */
object TimeWindow {
    const val DAY = 24 * 60

    fun isActive(days: Set<Int>, start: Int, end: Int, dayIso: Int, minuteOfDay: Int): Boolean {
        if (start == end) return dayIso in days
        return if (start < end) {
            dayIso in days && minuteOfDay in start until end
        } else {
            val startedYesterday = minuteOfDay < end
            val day = if (startedYesterday) previousDay(dayIso) else dayIso
            day in days && (minuteOfDay >= start || minuteOfDay < end)
        }
    }

    /**
     * Minutes from now until the window next opens or closes, looking up to a week ahead; null
     * if it never changes (every day, all day). Used to re-judge the foreground app on time.
     */
    fun minutesToNextChange(days: Set<Int>, start: Int, end: Int, dayIso: Int, minuteOfDay: Int): Int? {
        if (start == end && days.size == 7) return null
        val now = isActive(days, start, end, dayIso, minuteOfDay)
        var day = dayIso
        var minute = minuteOfDay
        var elapsed = 0
        // Walk minute by minute would be silly; walk boundary by boundary instead.
        repeat(7 * 4) {
            val candidates = listOf(start, end, 0).map { b -> if (b > minute) b - minute else DAY - minute + b }.filter { it > 0 }
            val step = candidates.min()
            elapsed += step
            minute = (minute + step) % DAY
            if (minute == 0 && step > 0 && (minuteOfDay + elapsed) % DAY == 0) day = nextDay(day)
            if (isActive(days, start, end, day, minute) != now) return elapsed
        }
        return null
    }

    fun previousDay(dayIso: Int) = if (dayIso == 1) 7 else dayIso - 1
    fun nextDay(dayIso: Int) = if (dayIso == 7) 1 else dayIso + 1
}

@Serializable
data class Schedule(
    val enabled: Boolean = false,
    /** ISO day numbers, Monday = 1 … Sunday = 7. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 18 * 60,
) {
    /** Handles windows that cross midnight (22:00 → 07:00). Day is checked at the window start. */
    fun isActive(dayIso: Int, minuteOfDay: Int): Boolean =
        !enabled || TimeWindow.isActive(days, startMinute, endMinute, dayIso, minuteOfDay)
}

@Serializable
data class MonkConfig(
    /** Bumped only for non-additive changes; additive fields with defaults need no bump. */
    val schemaVersion: Int = CURRENT_SCHEMA,
    val enabled: Boolean = true,
    val defaultDelaySeconds: Int = 10,
    val defaultAllowMinutes: Int = 5,
    val apps: List<BlockedApp> = emptyList(),
    /**
     * Settings of apps removed from the list (or uninstalled), by package id. Re-adding the same
     * package restores them; nothing the user tuned is ever thrown away silently.
     */
    val archivedApps: List<BlockedApp> = emptyList(),
    val schedule: Schedule = Schedule(),
    /** Epoch millis; protection is off until then. 0 = not paused. */
    val pausedUntil: Long = 0,
    /** Epoch millis; until then nothing that weakens protection can be changed. 0 = off. */
    val strictUntil: Long = 0,
    /** Epoch millis; until then every watched app is blocked outright. One-way. 0 = off. */
    val focusUntil: Long = 0,
    val focusStartedAt: Long = 0,
    val pauseStartedAt: Long = 0,
    /** Epoch millis when a break was cut short by hand; a break that ran out ends at [pausedUntil]. */
    val lastBreakEndedAt: Long = 0,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** "system", "en" or "ru". */
    val language: String = "system",
    /** The "How Monk works" card on Home was dismissed. */
    val helpDismissed: Boolean = false,
    /** Material You palette from the wallpaper (Android 12+). Ignored where unsupported. */
    val dynamicColor: Boolean = false,
    /** Ask "why?" on the pause screen before opening; the answer lands in stats. */
    val askIntention: Boolean = true,
    /** Your own line on the pause screen. Empty = the built-in one. */
    val pauseMessage: String = "",
    /**
     * Android: draw the pause screen as an accessibility overlay instead of an Activity. Covers
     * split-screen and ROMs that drop background activity starts; the Activity is the default
     * because it gets predictive back and a real task.
     */
    val overlayMode: Boolean = false,
    /** Android: notify when the accessibility service stops while apps are watched. */
    val notifyWhenOff: Boolean = true,
    /** The Home card asking for notification permission (Android 13+) was dismissed. */
    val notifyPromptDismissed: Boolean = false,
    /** Light haptic ticks on sliders, toggles and choices. */
    val haptics: Boolean = true,
    /** Android: an ongoing notification with the focus / break countdown (Live Update on 16+). */
    val liveStatus: Boolean = false,
) {
    fun app(packageName: String): BlockedApp? = apps.firstOrNull { it.packageName == packageName }
    fun archived(packageName: String): BlockedApp? = archivedApps.firstOrNull { it.packageName == packageName }
    fun delayFor(app: BlockedApp) = app.delaySeconds ?: defaultDelaySeconds
    fun allowFor(app: BlockedApp) = app.allowMinutes ?: defaultAllowMinutes
    fun isPaused(now: Long) = pausedUntil > now
    fun isStrict(now: Long) = strictUntil > now
    fun isFocus(now: Long) = focusUntil > now

    /** When the last break ended, by the clock or by hand; 0 if there was none. */
    fun breakEndedAt(now: Long): Long = maxOf(lastBreakEndedAt, if (pausedUntil in 1..now) pausedUntil else 0L)

    /** Epoch millis from which a new break may start; the cooldown is measured from the last one's end. */
    fun nextBreakAt(now: Long): Long = breakEndedAt(now).let { if (it == 0L) 0L else it + BREAK_COOLDOWN_MS }

    /**
     * A break is a softening, so strict mode and a focus session forbid it; one at a time; and
     * the next one waits out the cooldown so breaks cannot be chained into a permanent "off".
     */
    fun canStartBreak(now: Long) = enabled && !isStrict(now) && !isFocus(now) && !isPaused(now) && now >= nextBreakAt(now)

    /**
     * Every running timer moved by [deltaMillis]: commitments are measured in elapsed time, so
     * when the wall clock jumps the deadlines jump with it and nothing ends early or late.
     */
    fun shifted(deltaMillis: Long): MonkConfig {
        fun Long.moved() = if (this == 0L) 0L else this + deltaMillis
        return copy(
            pausedUntil = pausedUntil.moved(),
            strictUntil = strictUntil.moved(),
            focusUntil = focusUntil.moved(),
            focusStartedAt = focusStartedAt.moved(),
            pauseStartedAt = pauseStartedAt.moved(),
            lastBreakEndedAt = lastBreakEndedAt.moved(),
        )
    }

    companion object {
        const val CURRENT_SCHEMA = 1
        const val BREAK_COOLDOWN_MS = 30 * 60_000L
    }
}

@Serializable
data class AppDayStats(
    val intercepted: Int = 0,
    val turnedAway: Int = 0,
    val opened: Int = 0,
    val reasons: Map<String, Int> = emptyMap(),
) {
    operator fun plus(o: AppDayStats) = AppDayStats(
        intercepted + o.intercepted,
        turnedAway + o.turnedAway,
        opened + o.opened,
        (reasons.keys + o.reasons.keys).associateWith { (reasons[it] ?: 0) + (o.reasons[it] ?: 0) },
    )
}

@Serializable
data class DayStats(
    val date: String,
    val intercepted: Int = 0,
    val turnedAway: Int = 0,
    val opened: Int = 0,
    val byApp: Map<String, AppDayStats> = emptyMap(),
    /** Intercepts by hour of day (0–23). */
    val byHour: Map<Int, Int> = emptyMap(),
)

@Serializable
data class Stats(
    val days: List<DayStats> = emptyList(),
) {
    val totalIntercepted get() = days.sumOf { it.intercepted }
    val totalTurnedAway get() = days.sumOf { it.turnedAway }
    val totalOpened get() = days.sumOf { it.opened }
    fun day(date: String) = days.firstOrNull { it.date == date } ?: DayStats(date)

    fun opensToday(date: String, packageName: String) = day(date).byApp[packageName]?.opened ?: 0

    /** Per-app totals over the given dates, most intercepted first. */
    fun perApp(dates: Collection<String>): List<Pair<String, AppDayStats>> {
        val set = dates.toSet()
        val acc = HashMap<String, AppDayStats>()
        days.filter { it.date in set }.forEach { d ->
            d.byApp.forEach { (pkg, s) -> acc[pkg] = (acc[pkg] ?: AppDayStats()) + s }
        }
        return acc.entries.sortedByDescending { it.value.intercepted }.map { it.key to it.value }
    }

    fun byHour(dates: Collection<String>): List<Int> {
        val set = dates.toSet()
        val hours = IntArray(24)
        days.filter { it.date in set }.forEach { d -> d.byHour.forEach { (h, n) -> if (h in 0..23) hours[h] += n } }
        return hours.toList()
    }

    fun reasons(dates: Collection<String>): Map<String, Int> =
        perApp(dates).fold(AppDayStats()) { a, (_, s) -> a + s }.reasons

    /** Consecutive days, ending at [today] or the day before, on which the user walked away at least once. */
    fun walkAwayStreak(orderedDatesEndingToday: List<String>): Int {
        var streak = 0
        val reversed = orderedDatesEndingToday.asReversed()
        for ((i, date) in reversed.withIndex()) {
            val d = day(date)
            if (d.turnedAway > 0) streak++
            else if (i == 0) continue // today may still be empty
            else break
        }
        return streak
    }
}

data class InstalledApp(val packageName: String, val label: String)

sealed interface Decision {
    data object Allow : Decision
    data class Intercept(
        val app: BlockedApp,
        val limitReached: Boolean,
        val focus: Boolean = false,
        /** The rule that produced this decision, if any. */
        val rule: TimeRule? = null,
    ) : Decision {
        val effectiveMode
            get() = when {
                limitReached || focus -> BlockMode.BLOCK
                rule != null -> if (rule.mode == RuleMode.BLOCK) BlockMode.BLOCK else BlockMode.DELAY
                else -> app.mode
            }
    }
}

object BlockPolicy {
    fun decide(
        config: MonkConfig,
        packageName: String,
        nowMillis: Long,
        dayIso: Int,
        minuteOfDay: Int,
        allowances: Map<String, Long>,
        opensToday: Int = 0,
    ): Decision {
        val app = config.app(packageName) ?: return Decision.Allow
        // A locked app is the user's promise to themselves: neither the master switch nor a
        // break can loosen it. Only removing the app can, and that asks twice. Strict mode is
        // the same promise for the whole list, so a stray "off" or break in the config is void.
        if (!app.locked && !config.isStrict(nowMillis)) {
            if (!config.enabled) return Decision.Allow
            if (config.isPaused(nowMillis)) return Decision.Allow
        }
        if (config.isFocus(nowMillis)) return Decision.Intercept(app, limitReached = false, focus = true)
        if (!config.schedule.isActive(dayIso, minuteOfDay)) return Decision.Allow
        val rule = app.activeRule(dayIso, minuteOfDay)
        // A block window wins over an allowance handed out before it opened.
        if (rule?.mode == RuleMode.BLOCK) return Decision.Intercept(app, limitReached = false, rule = rule)
        val until = allowances[packageName] ?: 0L
        if (until > nowMillis) return Decision.Allow
        val limitReached = app.dailyLimit?.let { opensToday >= it } ?: false
        if (limitReached) return Decision.Intercept(app, limitReached = true, rule = rule)
        if (rule?.mode == RuleMode.FREE) return Decision.Allow
        return Decision.Intercept(app, limitReached = false, rule = rule)
    }

    /**
     * Millis until the app's verdict can change on its own: the nearest rule boundary or
     * the global schedule's; null when nothing is time-bound. The service re-judges the
     * foreground app then, so a block window starting mid-session actually starts.
     */
    /** Wall-clock minutes until the nearest rule / schedule boundary, or null. */
    fun minutesToNextChange(config: MonkConfig, app: BlockedApp, dayIso: Int, minuteOfDay: Int): Int? = buildList {
        app.rules.forEach { r -> TimeWindow.minutesToNextChange(r.days, r.startMinute, r.endMinute, dayIso, minuteOfDay)?.let { add(it) } }
        if (config.schedule.enabled) {
            TimeWindow.minutesToNextChange(config.schedule.days, config.schedule.startMinute, config.schedule.endMinute, dayIso, minuteOfDay)?.let { add(it) }
        }
    }.minOrNull()

    fun millisToNextChange(
        config: MonkConfig,
        app: BlockedApp,
        dayIso: Int,
        minuteOfDay: Int,
        secondOfMinute: Int,
        nowMillis: Long = 0L,
    ): Long? {
        val minutes = minutesToNextChange(config, app, dayIso, minuteOfDay)?.let { it * 60_000L - secondOfMinute * 1000L }
        // A break ending is a verdict change too: protection comes back on while the app is open.
        val breakEnd = if (nowMillis > 0 && config.isPaused(nowMillis)) config.pausedUntil - nowMillis else null
        return listOfNotNull(minutes, breakEnd).minOrNull()
    }

    /**
     * Minutes until the app's BLOCK coverage ends (the strictest active rule stops being BLOCK),
     * walking minute by minute up to a week; null if no BLOCK rule is active now.
     */
    fun blockEndsInMinutes(app: BlockedApp, dayIso: Int, minuteOfDay: Int): Int? {
        if (app.activeRule(dayIso, minuteOfDay)?.mode != RuleMode.BLOCK) return null
        var day = dayIso
        var minute = minuteOfDay
        for (elapsed in 1..7 * TimeWindow.DAY) {
            minute++
            if (minute == TimeWindow.DAY) { minute = 0; day = TimeWindow.nextDay(day) }
            if (app.activeRule(day, minute)?.mode != RuleMode.BLOCK) return elapsed
        }
        return null
    }
}
