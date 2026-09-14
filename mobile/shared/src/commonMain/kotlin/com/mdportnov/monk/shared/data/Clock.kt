package com.mdportnov.monk.shared.data

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

data class LocalMoment(val dateIso: String, val dayIso: Int, val minuteOfDay: Int)

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun localMoment(): LocalMoment {
    val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val d = local.date
    val dateIso = "${d.year}-${d.month.number.pad()}-${d.day.pad()}"
    return LocalMoment(dateIso, d.dayOfWeek.isoDayNumber, local.hour * 60 + local.minute)
}

private fun Int.pad() = if (this < 10) "0$this" else toString()
