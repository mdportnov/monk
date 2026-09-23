package com.mdportnov.monk.shared.data

import kotlin.time.Clock
import com.mdportnov.monk.shared.model.AppDayStats
import com.mdportnov.monk.shared.model.BlockPolicy
import com.mdportnov.monk.shared.model.BuiltInRoutines
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.DayStats
import com.mdportnov.monk.shared.model.Decision
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.Routine
import com.mdportnov.monk.shared.model.RoutineMode
import com.mdportnov.monk.shared.model.RoutineRun
import com.mdportnov.monk.shared.model.RuleMode
import com.mdportnov.monk.shared.model.Schedule
import com.mdportnov.monk.shared.model.Stats
import com.mdportnov.monk.shared.model.TimeRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Single source of truth for config, temporary allowances and stats. One instance per process:
 * on Android the accessibility service, the intercept screen and the main UI all share it, and
 * every caller is on the main thread — the class is not otherwise thread-safe.
 */
class MonkStore(
    private val kv: KeyValueStore,
    /** Where decode failures go; the platform wires a logger. */
    private val onLoadFailure: (key: String, error: Throwable) -> Unit = { _, _ -> },
    /** Stats may live in their own file: they are rewritten on every intercept and dwarf the config. */
    private val statsKv: KeyValueStore = kv,
) {
    // coerceInputValues: an enum constant this build does not know (written by a newer version)
    // falls back to the property default instead of throwing the whole config away.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    /** Zeros and empty maps are the bulk of a stats blob; defaults are implied on decode anyway. */
    private val statsJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; coerceInputValues = true }
    private val allowSerializer = MapSerializer(String.serializer(), Long.serializer())

    /** Non-null when the stored config could not be read; the raw blob is kept under [KEY_CONFIG_BACKUP]. */
    var configLoadError: Throwable? = null
        private set

    private val loadedConfig = load(KEY_CONFIG, MonkConfig.serializer()) ?: MonkConfig()

    // Normalised on the way in, once: the built-in routines are seeded or topped up, hand-edited
    // values are repaired, and a focus session written by a build before routines becomes a run.
    // Everything downstream may assume the result.
    private val _config = MutableStateFlow(loadedConfig.normalized(nowMillis()))
    val config: StateFlow<MonkConfig> = _config

    private val _stats = MutableStateFlow(loadStats() ?: Stats())
    val stats: StateFlow<Stats> = _stats

    private val _allowances = MutableStateFlow(load(KEY_ALLOW, allowSerializer) ?: emptyMap())
    val allowances: StateFlow<Map<String, Long>> = _allowances

    init {
        // Written back only when normalising actually changed something — a first run, an update
        // that adds a built-in, a stale focus session — so an ordinary start touches no disk.
        // Never over a blob that failed to decode: an update that can read it again finds it intact.
        if (configLoadError == null && _config.value != loadedConfig) kv.putString(KEY_CONFIG, json.encodeToString(MonkConfig.serializer(), _config.value))
    }

    fun updateConfig(transform: (MonkConfig) -> MonkConfig) {
        val next = transform(_config.value)
        _config.value = next
        kv.putString(KEY_CONFIG, json.encodeToString(MonkConfig.serializer(), next))
    }

    /** Switching an app to Block ends its live allowance: "never opens" starts now, not in five minutes. */
    fun upsertApp(app: BlockedApp) {
        val before = _config.value.app(app.packageName)
        updateConfig { c -> c.copy(apps = c.apps.filter { it.packageName != app.packageName } + app) }
        if (app.mode == BlockMode.BLOCK && before?.mode != BlockMode.BLOCK) revokeAllowance(app.packageName)
    }

    /**
     * Adds a package to the watch list. If it was watched before, its old settings come back
     * (minus the lock: removing the app is the sanctioned way out of a lock). Already-watched
     * packages are left untouched.
     */
    fun addApp(packageName: String, label: String) = updateConfig { c ->
        if (c.app(packageName) != null) return@updateConfig c
        val restored = c.archived(packageName)?.copy(label = label, locked = false, uninstalledAt = null) ?: BlockedApp(packageName, label)
        c.copy(apps = c.apps + restored, archivedApps = c.archivedApps.filter { it.packageName != packageName })
    }

    /**
     * Removes from the list but keeps the settings in the archive. Returns false when strict mode
     * refused it, so the caller can say so instead of closing the page as though it had worked.
     */
    fun removeApp(packageName: String): Boolean {
        val current = _config.value
        if (current.isStrict(nowMillis())) return false
        val gone = current.app(packageName) ?: return false
        updateConfig { c ->
            c.copy(
                apps = c.apps.filter { it.packageName != packageName },
                archivedApps = (c.archivedApps.filter { it.packageName != packageName } + gone).takeLast(MAX_ARCHIVE),
            )
        }
        return true
    }

    /** The package was uninstalled: park it. Returns true if something moved. */
    fun archiveIfWatched(packageName: String): Boolean {
        val gone = _config.value.app(packageName) ?: return false
        updateConfig { c ->
            c.copy(
                apps = c.apps.filter { it.packageName != packageName },
                archivedApps = (c.archivedApps.filter { it.packageName != packageName } + gone.copy(uninstalledAt = nowMillis())).takeLast(MAX_ARCHIVE),
            )
        }
        return true
    }

    /** The package came back after an uninstall: restore it without asking. */
    fun restoreIfArchived(packageName: String): Boolean {
        val archived = _config.value.archived(packageName) ?: return false
        addApp(packageName, archived.label)
        return true
    }

    /**
     * One write for the whole picker result instead of one per app. Returns false when strict mode
     * dropped some of the removals, so the screen can say which half of the edit went through.
     */
    fun applyPicker(remove: Set<String>, add: List<Pair<String, String>>): Boolean {
        val refused = _config.value.isStrict(nowMillis()) && remove.any { _config.value.app(it) != null }
        applyPickerWrite(remove, add)
        return !refused
    }

    private fun applyPickerWrite(remove: Set<String>, add: List<Pair<String, String>>) = updateConfig { c0 ->
        var c = c0
        val removable = if (c.isStrict(nowMillis())) emptySet() else remove
        removable.forEach { pkg ->
            val gone = c.app(pkg) ?: return@forEach
            c = c.copy(apps = c.apps.filter { it.packageName != pkg }, archivedApps = (c.archivedApps.filter { it.packageName != pkg } + gone).takeLast(MAX_ARCHIVE))
        }
        add.forEach { (pkg, label) ->
            if (c.app(pkg) != null) return@forEach
            val restored = c.archived(pkg)?.copy(label = label, locked = false, uninstalledAt = null) ?: BlockedApp(pkg, label)
            c = c.copy(apps = c.apps + restored, archivedApps = c.archivedApps.filter { it.packageName != pkg })
        }
        c
    }

    fun forgetArchived(packageName: String) = updateConfig { c ->
        c.copy(archivedApps = c.archivedApps.filter { it.packageName != packageName })
    }

    /** A Block window that is open right now wins over the allowance in the policy; drop it so the list says the same. */
    fun upsertRule(packageName: String, rule: TimeRule) {
        updateConfig { c ->
            c.copy(apps = c.apps.map { a -> if (a.packageName == packageName) a.copy(rules = a.rules.filter { it.id != rule.id } + rule) else a })
        }
        val m = localMoment()
        if (rule.mode == RuleMode.BLOCK && rule.isActive(m.dayIso, m.minuteOfDay)) revokeAllowance(packageName)
    }

    fun removeRule(packageName: String, ruleId: Long) = updateConfig { c ->
        c.copy(apps = c.apps.map { a -> if (a.packageName == packageName) a.copy(rules = a.rules.filter { it.id != ruleId }) else a })
    }

    /**
     * Millis until this app's verdict can change on its own: a rule, schedule or routine-window
     * boundary, the end of a break, or the end of a session started by hand. This is the value
     * the accessibility service schedules its next judgement on, so anything missing from it is
     * a moment protection comes back — or lifts — with nobody watching.
     */
    fun millisToNextChange(packageName: String): Long? {
        val config = _config.value
        val app = config.app(packageName) ?: return null
        // One reading of the clock for both halves: read twice across a minute boundary, "one
        // minute to the 10:00 block" would be counted from 10:00 and land at 10:01.
        val at = Clock.System.now()
        val m = localMoment(at)
        val minutes = BlockPolicy.minutesToNextChange(config, app, m.dayIso, m.minuteOfDay)
        // Boundaries are wall-clock minutes; convert through the zone so DST nights land on time.
        val boundary = minutes?.let { wallMinutesToMillis(it, at) }
        val now = at.toEpochMilliseconds()
        val breakEnd = if (config.isPaused(now)) config.pausedUntil - now else null
        val sessionEnd = config.activeRun(now)?.let { it.until - now }
        return listOfNotNull(boundary, breakEnd, sessionEnd).minOrNull()
    }

    /**
     * Epoch millis when the block on this app lifts, or null when nothing on the clock lifts it.
     * Two things can hold it: the clock layers (schedule, rules, routine windows) and a session
     * running by hand. While both hold it, it lifts when the later of them does — the earlier
     * one letting go changes nothing the user can see.
     */
    fun blockEndsAt(packageName: String): Long? {
        val config = _config.value
        val app = config.app(packageName) ?: return null
        val now = nowMillis()
        val m = localMoment()
        val byClock = if (BlockPolicy.verdictAt(config, app, m.dayIso, m.minuteOfDay) != RuleMode.BLOCK) {
            null
        } else {
            // Blocked by the clock with no end inside a week: nothing to promise.
            val minutes = BlockPolicy.blockEndsInMinutes(config, app, m.dayIso, m.minuteOfDay) ?: return null
            clockAfterWallMinutes(minutes)
        }
        val bySession = config.activeRun(now)
            ?.takeIf { run -> config.routine(run.routineId)?.let { it.covers(packageName) && it.mode == RoutineMode.BLOCK } == true }
            ?.until
        return when {
            byClock == null -> bySession
            bySession == null -> byClock
            else -> maxOf(byClock, bySession)
        }
    }

    /** Whether a break may start right now: on, inside the schedule, nothing stronger running, cooldown over. */
    fun canStartBreak(now: Long = nowMillis()): Boolean = localMoment().let { _config.value.canStartBreak(now, it.dayIso, it.minuteOfDay) }

    /** Starts a break if [canStartBreak] allows one right now. Returns whether it did. */
    fun pauseProtection(untilMillis: Long): Boolean {
        val now = nowMillis()
        if (untilMillis <= now || !canStartBreak(now)) return false
        updateConfig { it.copy(pausedUntil = untilMillis, pauseStartedAt = now) }
        return true
    }

    fun resumeProtection() = updateConfig { it.endingBreak(nowMillis()) }

    /** The master switch, off. Strict mode and a running routine keep it on; locked apps ignore it anyway. */
    fun switchOff(): Boolean {
        val now = nowMillis()
        if (_config.value.isStrict(now) || _config.value.activeRun(now) != null) return false
        updateConfig { it.endingBreak(now).copy(enabled = false) }
        return true
    }

    /** The master switch, on. A break, if one was somehow running, ends here too. */
    fun switchOn() = updateConfig { it.endingBreak(nowMillis()).copy(enabled = true) }

    /** The wall clock jumped by [deltaMillis]: every deadline follows it, allowances included. */
    fun shiftTimers(deltaMillis: Long) {
        if (deltaMillis == 0L) return
        updateConfig { it.shifted(deltaMillis) }
        val next = _allowances.value.mapValues { (_, until) -> until + deltaMillis }
        _allowances.value = next
        kv.putString(KEY_ALLOW, json.encodeToString(allowSerializer, next))
    }

    // --- routines ---

    /**
     * Adds or replaces a routine. Under strict mode — and while the routine itself is running,
     * which is the same promise with a shorter fuse — only a version that protects at least as
     * much gets through: tighten as much as you like, soften nothing. Returns false when it was
     * refused, so the caller can say why rather than appear to have saved.
     */
    fun upsertRoutine(routine: Routine): Boolean {
        val now = nowMillis()
        val current = _config.value
        val clean = routine.repaired().copy(builtIn = routine.id in BuiltInRoutines.ids)
        if (clean.id.isBlank()) return false
        val before = current.routine(clean.id)
        if (before == null && current.routines.size >= Routine.MAX_ROUTINES) return false
        val sealed = current.isStrict(now) || current.activeRun(now)?.routineId == clean.id
        if (sealed && before != null && !clean.isAtLeastAsStrictAs(before)) return false
        updateConfig { c ->
            if (c.routine(clean.id) == null) c.copy(routines = c.routines + clean)
            else c.copy(routines = c.routines.map { if (it.id == clean.id) clean else it })
        }
        // A routine that blocks this very minute must not be undercut by an allowance handed out
        // before it was saved — the same rule a Block window already follows.
        val m = localMoment()
        if (clean.mode == RoutineMode.BLOCK && (clean.isOpen(m.dayIso, m.minuteOfDay) || _config.value.activeRun(now)?.routineId == clean.id)) {
            revokeAllowancesFor(clean)
        }
        return true
    }

    /** Switches one on or off. Off is a softening, so strict mode refuses it. */
    fun setRoutineEnabled(id: String, on: Boolean): Boolean {
        val routine = _config.value.routine(id) ?: return false
        if (routine.enabled == on) return true
        return upsertRoutine(routine.copy(enabled = on))
    }

    /**
     * Deletes a routine the user made. Built-ins are not deletable by design — they are switched
     * off or put back instead — and neither strict mode nor a running session lets one go.
     */
    fun deleteRoutine(id: String): Boolean {
        val now = nowMillis()
        val current = _config.value
        val routine = current.routine(id) ?: return false
        if (routine.builtIn || current.isStrict(now) || current.activeRun(now)?.routineId == id) return false
        updateConfig { c -> c.copy(routines = c.routines.filter { it.id != id }) }
        return true
    }

    /**
     * A built-in back to the way it shipped, keeping only whether it is switched on. Refused
     * while it is the routine running: a session is a promise about a particular set of hours
     * and apps, and putting it back to factory settings mid-flight would rewrite the promise
     * under the person who made it — even when the new shape happens to be stricter.
     */
    fun resetRoutine(id: String): Boolean {
        val factory = BuiltInRoutines.factory(id) ?: return false
        val current = _config.value
        if (current.activeRun(nowMillis())?.routineId == id) return false
        val enabled = current.routine(id)?.enabled ?: factory.enabled
        return upsertRoutine(factory.copy(enabled = enabled))
    }

    /**
     * Starts a routine by hand until [untilMillis]. One-way, like strict mode: a session cannot be
     * cut short, only extended by starting the same one again for longer. A running break ends
     * (and its cooldown starts), protection turns on, and the allowances of the apps it covers
     * are dropped, so "blocked from now" means from now.
     */
    fun startRoutine(id: String, untilMillis: Long): Boolean {
        val now = nowMillis()
        val current = _config.value
        val routine = current.routine(id) ?: return false
        if (!routine.enabled || routine.coversNothing || untilMillis <= now) return false
        val live = current.activeRun(now)
        if (live != null && (live.routineId != id || untilMillis <= live.until)) return false
        val run = RoutineRun(id, live?.startedAt ?: now, untilMillis)
        updateConfig { it.endingBreak(now).copy(run = run, enabled = true) }
        revokeAllowancesFor(routine)
        return true
    }

    /** Epoch millis when the routine in force stops covering things, or null when nothing ends it. */
    fun routineEndsAt(routine: Routine): Long? {
        val now = nowMillis()
        _config.value.activeRun(now)?.let { if (it.routineId == routine.id) return it.until }
        val m = localMoment()
        return routine.openUntilMinutes(m.dayIso, m.minuteOfDay)?.let { clockAfterWallMinutes(it) }
    }

    /**
     * Puts apps on the watch list and into a routine in one act. This is what the picker opened
     * from a routine does: being asked to add the app to a general list first, and only then to
     * the routine, is bookkeeping the person should never have been shown.
     *
     * A routine that already covers every app needs no scope change — the apps join it by being
     * watched at all. Returns false only if the routine is gone or the widening was refused.
     */
    fun addAppsToRoutine(routineId: String, apps: List<Pair<String, String>>): Boolean {
        val routine = _config.value.routine(routineId) ?: return false
        apps.forEach { (pkg, label) -> addApp(pkg, label) }
        if (routine.allApps || apps.isEmpty()) return true
        val current = _config.value.routine(routineId) ?: return false
        return upsertRoutine(current.copy(packages = current.packages + apps.map { it.first }))
    }

    /**
     * Changes the base hours. Under strict mode only a version that covers at least as many
     * minutes gets through — which includes switching them off entirely, since that means every
     * minute. Returns false when it was refused.
     */
    fun setSchedule(next: Schedule): Boolean {
        val current = _config.value
        if (current.isStrict(nowMillis()) && !next.isAtLeastAsStrictAs(current.schedule)) return false
        updateConfig { it.copy(schedule = next) }
        return true
    }

    /** Watched apps a routine actually reaches; the ones it lists but nobody watches do not count. */
    fun appsCovered(routine: Routine): List<String> =
        _config.value.apps.map { it.packageName }.filter { routine.covers(it) }

    private fun revokeAllowancesFor(routine: Routine) {
        val next = _allowances.value.filterKeys { !routine.covers(it) }
        if (next == _allowances.value) return
        _allowances.value = next
        kv.putString(KEY_ALLOW, json.encodeToString(allowSerializer, next))
    }

    /** One-way while it lasts: there is deliberately no `disableStrict`. Ends a running break and turns protection on. */
    fun enableStrict(untilMillis: Long) {
        val now = nowMillis()
        // Already over: nothing to hold, and ending the user's break for it would be all it did.
        if (untilMillis <= now) return
        updateConfig { it.endingBreak(now).copy(strictUntil = untilMillis, enabled = true) }
    }

    fun grantAllowance(packageName: String, minutes: Int, now: Long = nowMillis()) {
        val next = _allowances.value.filterValues { it > now } + (packageName to now + minutes * 60_000L)
        _allowances.value = next
        kv.putString(KEY_ALLOW, json.encodeToString(allowSerializer, next))
    }

    fun revokeAllowance(packageName: String) {
        val next = _allowances.value - packageName
        _allowances.value = next
        kv.putString(KEY_ALLOW, json.encodeToString(allowSerializer, next))
    }

    fun activeAllowances(now: Long = nowMillis()) = _allowances.value.filterValues { it > now }

    fun opensToday(packageName: String) = _stats.value.opensToday(localMoment().dateIso, packageName)
    fun interceptsToday(packageName: String) = _stats.value.day(localMoment().dateIso).byApp[packageName]?.intercepted ?: 0

    fun decide(packageName: String): Decision {
        val m = localMoment()
        return BlockPolicy.decide(
            _config.value, packageName, nowMillis(), m.dayIso, m.minuteOfDay, _allowances.value,
            opensToday = _stats.value.opensToday(m.dateIso, packageName),
        )
    }

    fun recordIntercepted(packageName: String) {
        val hour = localMoment().hour
        bump(packageName, { it.copy(intercepted = it.intercepted + 1) }) { d ->
            d.copy(intercepted = d.intercepted + 1, byHour = d.byHour + (hour to (d.byHour[hour] ?: 0) + 1))
        }
    }

    fun recordTurnedAway(packageName: String) =
        bump(packageName, { it.copy(turnedAway = it.turnedAway + 1) }) { it.copy(turnedAway = it.turnedAway + 1) }

    fun recordOpened(packageName: String, reason: String?) =
        bump(
            packageName,
            { a ->
                a.copy(
                    opened = a.opened + 1,
                    reasons = if (reason == null) a.reasons else a.reasons + (reason to (a.reasons[reason] ?: 0) + 1),
                )
            },
        ) { it.copy(opened = it.opened + 1) }

    fun resetStats() {
        _stats.value = Stats()
        statsKv.putString(KEY_STATS, statsJson.encodeToString(Stats.serializer(), Stats()))
    }

    private fun bump(packageName: String, app: (AppDayStats) -> AppDayStats, day: (DayStats) -> DayStats) {
        val date = localMoment().dateIso
        val current = _stats.value
        val d0 = current.day(date)
        val d1 = day(d0).let { d -> d.copy(byApp = d.byApp + (packageName to app(d.byApp[packageName] ?: AppDayStats()))) }
        val label = _config.value.app(packageName)?.label
        val next = Stats(
            days = (current.days.filter { it.date != date } + d1).sortedBy { it.date }.takeLast(90),
            labels = if (label == null) current.labels else current.labels + (packageName to label),
        )
        _stats.value = next
        statsKv.putString(KEY_STATS, statsJson.encodeToString(Stats.serializer(), next))
    }

    /**
     * Unreadable data is never silently replaced: the raw blob moves to a `.bak` key (the next
     * write would otherwise overwrite the only copy) and the failure is reported.
     */
    private fun <T> load(key: String, serializer: KSerializer<T>): T? {
        val raw = kv.getString(key) ?: return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrElse { e ->
            kv.putString("$key.bak", raw)
            if (key == KEY_CONFIG) configLoadError = e
            onLoadFailure(key, e)
            null
        }
    }

    private fun loadStats(): Stats? {
        // Stats moved to their own store; pick up a blob left in the main one by older builds.
        val raw = statsKv.getString(KEY_STATS) ?: kv.getString(KEY_STATS)?.also { kv.remove(KEY_STATS) } ?: return null
        return runCatching { statsJson.decodeFromString(Stats.serializer(), raw) }.getOrElse { e -> onLoadFailure(KEY_STATS, e); null }
    }

    /** Last backed-up config blob, for a "restore" affordance or a bug report. */
    fun configBackup(): String? = kv.getString(KEY_CONFIG_BACKUP)

    private companion object {
        const val MAX_ARCHIVE = 100
        const val KEY_CONFIG = "config"
        const val KEY_CONFIG_BACKUP = "config.bak"
        const val KEY_STATS = "stats"
        const val KEY_ALLOW = "allowances"
    }
}
