package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.data.InMemoryStore
import com.mdportnov.monk.shared.data.ScreenTimeArchive
import com.mdportnov.monk.shared.model.ScreenTimeReport
import com.mdportnov.monk.shared.model.mergeScreenTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenTimeMergeTest {
    private val min = 60_000L
    private val ig = "com.instagram.android"
    private val tg = "org.telegram.messenger"
    private val d1 = "2026-09-01"
    private val d2 = "2026-09-02"
    private val d3 = "2026-09-03"

    @Test
    fun os_wins_where_it_covers_and_archive_fills_the_rest() {
        val os = mapOf(ig to mapOf(d2 to 30 * min, d3 to 10 * min))
        val archive = mapOf(d1 to mapOf(ig to 5 * min, tg to 7 * min), d2 to mapOf(ig to 99 * min))
        val (merged, covered) = mergeScreenTime(os, osCovered = setOf(d2, d3), archive = archive, dates = listOf(d1, d2, d3))
        assertEquals(setOf(d1, d2, d3), covered)
        assertEquals(5 * min, merged.getValue(ig)[d1])
        assertEquals(30 * min, merged.getValue(ig)[d2])
        assertEquals(10 * min, merged.getValue(ig)[d3])
        assertEquals(7 * min, merged.getValue(tg)[d1])
        assertNull(merged.getValue(tg)[d2])
    }

    @Test
    fun a_day_neither_has_stays_uncovered() {
        val (merged, covered) = mergeScreenTime(emptyMap(), osCovered = setOf(d3), archive = mapOf(d1 to mapOf(ig to min)), dates = listOf(d1, d2, d3))
        assertEquals(setOf(d1, d3), covered)
        assertFalse(d2 in merged.values.flatMap { it.keys })
    }

    @Test
    fun os_day_with_no_events_is_covered_but_empty_and_overrides_a_stale_archive() {
        val archive = mapOf(d2 to mapOf(ig to 40 * min))
        val (merged, covered) = mergeScreenTime(emptyMap(), osCovered = setOf(d2), archive = archive, dates = listOf(d2))
        assertTrue(d2 in covered)
        assertTrue(merged.isEmpty())
    }

    @Test
    fun archive_records_covered_days_and_replaces_them_on_the_next_write() {
        val a = ScreenTimeArchive(InMemoryStore())
        assertTrue(a.record(mapOf(ig to mapOf(d1 to 5 * min, d2 to 3 * min)), covered = setOf(d1, d2)))
        assertEquals(5 * min, a.days.getValue(d1)[ig])
        assertEquals(d1, a.earliestDate)
        // d2 grew (it was "today"); d1 is no longer covered by the OS and must survive untouched.
        assertTrue(a.record(mapOf(ig to mapOf(d2 to 60 * min), tg to mapOf(d2 to min)), covered = setOf(d2)))
        assertEquals(5 * min, a.days.getValue(d1)[ig])
        assertEquals(60 * min, a.days.getValue(d2)[ig])
        assertEquals(min, a.days.getValue(d2)[tg])
        assertFalse(a.record(mapOf(ig to mapOf(d2 to 60 * min), tg to mapOf(d2 to min)), covered = setOf(d2)), "identical write is a no-op")
    }

    @Test
    fun archive_survives_a_reload_and_keeps_only_the_top_apps_and_last_days() {
        val kv = InMemoryStore()
        val a = ScreenTimeArchive(kv)
        val many = (1..80).associate { "app$it" to mapOf(d1 to it * min) }
        a.record(many, setOf(d1))
        val b = ScreenTimeArchive(kv)
        assertEquals(ScreenTimeArchive.MAX_APPS_PER_DAY, b.days.getValue(d1).size)
        assertTrue("app80" in b.days.getValue(d1) && "app1" !in b.days.getValue(d1))
        fun pad(n: Int) = if (n < 10) "0$n" else "$n"
        val dates = (0 until 400).map { i -> "${2027 + i / 336}-${pad(1 + i % 336 / 28)}-${pad(1 + i % 28)}" }.distinct()
        assertTrue(dates.size > ScreenTimeArchive.MAX_DAYS)
        val perDay = dates.associateWith { min }
        a.record(mapOf(ig to perDay), dates.toSet())
        assertTrue(a.days.size <= ScreenTimeArchive.MAX_DAYS)
        assertEquals(dates.sortedDescending().take(a.days.size).toSet(), a.days.keys)
    }

    @Test
    fun report_totals_split_watched_from_the_whole_phone() {
        val r = ScreenTimeReport(
            supported = true, granted = true, dates = listOf(d1, d2), coveredDates = setOf(d1, d2),
            byApp = mapOf(ig to mapOf(d1 to 10 * min, d2 to 20 * min), tg to mapOf(d1 to 5 * min)),
            watched = setOf(tg),
        )
        assertEquals(35 * min, r.phoneTotal(listOf(d1, d2)))
        assertEquals(5 * min, r.watchedTotal(listOf(d1, d2)))
        assertEquals(0L, r.watchedByDate(d2))
        assertEquals(listOf(ig to 30 * min, tg to 5 * min), r.ranked(listOf(d1, d2)))
    }
}
