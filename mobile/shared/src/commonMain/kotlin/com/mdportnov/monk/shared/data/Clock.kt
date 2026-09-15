package com.mdportnov.monk.shared.data

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import com.mdportnov.monk.shared.model.DayBounds
import com.mdportnov.monk.shared.model.TimeWindow

data class LocalMoment(val dateIso: String, val dayIso: Int, val minuteOfDay: Int, val hour: Int, val second: Int = 0, val dayOfYear: Int = 0)

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun localMoment(): LocalMoment {
    val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val d = local.date
    return LocalMoment(d.toString(), d.dayOfWeek.isoDayNumber, local.hour * 60 + local.minute, local.hour, local.second, d.dayOfYear)
}

/** "14:05" in the device zone. */
fun formatClock(epochMillis: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.hour.pad()}:${t.minute.pad()}"
}

/**
 * Millis from now until the wall-clock moment [minutes] minutes ahead, resolved through the
 * zone: across a DST change 06:00 stays 06:00 on the clock, which plain multiplication would miss.
 */
fun wallMinutesToMillis(minutes: Int): Long {
    val tz = TimeZone.currentSystemDefault()
    val nowInstant = Clock.System.now()
    val local = nowInstant.toLocalDateTime(tz)
    val total = local.hour * 60 + local.minute + minutes
    val target = local.date.plus(total / TimeWindow.DAY, DateTimeUnit.DAY).atTime(LocalTime((total % TimeWindow.DAY) / 60, total % 60))
    return (target.toInstant(tz) - nowInstant).inWholeMilliseconds
}

/**
 * Epoch millis of the wall-clock moment [minutes] minutes ahead, landing exactly on the minute.
 *
 * Built from the calendar rather than as now-plus-a-duration. `nowMillis() + wallMinutesToMillis(n)`
 * reads the clock twice, a hair apart, so the sum lands a millisecond or two BEFORE the boundary
 * and formats as the minute before it: "back at 08:59" for hours that start at 09:00. Anything
 * that only wants the wall-clock moment must come through here.
 */
fun clockAfterWallMinutes(minutes: Int): Long {
    val tz = TimeZone.currentSystemDefault()
    val local = Clock.System.now().toLocalDateTime(tz)
    val total = local.hour * 60 + local.minute + minutes
    return local.date.plus(total / TimeWindow.DAY, DateTimeUnit.DAY)
        .atTime(LocalTime((total % TimeWindow.DAY) / 60, total % 60))
        .toInstant(tz)
        .toEpochMilliseconds()
}

/** Epoch millis of the next local midnight. */
fun nextMidnightMillis(): Long {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    return today.plus(1, DateTimeUnit.DAY).atTime(LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
}

/** Dates as ISO strings, oldest first, ending today. */
fun lastDates(n: Int): List<String> {
    val today = LocalDate.parse(localMoment().dateIso)
    return (n - 1 downTo 0).map { back -> today.minus(back, DateTimeUnit.DAY).toString() }
}

/** Local-day epoch ranges for ISO [dates], resolved through the zone (DST days are 23 or 25 h long). */
fun dayBounds(dates: List<String>): List<DayBounds> {
    val tz = TimeZone.currentSystemDefault()
    return dates.map { iso ->
        val d = LocalDate.parse(iso)
        DayBounds(iso, d.atTime(LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds(), d.plus(1, DateTimeUnit.DAY).atTime(LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds())
    }
}

private fun Int.pad() = if (this < 10) "0$this" else toString()
