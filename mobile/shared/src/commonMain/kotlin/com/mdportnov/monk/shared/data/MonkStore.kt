package com.mdportnov.monk.shared.data

import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.DayStats
import com.mdportnov.monk.shared.model.Decision
import com.mdportnov.monk.shared.model.BlockPolicy
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Single source of truth for config, temporary allowances and stats. One instance per process:
 * on Android the accessibility service, the intercept screen and the main UI all share it.
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

    fun decide(packageName: String): Decision {
        val m = localMoment()
        return BlockPolicy.decide(_config.value, packageName, nowMillis(), m.dayIso, m.minuteOfDay, _allowances.value)
    }

    fun recordIntercepted() = bump { it.copy(intercepted = it.intercepted + 1) }
    fun recordTurnedAway() = bump { it.copy(turnedAway = it.turnedAway + 1) }
    fun recordOpened() = bump { it.copy(opened = it.opened + 1) }

    private fun bump(transform: (DayStats) -> DayStats) {
        val date = localMoment().dateIso
        val current = _stats.value
        val day = transform(current.day(date))
        val next = Stats(days = (current.days.filter { it.date != date } + day).sortedBy { it.date }.takeLast(90))
        _stats.value = next
        kv.putString(KEY_STATS, json.encodeToString(Stats.serializer(), next))
    }

    private fun <T> load(key: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        kv.getString(key)?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }

    private companion object {
        const val KEY_CONFIG = "config"
        const val KEY_STATS = "stats"
        const val KEY_ALLOW = "allowances"
    }
}
