package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.data.InMemoryStore
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.BlockPolicy
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.Decision
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.Schedule
import com.mdportnov.monk.shared.model.Stats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlockPolicyTest {
    private val insta = BlockedApp("com.instagram.android", "Instagram", BlockMode.DELAY)
    private val config = MonkConfig(apps = listOf(insta))

    private fun decide(
        cfg: MonkConfig = config,
        pkg: String = insta.packageName,
        now: Long = 1_000_000L,
        day: Int = 3,
        minute: Int = 12 * 60,
        allow: Map<String, Long> = emptyMap(),
        opens: Int = 0,
    ) = BlockPolicy.decide(cfg, pkg, now, day, minute, allow, opens)

    @Test
    fun interceptsWatchedApp() {
        val d = decide()
        assertIs<Decision.Intercept>(d)
        assertEquals(insta, d.app)
        assertFalse(d.limitReached)
        assertEquals(BlockMode.DELAY, d.effectiveMode)
    }

    @Test
    fun ignoresUnknownDisabledAndPaused() {
        assertEquals(Decision.Allow, decide(pkg = "com.example.other"))
        assertEquals(Decision.Allow, decide(cfg = config.copy(enabled = false)))
        assertEquals(Decision.Allow, decide(cfg = config.copy(pausedUntil = 2_000_000L)))
        assertIs<Decision.Intercept>(decide(cfg = config.copy(pausedUntil = 999_999L)))
    }

    @Test
    fun allowanceWinsUntilExpiry() {
        assertEquals(Decision.Allow, decide(now = 100, allow = mapOf(insta.packageName to 200)))
        assertIs<Decision.Intercept>(decide(now = 200, allow = mapOf(insta.packageName to 200)))
    }

    @Test
    fun dailyLimitTurnsPauseIntoBlock() {
        val limited = config.copy(apps = listOf(insta.copy(dailyLimit = 2)))
        val under = decide(cfg = limited, opens = 1)
        assertIs<Decision.Intercept>(under)
        assertEquals(BlockMode.DELAY, under.effectiveMode)
        val over = decide(cfg = limited, opens = 2)
        assertIs<Decision.Intercept>(over)
        assertTrue(over.limitReached)
        assertEquals(BlockMode.BLOCK, over.effectiveMode)
    }

    @Test
    fun focusBlocksEverythingWatchedEvenWithAllowance() {
        val focused = config.copy(focusUntil = 5_000_000L)
        val d = decide(cfg = focused, allow = mapOf(insta.packageName to 9_000_000L))
        assertIs<Decision.Intercept>(d)
        assertTrue(d.focus)
        assertEquals(BlockMode.BLOCK, d.effectiveMode)
        assertEquals(Decision.Allow, decide(cfg = focused, pkg = "com.example.other"))
        assertEquals(Decision.Allow, decide(cfg = focused.copy(focusUntil = 10L), allow = mapOf(insta.packageName to 9_000_000L)))
    }

    @Test
    fun scheduleOutsideWindowAllows() {
        val scheduled = config.copy(schedule = Schedule(enabled = true, days = setOf(1, 2, 3, 4, 5), startMinute = 9 * 60, endMinute = 18 * 60))
        assertIs<Decision.Intercept>(decide(cfg = scheduled, day = 3, minute = 10 * 60))
        assertEquals(Decision.Allow, decide(cfg = scheduled, day = 3, minute = 20 * 60))
        assertEquals(Decision.Allow, decide(cfg = scheduled, day = 6, minute = 10 * 60))
    }

    @Test
    fun scheduleCrossingMidnight() {
        val night = Schedule(enabled = true, days = setOf(5), startMinute = 22 * 60, endMinute = 7 * 60)
        assertTrue(night.isActive(dayIso = 5, minuteOfDay = 23 * 60))
        assertTrue(night.isActive(dayIso = 6, minuteOfDay = 3 * 60))
        assertFalse(night.isActive(dayIso = 6, minuteOfDay = 12 * 60))
        assertFalse(night.isActive(dayIso = 4, minuteOfDay = 23 * 60))
    }

    @Test
    fun storeRoundTripsConfigAllowancesAndStats() {
        val kv = InMemoryStore()
        val store = MonkStore(kv)
        store.upsertApp(insta.copy(mode = BlockMode.BLOCK, delaySeconds = 30, dailyLimit = 4))
        store.grantAllowance(insta.packageName, minutes = 5, now = 0)
        store.recordIntercepted(insta.packageName)
        store.recordOpened(insta.packageName, "BORED")
        store.recordIntercepted(insta.packageName)
        store.recordTurnedAway(insta.packageName)
        val reloaded = MonkStore(kv)
        val app = reloaded.config.value.app(insta.packageName)
        assertEquals(BlockMode.BLOCK, app?.mode)
        assertEquals(30, app?.delaySeconds)
        assertEquals(4, app?.dailyLimit)
        assertEquals(5 * 60_000L, reloaded.allowances.value[insta.packageName])
        assertEquals(1, reloaded.opensToday(insta.packageName))
        val stats = reloaded.stats.value
        assertEquals(2, stats.totalIntercepted)
        assertEquals(1, stats.totalTurnedAway)
        assertEquals(mapOf("BORED" to 1), stats.days.single().byApp[insta.packageName]?.reasons)
        assertEquals(2, stats.days.single().byHour.values.sum())
    }

    @Test
    fun legacyStatsWithoutPerAppFieldsStillLoad() {
        val kv = InMemoryStore()
        kv.putString("stats", """{"days":[{"date":"2026-09-13","intercepted":3,"turnedAway":2,"opened":1}]}""")
        val stats = MonkStore(kv).stats.value
        assertEquals(3, stats.totalIntercepted)
        assertEquals(emptyMap(), stats.day("2026-09-13").byApp)
    }

    @Test
    fun walkAwayStreakSkipsEmptyToday() {
        val stats = Stats(
            days = listOf(
                com.mdportnov.monk.shared.model.DayStats("2026-09-11", turnedAway = 1),
                com.mdportnov.monk.shared.model.DayStats("2026-09-12", turnedAway = 2),
                com.mdportnov.monk.shared.model.DayStats("2026-09-13", turnedAway = 1),
            ),
        )
        val dates = listOf("2026-09-09", "2026-09-10", "2026-09-11", "2026-09-12", "2026-09-13", "2026-09-14")
        assertEquals(3, stats.walkAwayStreak(dates))
        assertEquals(0, stats.walkAwayStreak(listOf("2026-09-01", "2026-09-02")))
    }
}
