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

data class LocalMoment(val dateIso: String, val dayIso: Int, val minuteOfDay: Int, val hour: Int)

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun localMoment(): LocalMoment {
    val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val d = local.date
    return LocalMoment(d.toString(), d.dayOfWeek.isoDayNumber, local.hour * 60 + local.minute, local.hour)
}

/** "14:05" in the device zone. */
fun formatClock(epochMillis: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.hour.pad()}:${t.minute.pad()}"
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

private fun Int.pad() = if (this < 10) "0$this" else toString()
