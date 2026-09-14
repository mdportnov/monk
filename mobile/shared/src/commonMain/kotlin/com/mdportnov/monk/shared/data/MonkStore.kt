package com.mdportnov.monk.shared.data

import com.mdportnov.monk.shared.model.AppDayStats
import com.mdportnov.monk.shared.model.BlockPolicy
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.DayStats
import com.mdportnov.monk.shared.model.Decision
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.Stats
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
class MonkStore(private val kv: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val allowSerializer = MapSerializer(String.serializer(), Long.serializer())

    private val _config = MutableStateFlow(load(KEY_CONFIG, MonkConfig.serializer()) ?: MonkConfig())
    val config: StateFlow<MonkConfig> = _config

    private val _stats = MutableStateFlow(load(KEY_STATS, Stats.serializer()) ?: Stats())
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

    fun removeApp(packageName: String) = updateConfig { c ->
        c.copy(apps = c.apps.filter { it.packageName != packageName })
    }

    fun pauseProtection(untilMillis: Long) = updateConfig { it.copy(pausedUntil = untilMillis) }
    fun resumeProtection() = updateConfig { it.copy(pausedUntil = 0) }

    /** One-way, like strict mode: a focus session cannot be cut short. Live allowances are dropped. */
    fun startFocus(untilMillis: Long) {
        updateConfig { it.copy(focusUntil = untilMillis, pausedUntil = 0, enabled = true) }
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
        kv.putString(KEY_STATS, json.encodeToString(Stats.serializer(), Stats()))
    }

    private fun bump(packageName: String, app: (AppDayStats) -> AppDayStats, day: (DayStats) -> DayStats) {
        val date = localMoment().dateIso
        val current = _stats.value
        val d0 = current.day(date)
        val d1 = day(d0).let { d -> d.copy(byApp = d.byApp + (packageName to app(d.byApp[packageName] ?: AppDayStats()))) }
        val next = Stats(days = (current.days.filter { it.date != date } + d1).sortedBy { it.date }.takeLast(90))
        _stats.value = next
        kv.putString(KEY_STATS, json.encodeToString(Stats.serializer(), next))
    }

    private fun <T> load(key: String, serializer: KSerializer<T>): T? =
        kv.getString(key)?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }

    private companion object {
        const val KEY_CONFIG = "config"
        const val KEY_STATS = "stats"
        const val KEY_ALLOW = "allowances"
    }
}
