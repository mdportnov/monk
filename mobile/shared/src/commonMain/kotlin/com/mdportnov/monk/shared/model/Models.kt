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
)

@Serializable
data class Schedule(
    val enabled: Boolean = false,
    /** ISO day numbers, Monday = 1 … Sunday = 7. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 18 * 60,
) {
    /** Handles windows that cross midnight (22:00 → 07:00). Day is checked at the window start. */
    fun isActive(dayIso: Int, minuteOfDay: Int): Boolean {
        if (!enabled) return true
        if (startMinute == endMinute) return dayIso in days
        return if (startMinute < endMinute) {
            dayIso in days && minuteOfDay in startMinute until endMinute
        } else {
            val startedYesterday = minuteOfDay < endMinute
            val day = if (startedYesterday) previousDay(dayIso) else dayIso
            day in days && (minuteOfDay >= startMinute || minuteOfDay < endMinute)
        }
    }

    private fun previousDay(dayIso: Int) = if (dayIso == 1) 7 else dayIso - 1
}

@Serializable
data class MonkConfig(
    /** Bumped only for non-additive changes; additive fields with defaults need no bump. */
    val schemaVersion: Int = CURRENT_SCHEMA,
    val enabled: Boolean = true,
    val defaultDelaySeconds: Int = 10,
    val defaultAllowMinutes: Int = 5,
    val apps: List<BlockedApp> = emptyList(),
    val schedule: Schedule = Schedule(),
    /** Epoch millis; protection is off until then. 0 = not paused. */
    val pausedUntil: Long = 0,
    /** Epoch millis; until then nothing that weakens protection can be changed. 0 = off. */
    val strictUntil: Long = 0,
    /** Epoch millis; until then every watched app is blocked outright. One-way. 0 = off. */
    val focusUntil: Long = 0,
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
) {
    fun app(packageName: String): BlockedApp? = apps.firstOrNull { it.packageName == packageName }
    fun delayFor(app: BlockedApp) = app.delaySeconds ?: defaultDelaySeconds
    fun allowFor(app: BlockedApp) = app.allowMinutes ?: defaultAllowMinutes
    fun isPaused(now: Long) = pausedUntil > now
    fun isStrict(now: Long) = strictUntil > now
    fun isFocus(now: Long) = focusUntil > now

    companion object {
        const val CURRENT_SCHEMA = 1
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
    data class Intercept(val app: BlockedApp, val limitReached: Boolean, val focus: Boolean = false) : Decision {
        val effectiveMode get() = if (limitReached || focus) BlockMode.BLOCK else app.mode
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
        if (!config.enabled) return Decision.Allow
        if (config.isPaused(nowMillis)) return Decision.Allow
        val app = config.app(packageName) ?: return Decision.Allow
        if (config.isFocus(nowMillis)) return Decision.Intercept(app, limitReached = false, focus = true)
        if (!config.schedule.isActive(dayIso, minuteOfDay)) return Decision.Allow
        val until = allowances[packageName] ?: 0L
        if (until > nowMillis) return Decision.Allow
        val limitReached = app.dailyLimit?.let { opensToday >= it } ?: false
        return Decision.Intercept(app, limitReached)
    }
}
