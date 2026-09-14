package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.data.InMemoryStore
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.model.BlockPolicy
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.Decision
import com.mdportnov.monk.shared.model.MonkConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The friction model: what may weaken protection, when, and what the clock can and cannot do. */
class ProtectionFrictionTest {
    private val insta = BlockedApp("com.instagram.android", "Instagram")
    private val base = MonkConfig(apps = listOf(insta))
    private val now = 10_000_000L
    private val min = 60_000L

    @Test
    fun breakNeedsProtectionOnAndNoStrongerStateRunning() {
        assertTrue(base.canStartBreak(now))
        assertFalse(base.copy(enabled = false).canStartBreak(now))
        assertFalse(base.copy(strictUntil = now + min).canStartBreak(now))
        assertFalse(base.copy(focusUntil = now + min).canStartBreak(now))
        assertFalse(base.copy(pausedUntil = now + min).canStartBreak(now))
    }

    @Test
    fun breakCooldownRunsFromTheEndOfTheLastOne() {
        val ended = now - 10 * min
        val ranOut = base.copy(pausedUntil = ended)
        assertEquals(ended + MonkConfig.BREAK_COOLDOWN_MS, ranOut.nextBreakAt(now))
        assertFalse(ranOut.canStartBreak(now))
        assertTrue(ranOut.canStartBreak(ended + MonkConfig.BREAK_COOLDOWN_MS))

        val cutShort = base.copy(pausedUntil = 0, lastBreakEndedAt = ended)
        assertEquals(ended + MonkConfig.BREAK_COOLDOWN_MS, cutShort.nextBreakAt(now))
        assertFalse(cutShort.canStartBreak(now))

        assertEquals(0L, base.nextBreakAt(now))
    }

    @Test
    fun resumingEarlyStartsTheCooldownFromNow() {
        val store = MonkStore(InMemoryStore())
        store.upsertApp(insta)
        val t0 = nowMillis()
        assertTrue(store.pauseProtection(t0 + 15 * min))
        assertTrue(store.config.value.isPaused(t0))
        store.resumeProtection()
        val c = store.config.value
        assertEquals(0L, c.pausedUntil)
        assertTrue(c.lastBreakEndedAt >= t0)
        assertFalse(store.pauseProtection(t0 + 15 * min))
        assertEquals(0L, store.config.value.pausedUntil)
        assertTrue(store.config.value.nextBreakAt(t0) >= t0 + MonkConfig.BREAK_COOLDOWN_MS)
    }

    @Test
    fun strictAndFocusRefuseBreaksAndOffAtTheStore() {
        val strict = MonkStore(InMemoryStore()).apply { upsertApp(insta); enableStrict(nowMillis() + 60 * min) }
        assertFalse(strict.pauseProtection(nowMillis() + 5 * min))
        assertFalse(strict.switchOff())
        assertTrue(strict.config.value.enabled)
        assertEquals(0L, strict.config.value.pausedUntil)

        val focus = MonkStore(InMemoryStore()).apply { upsertApp(insta); startFocus(nowMillis() + 30 * min) }
        assertFalse(focus.pauseProtection(nowMillis() + 5 * min))
        assertFalse(focus.switchOff())
        assertTrue(focus.config.value.enabled)

        val plain = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        assertTrue(plain.switchOff())
        assertFalse(plain.config.value.enabled)
    }

    @Test
    fun strictKeepsAppsOnTheListWhateverTheDoor() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta); enableStrict(nowMillis() + 60 * min) }
        store.removeApp(insta.packageName)
        assertNotNull(store.config.value.app(insta.packageName))
        store.applyPicker(remove = setOf(insta.packageName), add = listOf("com.x" to "X"))
        assertNotNull(store.config.value.app(insta.packageName))
        assertNotNull(store.config.value.app("com.x"))
    }

    @Test
    fun strictOverridesAStrayOffOrBreakInTheConfig() {
        val strict = base.copy(strictUntil = now + 60 * min)
        assertIs<Decision.Intercept>(BlockPolicy.decide(strict.copy(enabled = false), insta.packageName, now, 3, 12 * 60, emptyMap()))
        assertIs<Decision.Intercept>(BlockPolicy.decide(strict.copy(pausedUntil = now + 5 * min), insta.packageName, now, 3, 12 * 60, emptyMap()))
        assertEquals(Decision.Allow, BlockPolicy.decide(base.copy(enabled = false), insta.packageName, now, 3, 12 * 60, emptyMap()))
    }

    @Test
    fun clockJumpsMoveEveryTimerAndLeaveIdleOnesAtZero() {
        val running = base.copy(
            focusUntil = now + 30 * min, focusStartedAt = now, strictUntil = now + 3 * 60 * min,
            pausedUntil = 0, pauseStartedAt = 0, lastBreakEndedAt = now - 5 * min,
        )
        val forward = running.shifted(2 * 60 * min)
        assertEquals(now + 2 * 60 * min + 30 * min, forward.focusUntil)
        assertEquals(now + 2 * 60 * min, forward.focusStartedAt)
        assertEquals(now + 5 * 60 * min, forward.strictUntil)
        assertEquals(now + 2 * 60 * min - 5 * min, forward.lastBreakEndedAt)
        assertEquals(0L, forward.pausedUntil)
        assertEquals(0L, forward.pauseStartedAt)
        // Setting the clock two hours ahead does not end a half-hour focus session.
        assertTrue(forward.isFocus(now + 2 * 60 * min))
        assertFalse(forward.isFocus(now + 2 * 60 * min + 31 * min))

        // Setting it back does not stretch a break either: it still ends 15 minutes of real time later.
        val onBreak = base.copy(pausedUntil = now + 15 * min, pauseStartedAt = now)
        val back = onBreak.shifted(-60 * min)
        assertTrue(back.isPaused(now - 60 * min + 14 * min))
        assertFalse(back.isPaused(now - 60 * min + 15 * min))
    }

    @Test
    fun storeShiftsAllowancesWithTheConfig() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        val t0 = nowMillis()
        store.grantAllowance(insta.packageName, 5, now = t0)
        store.startFocus(t0 + 30 * min)
        store.grantAllowance(insta.packageName, 5, now = t0)
        store.shiftTimers(60 * min)
        assertEquals(t0 + 65 * min, store.allowances.value[insta.packageName])
        assertEquals(t0 + 90 * min, store.config.value.focusUntil)
    }
}
