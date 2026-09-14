package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.model.DayBounds
import com.mdportnov.monk.shared.model.UsageEvent
import com.mdportnov.monk.shared.model.UsageEventKind
import com.mdportnov.monk.shared.model.foldForegroundByDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenTimeFoldTest {
    private val day = 24 * 60 * 60_000L
    private val min = 60_000L
    private val days = listOf(
        DayBounds("2026-09-12", 0, day),
        DayBounds("2026-09-13", day, 2 * day),
        DayBounds("2026-09-14", 2 * day, 3 * day),
    )
    private val ig = "com.instagram.android"
    private val tg = "org.telegram.messenger"

    private fun resumed(pkg: String, at: Long, cls: String = "Main") = UsageEvent(pkg, cls, at, UsageEventKind.RESUMED)
    private fun paused(pkg: String, at: Long, cls: String = "Main") = UsageEvent(pkg, cls, at, UsageEventKind.PAUSED)

    @Test
    fun pairs_add_up_within_a_day() {
        val events = listOf(resumed(ig, 10 * min), paused(ig, 25 * min), resumed(ig, 40 * min), paused(ig, 42 * min))
        val r = foldForegroundByDay(events, days, now = 3 * day)
        assertEquals(17 * min, r.getValue(ig).getValue("2026-09-12"))
        assertNull(r.getValue(ig)["2026-09-13"])
    }

    @Test
    fun session_across_midnight_is_split_between_days() {
        val events = listOf(resumed(ig, day - 20 * min), paused(ig, day + 10 * min))
        val r = foldForegroundByDay(events, days, now = 3 * day)
        assertEquals(20 * min, r.getValue(ig).getValue("2026-09-12"))
        assertEquals(10 * min, r.getValue(ig).getValue("2026-09-13"))
    }

    @Test
    fun open_session_is_closed_at_now() {
        val events = listOf(resumed(ig, 2 * day + 5 * min))
        val r = foldForegroundByDay(events, days, now = 2 * day + 35 * min)
        assertEquals(30 * min, r.getValue(ig).getValue("2026-09-14"))
    }

    @Test
    fun activity_handover_inside_one_app_does_not_end_the_session() {
        // Second activity resumes before the first pauses: still one continuous foreground stretch.
        val events = listOf(
            resumed(ig, 0, "Main"),
            resumed(ig, 5 * min, "Reels"),
            paused(ig, 5 * min + 1, "Main"),
            paused(ig, 15 * min, "Reels"),
        )
        val r = foldForegroundByDay(events, days, now = 3 * day)
        assertEquals(15 * min, r.getValue(ig).getValue("2026-09-12"))
    }

    @Test
    fun pause_without_resume_is_ignored_and_reset_closes_everything() {
        val events = listOf(
            paused(tg, 3 * min),
            resumed(ig, 10 * min),
            resumed(tg, 12 * min),
            UsageEvent("", null, 20 * min, UsageEventKind.RESET),
            resumed(tg, 30 * min),
            paused(tg, 31 * min),
        )
        val r = foldForegroundByDay(events, days, now = 3 * day)
        // Telegram's resume at 12 min ends Instagram's turn; the reset at 20 ends Telegram's.
        assertEquals(2 * min, r.getValue(ig).getValue("2026-09-12"))
        assertEquals(9 * min, r.getValue(tg).getValue("2026-09-12"))
    }

    @Test
    fun unsorted_input_and_events_outside_the_window_are_handled() {
        val events = listOf(paused(ig, 3 * day + 10 * min), resumed(ig, 3 * day - 5 * min), resumed(tg, 0), paused(tg, 2 * min)).shuffled()
        val r = foldForegroundByDay(events, days, now = 3 * day + 10 * min)
        assertEquals(5 * min, r.getValue(ig).getValue("2026-09-14"))
        assertEquals(2 * min, r.getValue(tg).getValue("2026-09-12"))
        assertTrue(r.getValue(ig).keys.all { it in days.map { d -> d.date } })
    }

    @Test
    fun another_app_resuming_ends_the_session_even_without_a_pause() {
        // Instagram resumes a stack of four activities, stops three (no pause), and stays "resumed"
        // by its own events while Telegram is in front. The next resume is the truth.
        val events = listOf(
            resumed(ig, 10 * min, "Main"),
            resumed(ig, 10 * min, "UrlHandler"),
            resumed(ig, 10 * min, "ViewProfile"),
            paused(ig, 10 * min + 1000, "UrlHandler"),
            resumed(tg, 15 * min),
            paused(tg, 20 * min),
            resumed(ig, 30 * min, "Main"),
            paused(ig, 31 * min, "Main"),
        )
        val r = foldForegroundByDay(events, days, now = 3 * day)
        assertEquals(6 * min, r.getValue(ig).getValue("2026-09-12"))
        assertEquals(5 * min, r.getValue(tg).getValue("2026-09-12"))
    }

    @Test
    fun a_pause_after_a_handover_to_another_app_does_not_reopen_anything() {
        val events = listOf(resumed(ig, 0), resumed(tg, 5 * min), paused(ig, 5 * min + 100), paused(tg, 9 * min))
        val r = foldForegroundByDay(events, days, now = 3 * day)
        assertEquals(5 * min, r.getValue(ig).getValue("2026-09-12"))
        assertEquals(4 * min, r.getValue(tg).getValue("2026-09-12"))
    }
}
