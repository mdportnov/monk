package com.mdportnov.monk.shared.ui.intercept

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PauseClockTest {
    private val frame = 16_666_667L

    @Test
    fun countsFramesUpToTheTotal() {
        val clock = PauseClock(1_000f)
        assertEquals(1, clock.secondsLeft)
        repeat(59) { clock.advance(frame) }
        assertFalse(clock.done)
        repeat(5) { clock.advance(frame) }
        assertTrue(clock.done)
        assertEquals(1_000f, clock.elapsed)
        assertEquals(0, clock.secondsLeft)
    }

    @Test
    fun aLongGapIsOneStepNotWaitedOutTime() {
        val clock = PauseClock(10_000f)
        clock.advance(60_000_000_000L)
        assertEquals(100f, clock.elapsed)
        assertEquals(10, clock.secondsLeft)
    }

    @Test
    fun backwardsOrZeroDeltasAreIgnored() {
        val clock = PauseClock(5_000f)
        clock.advance(0)
        clock.advance(-frame)
        assertEquals(0f, clock.elapsed)
    }

    @Test
    fun noDelayIsDoneFromTheStart() {
        val clock = PauseClock(0f)
        assertTrue(clock.done)
        assertEquals(1f, clock.fraction)
        assertEquals(0, clock.secondsLeft)
    }
}
