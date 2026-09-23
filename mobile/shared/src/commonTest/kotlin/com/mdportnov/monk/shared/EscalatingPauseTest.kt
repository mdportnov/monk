package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.MAX_PAUSE_SECONDS
import com.mdportnov.monk.shared.model.MonkConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class EscalatingPauseTest {
    private val config = MonkConfig(defaultDelaySeconds = 10)

    @Test
    fun flatWhenOff() {
        assertEquals(10, config.delayFor(BlockedApp("a", "A"), opensToday = 7))
    }

    @Test
    fun growsWithEachOpenToday() {
        val app = BlockedApp("a", "A", escalateSeconds = 5)
        assertEquals(10, config.delayFor(app, opensToday = 0))
        assertEquals(25, config.delayFor(app, opensToday = 3))
        assertEquals(45, config.delayFor(app.copy(delaySeconds = 30), opensToday = 3))
    }

    @Test
    fun stopsGrowingAtTheCap() {
        val app = BlockedApp("a", "A", escalateSeconds = 30)
        assertEquals(MAX_PAUSE_SECONDS, config.delayFor(app, opensToday = 50))
    }
}
