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

    /** A pause screen can show for this app at some hour: the delay and the allowance mean something. */
    val hasPauseScreen get() = mode == BlockMode.DELAY || rules.any { it.mode == RuleMode.PAUSE }

    /** The app can be opened at some hour, so a daily limit can be reached. */
    val canOpen get() = mode == BlockMode.DELAY || rules.any { it.mode != RuleMode.BLOCK }

    /** The limit is set and there is an hour at which it counts. */
    val limitApplies get() = dailyLimit != null && canOpen
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

/** Does this weekly window ever overlap [schedule]? A rule that never does can never fire. */
fun TimeRule.everAppliesUnder(schedule: Schedule): Boolean {
    if (!schedule.enabled) return true
    for (day in 1..7) {
        for (minute in 0 until TimeWindow.DAY) {
            if (isActive(day, minute) && schedule.isActive(day, minute)) return true
        }
    }
    return false
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

    /** Wall-clock minutes until the schedule flips next, or null when it never does. */
    fun minutesToNextChange(dayIso: Int, minuteOfDay: Int): Int? =
        if (!enabled) null else TimeWindow.minutesToNextChange(days, startMinute, endMinute, dayIso, minuteOfDay)

    /** Every minute of the week this schedule lets the app layer work. Disabled means all of them. */
    fun weekMask(): BooleanArray {
        val mask = BooleanArray(7 * TimeWindow.DAY)
        for (day in 1..7) {
            val base = (day - 1) * TimeWindow.DAY
            for (minute in 0 until TimeWindow.DAY) mask[base + minute] = isActive(day, minute)
        }
        return mask
    }

    /**
     * True when this version protects at least as many minutes as [other]. Switching the base
     * hours off makes them every minute of the week, so it is a tightening — which is why strict
     * mode must allow it, though it looks like turning something off.
     */
    fun isAtLeastAsStrictAs(other: Schedule): Boolean {
        if (!enabled) return true
        if (!other.enabled) return false
        val mine = weekMask()
        val theirs = other.weekMask()
        return theirs.indices.none { theirs[it] && !mine[it] }
    }
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
    /**
     * Named hours with a verdict of their own, layered over [apps]: the built-ins plus whatever
     * the user made. Seeded and repaired by [normalized]; empty only before the first one runs.
     */
    val routines: List<Routine> = emptyList(),
    /** The routine started by hand, if any. One at a time, and it cannot be cut short. */
    val run: RoutineRun? = null,
    /** Epoch millis; protection is off until then. 0 = not paused. */
    val pausedUntil: Long = 0,
    /** Epoch millis; until then nothing that weakens protection can be changed. 0 = off. */
    val strictUntil: Long = 0,
    /**
     * Focus as it was before routines: a bare pair of timestamps. Nothing writes these any more —
     * [normalized] folds a session still running into a [run] of the built-in focus routine — but
     * they stay in the schema so a config written by an older build still decodes and its session
     * is not silently dropped.
     */
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

    fun routine(id: String): Routine? = routines.firstOrNull { it.id == id }

    /**
     * The hand-started run that is still going, or null. A run whose routine has been deleted, or
     * whose routine covers no app at all, counts as over: it is the one gate the master switch,
     * breaks and the policy all consult, so a session that does nothing must not be able to hold
     * the switch down.
     */
    fun activeRun(now: Long): RoutineRun? =
        run?.takeIf { it.until > now && routine(it.routineId)?.coversNothing == false }

    fun runningRoutine(now: Long): Routine? = activeRun(now)?.let { routine(it.routineId) }

    /**
     * Every routine in force this minute: the one running by hand first, then the ones their own
     * windows have opened. Scope is not applied here — this is what the status surfaces show.
     */
    fun openRoutines(now: Long, dayIso: Int, minuteOfDay: Int): List<Routine> {
        val running = runningRoutine(now)
        val open = routines.filter { it.isOpen(dayIso, minuteOfDay) }
        return if (running == null) open else listOf(running) + open.filter { it.id != running.id }
    }

    /** [openRoutines] narrowed to the ones that cover [packageName]. */
    fun activeRoutines(packageName: String, now: Long, dayIso: Int, minuteOfDay: Int): List<Routine> =
        openRoutines(now, dayIso, minuteOfDay).filter { it.covers(packageName) }

    /**
     * Whether this routine is actually deciding anything this minute. A session holds through
     * everything; a scheduled routine is an ordinary layer that the master switch turns off and a
     * break lifts unless it says otherwise. [sealed] is the caller's "nothing here may be
     * softened" — strict mode, or a locked app.
     *
     * Every screen that says a routine is in force must ask this, and so must the policy. They
     * used to answer it separately, and the screens said a routine was holding while the policy
     * was letting the app through — the one direction of error that costs the user something.
     */
    fun routineHolds(routine: Routine, now: Long, sealed: Boolean = false): Boolean = when {
        activeRun(now)?.routineId == routine.id -> true
        sealed || isStrict(now) -> true
        !enabled -> false
        isPaused(now) -> routine.ignoresBreaks
        else -> true
    }

    /** The routines that are both open and in force: what the screens may truthfully name. */
    fun routinesInForce(now: Long, dayIso: Int, minuteOfDay: Int): List<Routine> =
        openRoutines(now, dayIso, minuteOfDay).filter { !it.coversNothing && routineHolds(it, now) }

    /** The one a surface names when the state is [ProtectionState.ROUTINE]: the run, else the strictest. */
    fun leadingRoutine(now: Long, dayIso: Int, minuteOfDay: Int): Routine? =
        runningRoutine(now) ?: routinesInForce(now, dayIso, minuteOfDay).minByOrNull { it.mode.ordinal }

    /** When the last break ended, by the clock or by hand; 0 if there was none. */
    fun breakEndedAt(now: Long): Long = maxOf(lastBreakEndedAt, if (pausedUntil in 1..now) pausedUntil else 0L)

    /** Epoch millis from which a new break may start; the cooldown is measured from the last one's end. */
    fun nextBreakAt(now: Long): Long = breakEndedAt(now).let { if (it == 0L) 0L else it + BREAK_COOLDOWN_MS }

    /**
     * A break is a softening, so strict mode and a running routine forbid it; one at a time; and
     * the next one waits out the cooldown so breaks cannot be chained into a permanent "off".
     */
    fun canStartBreak(now: Long) =
        enabled && !isStrict(now) && activeRun(now) == null && !isPaused(now) && now >= nextBreakAt(now)

    /** Routines a break would not lift: the card says so before the breath, not after. */
    fun routinesThroughBreak(now: Long, dayIso: Int, minuteOfDay: Int): List<Routine> =
        openRoutines(now, dayIso, minuteOfDay).filter { it.ignoresBreaks && !it.coversNothing }

    /** [canStartBreak], and there is something to soften: outside the schedule a break is void. */
    fun canStartBreak(now: Long, dayIso: Int, minuteOfDay: Int) = canStartBreak(now) && schedule.isActive(dayIso, minuteOfDay)

    /**
     * The one state every surface shows, in order of what actually decides the verdict. A routine
     * started by hand is a promise with an end time, so it outranks even the master switch. Below
     * it the master switch, then the schedule — and a routine open on its own hours keeps the
     * headline there, because outside protection hours it is the only thing still working. A
     * break lifts the ordinary layers but not a routine that says it holds through one. Strict
     * mode never decides what happens to an app, only what may be softened, so it comes last.
     */
    fun state(now: Long, dayIso: Int, minuteOfDay: Int): ProtectionState {
        if (activeRun(now) != null) return ProtectionState.ROUTINE
        if (!enabled) return ProtectionState.OFF
        // In force, not merely open: a break outside the base hours used to leave this saying
        // ROUTINE while the policy was letting every app through.
        val held = routinesInForce(now, dayIso, minuteOfDay)
        return when {
            !schedule.isActive(dayIso, minuteOfDay) -> if (held.isNotEmpty()) ProtectionState.ROUTINE else ProtectionState.SCHEDULED_OFF
            isPaused(now) -> if (held.isNotEmpty()) ProtectionState.ROUTINE else ProtectionState.BREAK
            held.isNotEmpty() -> ProtectionState.ROUTINE
            isStrict(now) -> ProtectionState.STRICT
            else -> ProtectionState.ON
        }
    }

    /**
     * Built-ins present and in order, hand-written nonsense repaired, and a focus session left
     * over from a build before routines folded into a run of the built-in focus routine. Run on
     * load and after every decode, so the rest of the code can assume all of it.
     */
    fun normalized(now: Long): MonkConfig {
        val stored = routines.filter { it.id.isNotBlank() }.distinctBy { it.id }
        val known = stored.associateBy { it.id }
        // A built-in this build ships but the config has never seen (new install, or a version
        // that added one) comes in at its factory settings; the rest keep whatever the user did.
        val builtIns = BuiltInRoutines.all.map { factory -> known[factory.id]?.copy(builtIn = true) ?: factory }
        val custom = stored.filter { it.id !in BuiltInRoutines.ids }.map { it.copy(builtIn = false) }
        val all = (builtIns + custom).take(Routine.MAX_ROUTINES).map { it.repaired() }
        val legacyFocus = focusUntil.takeIf { it > now }?.let {
            RoutineRun(BuiltInRoutines.FOCUS, focusStartedAt.takeIf { s -> s in 1..it } ?: now, it)
        }
        val nextRun = (run ?: legacyFocus)?.takeIf { r -> r.until > now && all.any { it.id == r.routineId } }
        return copy(
            schemaVersion = CURRENT_SCHEMA,
            routines = all,
            run = nextRun,
            focusUntil = 0,
            focusStartedAt = 0,
        )
    }

    /** The break is over from now on, by hand: the cooldown counts from this moment. */
    fun endingBreak(now: Long): MonkConfig = if (isPaused(now)) copy(pausedUntil = 0, lastBreakEndedAt = now) else this

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
            run = run?.copy(startedAt = run.startedAt.moved(), until = run.until.moved()),
        )
    }

    companion object {
        /** 2 added routines and folded the focus session into one of them. */
        const val CURRENT_SCHEMA = 2
        const val BREAK_COOLDOWN_MS = 30 * 60_000L
    }
}

enum class ProtectionState {
    OFF,

    /** A routine is in force: started by hand, or open on its own hours. */
    ROUTINE,
    SCHEDULED_OFF,
    BREAK,
    STRICT,
    ON,
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
    /** Last known label per package, so an app forgotten from the list still has a name in its rows. */
    val labels: Map<String, String> = emptyMap(),
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

    /**
     * [verdict] is what the layers came to together — the app's own mode and rules merged with
     * every routine in force — so no surface has to redo that arithmetic. [rule] and [routine]
     * are only there to explain the verdict on the pause screen, and only whichever of them
     * actually made it stricter.
     */
    data class Intercept(
        val app: BlockedApp,
        val limitReached: Boolean,
        val verdict: RuleMode = RuleMode.PAUSE,
        /** The rule that produced this decision, if any. */
        val rule: TimeRule? = null,
        /** The routine that made this stricter than the app's own setting, if one did. */
        val routine: Routine? = null,
    ) : Decision {
        val effectiveMode get() = if (limitReached || verdict == RuleMode.BLOCK) BlockMode.BLOCK else BlockMode.DELAY
    }
}

/**
 * What the two layers come to for one app at one instant, before allowances and daily limits.
 * [routine] is named only when the routine layer is what tightened the answer.
 */
class Layers(
    val verdict: RuleMode?,
    val rule: TimeRule?,
    val routineMode: RuleMode?,
    val routine: Routine?,
)

object BlockPolicy {
    /** Strictest wins, as everywhere else: BLOCK over PAUSE over FREE. null = this layer is silent. */
    fun strictest(a: RuleMode?, b: RuleMode?): RuleMode? = when {
        a == null -> b
        b == null -> a
        else -> if (a.ordinal <= b.ordinal) a else b
    }

    /**
     * What would happen to this app if it were opened right now — everything the gate weighs
     * except the allowance it may already hold and its daily limit. A null verdict means nothing
     * would happen: the switch is off, a break is running, the base hours are closed, or no layer
     * covers it.
     *
     * [decide] adds allowances and daily limits on top, and every screen that shows what an app
     * is about to do reads this same function, so a row cannot promise what the gate would not do.
     */
    fun layersAt(config: MonkConfig, app: BlockedApp, nowMillis: Long, dayIso: Int, minuteOfDay: Int): Layers {
        val sealed = app.locked || config.isStrict(nowMillis)
        val off = !config.enabled
        val onBreak = config.isPaused(nowMillis)
        val routines = config.activeRoutines(app.packageName, nowMillis, dayIso, minuteOfDay)
            .filter { config.routineHolds(it, nowMillis, sealed) }
        // Nothing holds and nothing seals: the master switch or the break has the whole app list.
        if (routines.isEmpty() && !sealed && (off || onBreak)) return Layers(null, null, null, null)

        // The app's own layer. Outside the base hours it says nothing at all, which is what makes
        // a routine the only way to be protected out there.
        val appLive = (sealed || (!off && !onBreak)) && config.schedule.isActive(dayIso, minuteOfDay)
        val rule = if (appLive) app.activeRule(dayIso, minuteOfDay) else null
        val appMode: RuleMode? = when {
            !appLive -> null
            rule != null -> rule.mode
            app.mode == BlockMode.BLOCK -> RuleMode.BLOCK
            else -> RuleMode.PAUSE
        }
        val routineMode = routines.minByOrNull { it.mode.ordinal }?.ruleMode
        // Named only when it is the routine that tightened things: a routine that merely agrees
        // with the app's own setting has nothing to explain, and saying its name would puzzle.
        val blame = routines
            .firstOrNull { it.ruleMode == routineMode }
            ?.takeIf { appMode == null || routineMode!!.ordinal < appMode.ordinal }
        return Layers(strictest(appMode, routineMode), rule, routineMode, blame)
    }

    /**
     * Two layers decide an app, and the stricter one wins.
     *
     * The app's own layer is its mode and its rules, inside protection hours. The routine layer
     * is every routine in force that covers it. A routine can only tighten: it turns a pause into
     * a block, or gives a free hour a pause back, and it never opens what the app's own setting
     * closed. That is the whole contract, and it is why routines can be added to a config without
     * anybody's apps getting easier to reach.
     */
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
        val layers = layersAt(config, app, nowMillis, dayIso, minuteOfDay)
        val verdict = layers.verdict ?: return Decision.Allow
        val rule = layers.rule
        val routineMode = layers.routineMode
        val blame = layers.routine

        // A window that closed after the allowance was handed out wins over it — a rule's or a
        // routine's. The app's own mode is not a window: switching it to Block revokes the
        // allowance in the store, so the policy has no second-guessing to do there, and an
        // allowance already running is honoured to its end.
        val windowBlock = rule?.mode == RuleMode.BLOCK || routineMode == RuleMode.BLOCK
        if (windowBlock) return Decision.Intercept(app, limitReached = false, verdict = RuleMode.BLOCK, rule = rule, routine = blame)
        val until = allowances[packageName] ?: 0L
        if (until > nowMillis) return Decision.Allow
        val limitReached = app.dailyLimit?.let { opensToday >= it } ?: false
        if (limitReached) return Decision.Intercept(app, limitReached = true, verdict = verdict, rule = rule, routine = blame)
        if (verdict == RuleMode.FREE) return Decision.Allow
        return Decision.Intercept(app, limitReached = false, verdict = verdict, rule = rule, routine = blame)
    }

    /**
     * What the app's two layers come to at some minute of some day **by the clock alone**: the
     * schedule, the app's rules and any routine window open then. Nothing that depends on the
     * instant — a session, a break, an allowance — is in here, which is what makes it safe to
     * walk minute by minute over a future week.
     */
    fun verdictAt(config: MonkConfig, app: BlockedApp, dayIso: Int, minuteOfDay: Int): RuleMode? {
        val appMode: RuleMode? = if (!config.schedule.isActive(dayIso, minuteOfDay)) null
        else app.activeRule(dayIso, minuteOfDay)?.mode
            ?: if (app.mode == BlockMode.BLOCK) RuleMode.BLOCK else RuleMode.PAUSE
        val routineMode = config.routines
            .filter { it.isOpen(dayIso, minuteOfDay) && it.covers(app.packageName) }
            .minByOrNull { it.mode.ordinal }?.ruleMode
        return strictest(appMode, routineMode)
    }

    /** [layersAt]'s answer alone, for callers that only need to know what would happen. */
    fun verdictNow(config: MonkConfig, app: BlockedApp, nowMillis: Long, dayIso: Int, minuteOfDay: Int): RuleMode? =
        layersAt(config, app, nowMillis, dayIso, minuteOfDay).verdict

    /**
     * Millis until the app's verdict can change on its own: the nearest rule boundary or
     * the global schedule's; null when nothing is time-bound. The service re-judges the
     * foreground app then, so a block window starting mid-session actually starts.
     */
    /**
     * Wall-clock minutes until the nearest rule, schedule or routine-window boundary, or null.
     * Windows of one routine are taken one by one rather than as their union, so two that touch
     * ask for one re-judgement too many; the gate simply decides again and finds nothing changed,
     * which is far cheaper than getting a boundary wrong.
     */
    fun minutesToNextChange(config: MonkConfig, app: BlockedApp, dayIso: Int, minuteOfDay: Int): Int? = buildList {
        app.rules.forEach { r -> TimeWindow.minutesToNextChange(r.days, r.startMinute, r.endMinute, dayIso, minuteOfDay)?.let { add(it) } }
        if (config.schedule.enabled) {
            TimeWindow.minutesToNextChange(config.schedule.days, config.schedule.startMinute, config.schedule.endMinute, dayIso, minuteOfDay)?.let { add(it) }
        }
        config.routines.forEach { routine ->
            if (!routine.enabled || routine.coversNothing || !routine.covers(app.packageName)) return@forEach
            routine.windows.forEach { w -> TimeWindow.minutesToNextChange(w.days, w.startMinute, w.endMinute, dayIso, minuteOfDay)?.let { add(it) } }
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
        // A break or a hand-started routine ending is a verdict change too: the app is still in
        // front, and nothing else would tell us the moment protection comes back.
        val breakEnd = if (nowMillis > 0 && config.isPaused(nowMillis)) config.pausedUntil - nowMillis else null
        val runEnd = if (nowMillis > 0) config.activeRun(nowMillis)?.let { it.until - nowMillis } else null
        return listOfNotNull(minutes, breakEnd, runEnd).minOrNull()
    }

    /**
     * Minutes until the block on this app lifts by the clock, walking minute by minute up to a
     * week; null when nothing within the week lifts it, and null when the clock is not blocking
     * it now (ask [verdictAt] first to tell those two apart).
     *
     * Both layers are walked, not just the rules: a rule window that closes at 18:00 inside a
     * routine that blocks until 22:00 does not open anything at 18:00, and a screen that
     * promised 18:00 would be lying to the one person who took it seriously.
     */
    fun blockEndsInMinutes(config: MonkConfig, app: BlockedApp, dayIso: Int, minuteOfDay: Int): Int? {
        if (verdictAt(config, app, dayIso, minuteOfDay) != RuleMode.BLOCK) return null
        var day = dayIso
        var minute = minuteOfDay
        for (elapsed in 1..7 * TimeWindow.DAY) {
            minute++
            if (minute == TimeWindow.DAY) { minute = 0; day = TimeWindow.nextDay(day) }
            if (verdictAt(config, app, day, minute) != RuleMode.BLOCK) return elapsed
        }
        return null
    }
}
