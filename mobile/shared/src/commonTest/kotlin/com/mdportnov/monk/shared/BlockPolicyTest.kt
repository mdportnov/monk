package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.data.InMemoryStore
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.BlockPolicy
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.Decision
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.BuiltInRoutines
import com.mdportnov.monk.shared.model.Routine
import com.mdportnov.monk.shared.model.RoutineRun
import com.mdportnov.monk.shared.model.Schedule
import com.mdportnov.monk.shared.model.Stats
import com.mdportnov.monk.shared.model.RuleMode
import com.mdportnov.monk.shared.model.TimeRule
import com.mdportnov.monk.shared.model.TimeWindow
import kotlin.test.assertNull
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
    fun aRunningRoutineBlocksEverythingItCoversEvenWithAllowance() {
        val running = config.withRun(until = 5_000_000L)
        val d = decide(cfg = running, allow = mapOf(insta.packageName to 9_000_000L))
        assertIs<Decision.Intercept>(d)
        assertEquals(BuiltInRoutines.FOCUS, d.routine?.id)
        assertEquals(BlockMode.BLOCK, d.effectiveMode)
        // Only what the routine covers, and only while it lasts.
        assertEquals(Decision.Allow, decide(cfg = running, pkg = "com.example.other"))
        assertEquals(Decision.Allow, decide(cfg = config.withRun(until = 10L), allow = mapOf(insta.packageName to 9_000_000L)))
    }

    /** The built-in focus routine, running until [until]: what a hand-started session looks like. */
    private fun MonkConfig.withRun(until: Long): MonkConfig {
        val focus = BuiltInRoutines.factory(BuiltInRoutines.FOCUS)!!
        return copy(routines = routines.filter { it.id != focus.id } + focus, run = RoutineRun(focus.id, 0L, until))
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
    fun unreadableConfigIsBackedUpNotDropped() {
        val kv = InMemoryStore()
        kv.putString("config", "{not json")
        var reported: String? = null
        val store = MonkStore(kv, onLoadFailure = { key, _ -> reported = key })
        assertEquals("config", reported)
        assertEquals("{not json", store.configBackup())
        // Back to defaults, normalised the same way a first run is: the built-in routines are there.
        assertEquals(MonkConfig().normalized(0L), store.config.value)
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

class TimeRuleTest {
    private val insta = BlockedApp("com.instagram.android", "Instagram", BlockMode.DELAY)
    private fun cfg(vararg rules: TimeRule) = MonkConfig(apps = listOf(insta.copy(rules = rules.toList())))
    private fun decide(c: MonkConfig, day: Int, minute: Int, allow: Map<String, Long> = emptyMap(), opens: Int = 0) =
        BlockPolicy.decide(c, insta.packageName, 1_000_000L, day, minute, allow, opens)

    @Test
    fun blockWindowTurnsPauseIntoBlock() {
        val morning = TimeRule(1, RuleMode.BLOCK, startMinute = 6 * 60, endMinute = 9 * 60)
        val inside = decide(cfg(morning), day = 2, minute = 7 * 60)
        assertIs<Decision.Intercept>(inside)
        assertEquals(BlockMode.BLOCK, inside.effectiveMode)
        assertEquals(morning, inside.rule)
        val outside = decide(cfg(morning), day = 2, minute = 12 * 60)
        assertIs<Decision.Intercept>(outside)
        assertEquals(BlockMode.DELAY, outside.effectiveMode)
    }

    @Test
    fun blockWindowBeatsAnExistingAllowance() {
        val morning = TimeRule(1, RuleMode.BLOCK, startMinute = 6 * 60, endMinute = 9 * 60)
        val d = decide(cfg(morning), day = 2, minute = 6 * 60, allow = mapOf(insta.packageName to 9_000_000L))
        assertIs<Decision.Intercept>(d)
        assertEquals(BlockMode.BLOCK, d.effectiveMode)
    }

    @Test
    fun crossMidnightRuleCountsOnTheStartDay() {
        val night = TimeRule(2, RuleMode.BLOCK, days = setOf(5), startMinute = 22 * 60, endMinute = 0)
        assertTrue(night.crossesMidnight)
        assertIs<Decision.Intercept>(decide(cfg(night), day = 5, minute = 23 * 60).also { assertEquals(BlockMode.BLOCK, (it as Decision.Intercept).effectiveMode) })
        // Saturday 00:30 still belongs to Friday's window? endMinute = 0 means "until midnight" exactly.
        assertEquals(BlockMode.DELAY, (decide(cfg(night), day = 6, minute = 30) as Decision.Intercept).effectiveMode)
        val late = TimeRule(3, RuleMode.BLOCK, days = setOf(5), startMinute = 22 * 60, endMinute = 2 * 60)
        assertEquals(BlockMode.BLOCK, (decide(cfg(late), day = 6, minute = 60) as Decision.Intercept).effectiveMode)
        assertEquals(BlockMode.DELAY, (decide(cfg(late), day = 7, minute = 60) as Decision.Intercept).effectiveMode)
    }

    @Test
    fun freeWindowSkipsThePauseButNotTheLimit() {
        val free = TimeRule(4, RuleMode.FREE, startMinute = 20 * 60, endMinute = 21 * 60)
        assertEquals(Decision.Allow, decide(cfg(free), day = 3, minute = 20 * 60 + 30))
        val limited = cfg(free).let { c -> c.copy(apps = c.apps.map { it.copy(dailyLimit = 1) }) }
        val d = decide(limited, day = 3, minute = 20 * 60 + 30, opens = 1)
        assertIs<Decision.Intercept>(d)
        assertTrue(d.limitReached)
    }

    @Test
    fun overlappingRulesResolveToTheStrictest() {
        val free = TimeRule(5, RuleMode.FREE, startMinute = 0, endMinute = 0)
        val block = TimeRule(6, RuleMode.BLOCK, startMinute = 9 * 60, endMinute = 10 * 60)
        assertEquals(BlockMode.BLOCK, (decide(cfg(free, block), day = 1, minute = 9 * 60 + 30) as Decision.Intercept).effectiveMode)
        assertEquals(Decision.Allow, decide(cfg(free, block), day = 1, minute = 11 * 60))
    }

    @Test
    fun nextChangeFindsTheNearestBoundary() {
        val days = setOf(1, 2, 3, 4, 5, 6, 7)
        assertEquals(30, TimeWindow.minutesToNextChange(days, 6 * 60, 9 * 60, dayIso = 2, minuteOfDay = 5 * 60 + 30))
        assertEquals(60, TimeWindow.minutesToNextChange(days, 6 * 60, 9 * 60, dayIso = 2, minuteOfDay = 8 * 60))
        // Cross-midnight: at 23:00 the 22:00–02:00 window closes in 3 h.
        assertEquals(180, TimeWindow.minutesToNextChange(days, 22 * 60, 2 * 60, dayIso = 2, minuteOfDay = 23 * 60))
        // Friday-only window seen from Wednesday noon opens in 2 days + 10 h.
        assertEquals(2 * 24 * 60 + 10 * 60, TimeWindow.minutesToNextChange(setOf(5), 22 * 60, 2 * 60, dayIso = 3, minuteOfDay = 12 * 60))
        assertNull(TimeWindow.minutesToNextChange(days, 0, 0, dayIso = 1, minuteOfDay = 0))
    }

    @Test
    fun removedAppSettingsComeBackOnReAdd() {
        val store = MonkStore(InMemoryStore())
        store.addApp(insta.packageName, "Instagram")
        store.upsertApp(store.config.value.app(insta.packageName)!!.copy(mode = BlockMode.BLOCK, dailyLimit = 2, locked = true))
        store.upsertRule(insta.packageName, TimeRule(9, RuleMode.BLOCK, setOf(1), 6 * 60, 9 * 60))
        store.removeApp(insta.packageName)
        assertNull(store.config.value.app(insta.packageName))
        assertEquals(1, store.config.value.archivedApps.size)
        store.addApp(insta.packageName, "Instagram (new label)")
        val back = store.config.value.app(insta.packageName)!!
        assertEquals(BlockMode.BLOCK, back.mode)
        assertEquals(2, back.dailyLimit)
        assertEquals(1, back.rules.size)
        assertEquals("Instagram (new label)", back.label)
        assertFalse(back.locked)
        assertEquals(0, store.config.value.archivedApps.size)
    }

    @Test
    fun uninstallParksAndReinstallRestores() {
        val store = MonkStore(InMemoryStore())
        store.addApp(insta.packageName, "Instagram")
        store.upsertRule(insta.packageName, TimeRule(10, RuleMode.PAUSE, setOf(2), 0, 60))
        assertTrue(store.archiveIfWatched(insta.packageName))
        assertFalse(store.archiveIfWatched(insta.packageName))
        assertNull(store.config.value.app(insta.packageName))
        assertTrue(store.restoreIfArchived(insta.packageName))
        assertEquals(1, store.config.value.app(insta.packageName)!!.rules.size)
        assertFalse(store.restoreIfArchived("com.never.seen"))
    }

    @Test
    fun lockedAppIgnoresMasterSwitchAndBreaks() {
        val locked = MonkConfig(apps = listOf(insta.copy(locked = true)), enabled = false)
        assertIs<Decision.Intercept>(decide(locked, day = 2, minute = 12 * 60))
        val onBreak = MonkConfig(apps = listOf(insta.copy(locked = true)), pausedUntil = 9_000_000L)
        assertIs<Decision.Intercept>(decide(onBreak, day = 2, minute = 12 * 60))
        val plain = MonkConfig(apps = listOf(insta), enabled = false)
        assertEquals(Decision.Allow, decide(plain, day = 2, minute = 12 * 60))
    }

    @Test
    fun breakEndCountsAsAVerdictChange() {
        val c = MonkConfig(apps = listOf(insta), pausedUntil = 1_000_000L + 5 * 60_000L)
        assertEquals(5 * 60_000L, BlockPolicy.millisToNextChange(c, insta, 2, 12 * 60, 0, nowMillis = 1_000_000L))
        assertNull(BlockPolicy.millisToNextChange(c, insta, 2, 12 * 60, 0, nowMillis = 2_000_000L))
    }

    @Test
    fun blockEndIsTheEndOfBlockCoverageNotTheNearestBoundary() {
        val block = TimeRule(1, RuleMode.BLOCK, startMinute = 9 * 60, endMinute = 12 * 60)
        val free = TimeRule(2, RuleMode.FREE, startMinute = 10 * 60, endMinute = 11 * 60)
        val app = insta.copy(rules = listOf(block, free))
        fun endFor(a: com.mdportnov.monk.shared.model.BlockedApp, minute: Int, cfg: MonkConfig = MonkConfig(apps = listOf(a))) =
            BlockPolicy.blockEndsInMinutes(cfg, a, 2, minute)
        assertEquals(150, endFor(app, 9 * 60 + 30))
        val chained = insta.copy(rules = listOf(block, TimeRule(3, RuleMode.BLOCK, startMinute = 12 * 60, endMinute = 13 * 60)))
        assertEquals(210, endFor(chained, 9 * 60 + 30))
        assertNull(endFor(app, 14 * 60))
    }

    @Test
    fun rulesRoundTripThroughTheStore() {
        val kv = InMemoryStore()
        val store = MonkStore(kv)
        store.upsertApp(insta)
        store.upsertRule(insta.packageName, TimeRule(7, RuleMode.BLOCK, setOf(1, 3), 6 * 60, 9 * 60))
        store.upsertApp(store.config.value.app(insta.packageName)!!.copy(locked = true))
        val again = MonkStore(kv).config.value.app(insta.packageName)!!
        assertEquals(1, again.rules.size)
        assertTrue(again.locked)
        store.removeRule(insta.packageName, 7)
        assertEquals(0, store.config.value.app(insta.packageName)!!.rules.size)
    }
}
