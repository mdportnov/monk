package com.mdportnov.monk.shared.data

import com.mdportnov.monk.shared.model.AppDayStats
import com.mdportnov.monk.shared.model.BlockPolicy
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.DayStats
import com.mdportnov.monk.shared.model.Decision
import com.mdportnov.monk.shared.model.MonkConfig
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

    private val _config = MutableStateFlow(load(KEY_CONFIG, MonkConfig.serializer()) ?: MonkConfig())
    val config: StateFlow<MonkConfig> = _config

    private val _stats = MutableStateFlow(loadStats() ?: Stats())
    val stats: StateFlow<Stats> = _stats

    private val _allowances = MutableStateFlow(load(KEY_ALLOW, allowSerializer) ?: emptyMap())
    val allowances: StateFlow<Map<String, Long>> = _allowances

    fun updateConfig(transform: (MonkConfig) -> MonkConfig) {
        val next = transform(_config.value)
        _config.value = next
        kv.putString(KEY_CONFIG, json.encodeToString(MonkConfig.serializer(), next))
    }

    fun upsertApp(app: BlockedApp) = updateConfig { c ->
        c.copy(apps = c.apps.filter { it.packageName != app.packageName } + app)
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

    /** Removes from the list but keeps the settings in the archive. Refused under strict mode. */
    fun removeApp(packageName: String) = updateConfig { c ->
        if (c.isStrict(nowMillis())) return@updateConfig c
        val gone = c.app(packageName) ?: return@updateConfig c
        c.copy(
            apps = c.apps.filter { it.packageName != packageName },
            archivedApps = (c.archivedApps.filter { it.packageName != packageName } + gone).takeLast(MAX_ARCHIVE),
        )
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

    /** One write for the whole picker result instead of one per app. */
    fun applyPicker(remove: Set<String>, add: List<Pair<String, String>>) = updateConfig { c0 ->
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

    fun upsertRule(packageName: String, rule: TimeRule) = updateConfig { c ->
        c.copy(apps = c.apps.map { a -> if (a.packageName == packageName) a.copy(rules = a.rules.filter { it.id != rule.id } + rule) else a })
    }

    fun removeRule(packageName: String, ruleId: Long) = updateConfig { c ->
        c.copy(apps = c.apps.map { a -> if (a.packageName == packageName) a.copy(rules = a.rules.filter { it.id != ruleId }) else a })
    }

    /** Millis until the app's verdict changes by the clock alone (rule or schedule boundary). */
    fun millisToNextChange(packageName: String): Long? {
        val app = _config.value.app(packageName) ?: return null
        val m = localMoment()
        val minutes = BlockPolicy.minutesToNextChange(_config.value, app, m.dayIso, m.minuteOfDay)
        // Boundaries are wall-clock minutes; convert through the zone so DST nights land on time.
        val boundary = minutes?.let { wallMinutesToMillis(it) }
        val now = nowMillis()
        val breakEnd = if (_config.value.isPaused(now)) _config.value.pausedUntil - now else null
        return listOfNotNull(boundary, breakEnd).minOrNull()
    }

    /** Epoch millis when the active BLOCK rule coverage ends, or null. */
    fun blockEndsAt(packageName: String): Long? {
        val app = _config.value.app(packageName) ?: return null
        val m = localMoment()
        val minutes = BlockPolicy.blockEndsInMinutes(app, m.dayIso, m.minuteOfDay) ?: return null
        return nowMillis() + wallMinutesToMillis(minutes)
    }

    /** Starts a break if [MonkConfig.canStartBreak] allows one right now. Returns whether it did. */
    fun pauseProtection(untilMillis: Long): Boolean {
        val now = nowMillis()
        if (!_config.value.canStartBreak(now)) return false
        updateConfig { it.copy(pausedUntil = untilMillis, pauseStartedAt = now) }
        return true
    }

    fun resumeProtection() = updateConfig { c ->
        if (!c.isPaused(nowMillis())) c else c.copy(pausedUntil = 0, lastBreakEndedAt = nowMillis())
    }

    /** The master switch, off. Strict mode and a focus session keep it on; locked apps ignore it anyway. */
    fun switchOff(): Boolean {
        val now = nowMillis()
        if (_config.value.isStrict(now) || _config.value.isFocus(now)) return false
        updateConfig { it.copy(enabled = false, pausedUntil = 0) }
        return true
    }

    /** The wall clock jumped by [deltaMillis]: every deadline follows it, allowances included. */
    fun shiftTimers(deltaMillis: Long) {
        if (deltaMillis == 0L) return
        updateConfig { it.shifted(deltaMillis) }
        val next = _allowances.value.mapValues { (_, until) -> until + deltaMillis }
        _allowances.value = next
        kv.putString(KEY_ALLOW, json.encodeToString(allowSerializer, next))
    }

    /** One-way, like strict mode: a focus session cannot be cut short. Live allowances are dropped. */
    fun startFocus(untilMillis: Long) {
        updateConfig { it.copy(focusUntil = untilMillis, focusStartedAt = nowMillis(), pausedUntil = 0, enabled = true) }
        _allowances.value = emptyMap()
        kv.putString(KEY_ALLOW, json.encodeToString(allowSerializer, emptyMap()))
    }

    /** One-way while it lasts: there is deliberately no `disableStrict`. */
    fun enableStrict(untilMillis: Long) = updateConfig { it.copy(strictUntil = untilMillis, pausedUntil = 0, enabled = true) }

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
        val next = Stats(days = (current.days.filter { it.date != date } + d1).sortedBy { it.date }.takeLast(90))
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
