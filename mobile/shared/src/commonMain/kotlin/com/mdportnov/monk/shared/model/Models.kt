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
data class BlockedApp(
    val packageName: String,
    val label: String,
    val mode: BlockMode = BlockMode.DELAY,
    /** null = use [MonkConfig.defaultDelaySeconds]. */
    val delaySeconds: Int? = null,
    /** null = use [MonkConfig.defaultAllowMinutes]. */
    val allowMinutes: Int? = null,
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
    val enabled: Boolean = true,
    val defaultDelaySeconds: Int = 10,
    val defaultAllowMinutes: Int = 5,
    val apps: List<BlockedApp> = emptyList(),
    val schedule: Schedule = Schedule(),
) {
    fun app(packageName: String): BlockedApp? = apps.firstOrNull { it.packageName == packageName }
    fun delayFor(app: BlockedApp) = app.delaySeconds ?: defaultDelaySeconds
    fun allowFor(app: BlockedApp) = app.allowMinutes ?: defaultAllowMinutes
}

@Serializable
data class DayStats(
    val date: String,
    val intercepted: Int = 0,
    val turnedAway: Int = 0,
    val opened: Int = 0,
)

@Serializable
data class Stats(
    val days: List<DayStats> = emptyList(),
) {
    val totalIntercepted get() = days.sumOf { it.intercepted }
    val totalTurnedAway get() = days.sumOf { it.turnedAway }
    val totalOpened get() = days.sumOf { it.opened }
    fun day(date: String) = days.firstOrNull { it.date == date } ?: DayStats(date)
}

data class InstalledApp(val packageName: String, val label: String)

sealed interface Decision {
    data object Allow : Decision
    data class Intercept(val app: BlockedApp) : Decision
}

object BlockPolicy {
    fun decide(
        config: MonkConfig,
        packageName: String,
        nowMillis: Long,
        dayIso: Int,
        minuteOfDay: Int,
        allowances: Map<String, Long>,
    ): Decision {
        if (!config.enabled) return Decision.Allow
        val app = config.app(packageName) ?: return Decision.Allow
        if (!config.schedule.isActive(dayIso, minuteOfDay)) return Decision.Allow
        val until = allowances[packageName] ?: 0L
        if (until > nowMillis) return Decision.Allow
        return Decision.Intercept(app)
    }
}
