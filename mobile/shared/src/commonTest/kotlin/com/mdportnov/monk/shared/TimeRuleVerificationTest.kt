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
import com.mdportnov.monk.shared.model.RuleMode
import com.mdportnov.monk.shared.model.Schedule
import com.mdportnov.monk.shared.model.TimeRule
import com.mdportnov.monk.shared.model.TimeWindow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adversarial checks of the per-app time-rule arithmetic. The weekly model used as the oracle
 * below is written independently of [TimeWindow]: a window is the set of weekly minutes
 * [s*1440 + start, s*1440 + start + len) for every selected weekday s, with len = 1440 when
 * start == end. Any disagreement between the two is a bug in one of them.
 */
class TimeRuleVerificationTest {
    private val all = setOf(1, 2, 3, 4, 5, 6, 7)
    private val insta = BlockedApp("com.instagram.android", "Instagram", BlockMode.DELAY)

    private fun cfg(vararg rules: TimeRule, app: BlockedApp = insta, schedule: Schedule = Schedule()) =
        MonkConfig(apps = listOf(app.copy(rules = rules.toList())), schedule = schedule)

    private fun decide(c: MonkConfig, day: Int, minute: Int, allow: Map<String, Long> = emptyMap(), opens: Int = 0, now: Long = 1_000_000L) =
        BlockPolicy.decide(c, insta.packageName, now, day, minute, allow, opens)

    private fun mode(d: Decision) = (d as Decision.Intercept).effectiveMode

    private fun h(hours: Int, minutes: Int = 0) = hours * 60 + minutes

    // --- isActive edge cases ---

    @Test
    fun windowEndingAtMidnightStopsExactlyAtMidnight() {
        val r = TimeRule(1, RuleMode.BLOCK, all, h(22), 0)
        assertTrue(r.crossesMidnight)
        assertTrue(r.isActive(3, h(22)))
        assertTrue(r.isActive(3, h(23, 59)))
        assertFalse(r.isActive(4, 0))
        assertFalse(r.isActive(3, 0))
        assertFalse(r.isActive(3, h(21, 59)))
        // Day-restricted: Wednesday only. Thursday 00:00 is not covered, Wednesday 22:00 is.
        val wed = r.copy(days = setOf(3))
        assertTrue(wed.isActive(3, h(23, 59)))
        assertFalse(wed.isActive(4, 0))
        assertFalse(wed.isActive(2, h(23)))
    }

    @Test
    fun windowStartingAtMidnightBelongsToThatDay() {
        val r = TimeRule(1, RuleMode.BLOCK, setOf(4), 0, h(6))
        assertFalse(r.crossesMidnight)
        assertTrue(r.isActive(4, 0))
        assertTrue(r.isActive(4, h(5, 59)))
        assertFalse(r.isActive(4, h(6)))
        assertFalse(r.isActive(3, h(23, 59)))
        assertFalse(r.isActive(5, 0))
    }

    @Test
    fun oneMinuteWindowIsExactlyOneMinute() {
        val r = TimeRule(1, RuleMode.BLOCK, all, h(9), h(9, 1))
        assertFalse(r.isActive(1, h(8, 59)))
        assertTrue(r.isActive(1, h(9)))
        assertFalse(r.isActive(1, h(9, 1)))
        assertEquals(1, TimeWindow.minutesToNextChange(all, h(9), h(9, 1), 1, h(8, 59)))
        assertEquals(1, TimeWindow.minutesToNextChange(all, h(9), h(9, 1), 1, h(9)))
        assertEquals(TimeWindow.DAY - 1, TimeWindow.minutesToNextChange(all, h(9), h(9, 1), 1, h(9, 1)))
        // One minute across midnight: 23:59–00:00.
        val last = TimeRule(2, RuleMode.BLOCK, all, h(23, 59), 0)
        assertTrue(last.isActive(1, h(23, 59)))
        assertFalse(last.isActive(2, 0))
        assertFalse(last.isActive(1, h(23, 58)))
    }

    @Test
    fun backToBackWindowsAreSeamless() {
        val morning = TimeRule(1, RuleMode.BLOCK, all, h(9), h(12))
        val afternoon = TimeRule(2, RuleMode.PAUSE, all, h(12), h(15))
        val c = cfg(morning, afternoon)
        assertEquals(morning, (decide(c, 3, h(11, 59)) as Decision.Intercept).rule)
        assertEquals(afternoon, (decide(c, 3, h(12)) as Decision.Intercept).rule)
        assertEquals(BlockMode.BLOCK, mode(decide(c, 3, h(11, 59))))
        assertEquals(BlockMode.DELAY, mode(decide(c, 3, h(12))))
        assertEquals(afternoon, (decide(c, 3, h(14, 59)) as Decision.Intercept).rule)
        assertNull((decide(c, 3, h(15)) as Decision.Intercept).rule)
        // No minute is uncovered between 09:00 and 15:00.
        for (m in h(9) until h(15)) assertTrue(c.apps.single().activeRule(3, m) != null, "gap at $m")
        // Back-to-back across midnight: 20:00–00:00 then 00:00–02:00.
        val eve = TimeRule(3, RuleMode.BLOCK, all, h(20), 0)
        val night = TimeRule(4, RuleMode.BLOCK, all, 0, h(2))
        val app = cfg(eve, night).apps.single()
        assertEquals(eve, app.activeRule(3, h(23, 59)))
        assertEquals(night, app.activeRule(4, 0))
        assertNull(app.activeRule(4, h(2)))
    }

    @Test
    fun overlappingBlockAndFreeResolvesToBlockPauseAndFreeToPause() {
        val free = TimeRule(1, RuleMode.FREE, all, h(8), h(20))
        val block = TimeRule(2, RuleMode.BLOCK, all, h(12), h(13))
        val pause = TimeRule(3, RuleMode.PAUSE, all, h(15), h(16))
        val c = cfg(free, block, pause)
        assertEquals(BlockMode.BLOCK, mode(decide(c, 2, h(12, 30))))
        assertEquals(block, (decide(c, 2, h(12, 30)) as Decision.Intercept).rule)
        val p = decide(c, 2, h(15, 30))
        assertIs<Decision.Intercept>(p)
        assertEquals(pause, p.rule)
        assertEquals(BlockMode.DELAY, p.effectiveMode)
        assertEquals(Decision.Allow, decide(c, 2, h(10)))
        assertEquals(Decision.Allow, decide(c, 2, h(13)))
        assertEquals(Decision.Allow, decide(c, 2, h(16)))
        // All three at once: BLOCK wins regardless of list order.
        val stack = cfg(pause.copy(startMinute = h(12), endMinute = h(13)), free, block)
        assertEquals(block, stack.apps.single().activeRule(2, h(12, 30)))
    }

    @Test
    fun crossMidnightRuleOnSundayCoversMondayEarlyHoursOnly() {
        val sunday = TimeRule(1, RuleMode.BLOCK, setOf(7), h(23), h(1))
        assertTrue(sunday.isActive(7, h(23)))
        assertTrue(sunday.isActive(7, h(23, 59)))
        assertTrue(sunday.isActive(1, 0))
        assertTrue(sunday.isActive(1, 30))
        assertFalse(sunday.isActive(1, h(1)))
        assertFalse(sunday.isActive(7, 30)) // Saturday night is not selected
        assertFalse(sunday.isActive(6, h(23, 30)))
        // The same window on Monday does not cover Monday 00:30 (that belongs to Sunday's start).
        val monday = sunday.copy(days = setOf(1))
        assertFalse(monday.isActive(1, 30))
        assertTrue(monday.isActive(1, h(23, 30)))
        assertTrue(monday.isActive(2, 30))
        // Decision at Monday 00:30 with the Sunday rule.
        assertEquals(BlockMode.BLOCK, mode(decide(cfg(sunday), 1, 30)))
        assertEquals(BlockMode.DELAY, mode(decide(cfg(sunday), 1, h(1))))
    }

    @Test
    fun wholeDayRuleOnSpecificDays() {
        val weekend = TimeRule(1, RuleMode.BLOCK, setOf(6, 7), h(9), h(9))
        assertTrue(weekend.crossesMidnight)
        assertTrue(weekend.isActive(6, 0))
        assertTrue(weekend.isActive(6, h(8, 59)))
        assertTrue(weekend.isActive(6, h(9)))
        assertTrue(weekend.isActive(7, h(23, 59)))
        assertFalse(weekend.isActive(5, h(23, 59)))
        assertFalse(weekend.isActive(1, 0))
        val zero = weekend.copy(startMinute = 0, endMinute = 0)
        for (m in listOf(0, 1, h(12), h(23, 59))) {
            assertTrue(zero.isActive(6, m)); assertTrue(zero.isActive(7, m))
            assertFalse(zero.isActive(5, m)); assertFalse(zero.isActive(1, m))
        }
    }

    // --- minutesToNextChange at every boundary type ---

    @Test
    fun nextChangeWhenEnteringAndLeavingAndAtTheEdges() {
        val s = h(6); val e = h(9)
        assertEquals(30, TimeWindow.minutesToNextChange(all, s, e, 2, h(5, 30)))
        assertEquals(180, TimeWindow.minutesToNextChange(all, s, e, 2, h(6)))
        assertEquals(1, TimeWindow.minutesToNextChange(all, s, e, 2, h(8, 59)))
        assertEquals(21 * 60, TimeWindow.minutesToNextChange(all, s, e, 2, h(9)))
        assertEquals(6 * 60, TimeWindow.minutesToNextChange(all, s, e, 2, 0))
    }

    @Test
    fun nextChangeAcrossMidnightWrap() {
        assertEquals(60, TimeWindow.minutesToNextChange(all, h(22), h(2), 2, h(21)))
        assertEquals(180, TimeWindow.minutesToNextChange(all, h(22), h(2), 2, h(23)))
        assertEquals(120, TimeWindow.minutesToNextChange(all, h(22), h(2), 3, 0))
        assertEquals(1, TimeWindow.minutesToNextChange(all, h(22), h(2), 3, h(1, 59)))
        assertEquals(20 * 60, TimeWindow.minutesToNextChange(all, h(22), h(2), 3, h(2)))
        // end = 0: closes at midnight, reopens 22:00.
        assertEquals(30, TimeWindow.minutesToNextChange(all, h(22), 0, 2, h(23, 30)))
        assertEquals(22 * 60, TimeWindow.minutesToNextChange(all, h(22), 0, 3, 0))
        // start = 0: opens at midnight.
        assertEquals(1, TimeWindow.minutesToNextChange(all, 0, h(6), 2, h(23, 59)))
        assertEquals(h(6), TimeWindow.minutesToNextChange(all, 0, h(6), 3, 0))
    }

    @Test
    fun nextChangeAcrossWeekWrap() {
        val sun = setOf(7)
        // Sunday 23:00–01:00 seen from Monday 00:30: closes in 30 min.
        assertEquals(30, TimeWindow.minutesToNextChange(sun, h(23), h(1), 1, 30))
        // Just closed Monday 01:00: reopens next Sunday 23:00.
        assertEquals(6 * TimeWindow.DAY + 22 * 60, TimeWindow.minutesToNextChange(sun, h(23), h(1), 1, h(1)))
        // Sunday 22:00: opens in an hour.
        assertEquals(60, TimeWindow.minutesToNextChange(sun, h(23), h(1), 7, h(22)))
        // Whole-day Monday rule: from Monday noon it ends at midnight; from Tuesday 00:00 it is six days away.
        assertEquals(12 * 60, TimeWindow.minutesToNextChange(setOf(1), h(9), h(9), 1, h(12)))
        assertEquals(6 * TimeWindow.DAY, TimeWindow.minutesToNextChange(setOf(1), h(9), h(9), 2, 0))
        assertEquals(6 * TimeWindow.DAY, TimeWindow.minutesToNextChange(setOf(1), 0, 0, 2, 0))
        // Sunday-only whole day, seen from Sunday 23:59: ends in one minute (week boundary).
        assertEquals(1, TimeWindow.minutesToNextChange(sun, 0, 0, 7, h(23, 59)))
        // Monday-only whole day, seen from Sunday 23:59: starts in one minute (week boundary).
        assertEquals(1, TimeWindow.minutesToNextChange(setOf(1), 0, 0, 7, h(23, 59)))
    }

    @Test
    fun nextChangeNoChangeCases() {
        assertNull(TimeWindow.minutesToNextChange(all, 0, 0, 1, 0))
        assertNull(TimeWindow.minutesToNextChange(all, h(9), h(9), 4, h(15)))
        assertNull(TimeWindow.minutesToNextChange(emptySet(), h(9), h(12), 4, h(15)))
        assertNull(TimeWindow.minutesToNextChange(emptySet(), 0, 0, 4, h(15)))
        assertNull(TimeWindow.minutesToNextChange(emptySet(), h(22), h(2), 4, h(15)))
    }

    @Test
    fun nextChangeDayRestrictedSeenFromNonSelectedDay() {
        val fri = setOf(5)
        // Wednesday noon → Friday 09:00.
        assertEquals(2 * TimeWindow.DAY - 3 * 60, TimeWindow.minutesToNextChange(fri, h(9), h(12), 3, h(12)))
        // Friday 12:00 (just closed) → next Friday 09:00.
        assertEquals(7 * TimeWindow.DAY - 3 * 60, TimeWindow.minutesToNextChange(fri, h(9), h(12), 5, h(12)))
        // Saturday 00:00 → next Friday 09:00.
        assertEquals(6 * TimeWindow.DAY + 9 * 60, TimeWindow.minutesToNextChange(fri, h(9), h(12), 6, 0))
        // Friday 22:00–02:00 seen from Saturday 03:00: a whole week minus an hour.
        assertEquals(6 * TimeWindow.DAY + 19 * 60, TimeWindow.minutesToNextChange(fri, h(22), h(2), 6, h(3)))
        // Same window from Thursday 23:00: Friday 22:00 is 23 h away, not "tonight".
        assertEquals(23 * 60, TimeWindow.minutesToNextChange(fri, h(22), h(2), 4, h(23)))
        // Cross-midnight day-restricted window seen from the following night at the same clock: not active.
        assertFalse(TimeWindow.isActive(fri, h(22), h(2), 7, h(1)))
        assertEquals(5 * TimeWindow.DAY + 21 * 60, TimeWindow.minutesToNextChange(fri, h(22), h(2), 7, h(1)))
    }

    @Test
    fun millisToNextChangeTakesNearestOfRulesAndScheduleAndSubtractsSeconds() {
        val a = TimeRule(1, RuleMode.BLOCK, all, h(14), h(15))
        val b = TimeRule(2, RuleMode.FREE, setOf(3), h(12, 45), h(13))
        val sched = Schedule(enabled = true, days = all, startMinute = h(9), endMinute = h(18))
        val app = cfg(a, b).apps.single()
        // Wednesday 12:30:15 → FREE opens at 12:45 (15 min) before BLOCK at 14:00 and schedule at 18:00.
        assertEquals(15 * 60_000L - 15_000L, BlockPolicy.millisToNextChange(cfg(a, b, schedule = sched), app, 3, h(12, 30), 15))
        // Thursday 12:30: b is not selected today; next is a at 14:00 (90 min).
        assertEquals(90 * 60_000L, BlockPolicy.millisToNextChange(cfg(a, b, schedule = sched), app, 4, h(12, 30), 0))
        // Thursday 17:30: the schedule closes at 18:00 before a's next boundary tomorrow.
        assertEquals(30 * 60_000L, BlockPolicy.millisToNextChange(cfg(a, b, schedule = sched), app, 4, h(17, 30), 0))
        // Disabled schedule is ignored even if its window would be nearer.
        val off = sched.copy(enabled = false, startMinute = h(17, 40))
        assertEquals(90 * 60_000L, BlockPolicy.millisToNextChange(cfg(a, b, schedule = off), app, 4, h(12, 30), 0))
        // No rules, no schedule: nothing time-bound.
        assertNull(BlockPolicy.millisToNextChange(cfg(), cfg().apps.single(), 4, h(12, 30), 0))
        // Only an all-week whole-day rule: nothing time-bound either.
        val always = TimeRule(3, RuleMode.BLOCK, all, 0, 0)
        assertNull(BlockPolicy.millisToNextChange(cfg(always), cfg(always).apps.single(), 4, h(12, 30), 0))
        // Whole-day rule on one weekday: ends at midnight; with the schedule, nearest of both.
        val thursday = cfg(always.copy(days = setOf(4)), schedule = sched)
        assertEquals(5 * 60 * 60_000L, BlockPolicy.millisToNextChange(thursday, thursday.apps.single(), 4, h(13), 0))
        val thursdayNoSched = cfg(always.copy(days = setOf(4)))
        assertEquals(11 * 60 * 60_000L, BlockPolicy.millisToNextChange(thursdayNoSched, thursdayNoSched.apps.single(), 4, h(13), 0))
    }

    // --- decision precedence ---

    @Test
    fun blockRuleBeatsAllowanceAndLimitButFreeDoesNotBeatLimit() {
        val block = TimeRule(1, RuleMode.BLOCK, all, h(9), h(12))
        val free = TimeRule(2, RuleMode.FREE, all, h(13), h(14))
        val pause = TimeRule(3, RuleMode.PAUSE, all, h(15), h(16))
        val limited = cfg(block, free, pause, app = insta.copy(dailyLimit = 1))
        val allow = mapOf(insta.packageName to 9_000_000L)
        // BLOCK window: allowance ignored, limit irrelevant, rule attached.
        val b = decide(limited, 2, h(10), allow = allow, opens = 5)
        assertIs<Decision.Intercept>(b)
        assertEquals(BlockMode.BLOCK, b.effectiveMode)
        assertFalse(b.limitReached)
        assertEquals(block, b.rule)
        // FREE window: allowance → Allow; no allowance but under limit → Allow; limit reached → BLOCK.
        assertEquals(Decision.Allow, decide(limited, 2, h(13, 30), allow = allow, opens = 5))
        assertEquals(Decision.Allow, decide(limited, 2, h(13, 30), opens = 0))
        val f = decide(limited, 2, h(13, 30), opens = 1)
        assertIs<Decision.Intercept>(f)
        assertTrue(f.limitReached)
        assertEquals(BlockMode.BLOCK, f.effectiveMode)
        assertEquals(free, f.rule)
        // PAUSE window: allowance → Allow; limit reached → BLOCK; otherwise DELAY.
        assertEquals(Decision.Allow, decide(limited, 2, h(15, 30), allow = allow, opens = 5))
        assertEquals(BlockMode.BLOCK, mode(decide(limited, 2, h(15, 30), opens = 1)))
        assertEquals(BlockMode.DELAY, mode(decide(limited, 2, h(15, 30), opens = 0)))
        // Expired allowance does not count.
        assertIs<Decision.Intercept>(decide(limited, 2, h(15, 30), allow = mapOf(insta.packageName to 1_000_000L)))
    }

    @Test
    fun ruleOverridesAppModeInBothDirections() {
        val strictApp = insta.copy(mode = BlockMode.BLOCK)
        val pause = TimeRule(1, RuleMode.PAUSE, all, h(9), h(12))
        val free = TimeRule(2, RuleMode.FREE, all, h(13), h(14))
        val c = cfg(pause, free, app = strictApp)
        assertEquals(BlockMode.BLOCK, mode(decide(c, 2, h(8))))
        assertEquals(BlockMode.DELAY, mode(decide(c, 2, h(10))))
        assertEquals(Decision.Allow, decide(c, 2, h(13, 30)))
        // A BLOCK rule on a DELAY app blocks; outside the window the app is back to DELAY.
        val block = TimeRule(3, RuleMode.BLOCK, all, h(9), h(12))
        assertEquals(BlockMode.BLOCK, mode(decide(cfg(block), 2, h(10))))
        assertEquals(BlockMode.DELAY, mode(decide(cfg(block), 2, h(12))))
    }

    @Test
    fun scheduleInactiveWinsOverBlockRuleButARunningRoutineComesFirst() {
        val block = TimeRule(1, RuleMode.BLOCK, all, 0, 0)
        val weekdays = Schedule(enabled = true, days = setOf(1, 2, 3, 4, 5), startMinute = h(9), endMinute = h(18))
        val c = cfg(block, schedule = weekdays)
        assertEquals(BlockMode.BLOCK, mode(decide(c, 3, h(10))))
        assertEquals(Decision.Allow, decide(c, 3, h(20)))
        assertEquals(Decision.Allow, decide(c, 6, h(10)))
        // A running routine is judged before the schedule: blocked on Saturday too.
        val focus = BuiltInRoutines.factory(BuiltInRoutines.FOCUS)!!
        val focused = c.copy(routines = listOf(focus), run = RoutineRun(focus.id, 0L, 5_000_000L))
        val f = decide(focused, 6, h(10))
        assertIs<Decision.Intercept>(f)
        assertEquals(BuiltInRoutines.FOCUS, f.routine?.id)
        assertNull(f.rule)
        // Paused / disabled protection lets the app through even inside a BLOCK window.
        assertEquals(Decision.Allow, decide(c.copy(pausedUntil = 2_000_000L), 3, h(10)))
        assertEquals(Decision.Allow, decide(c.copy(enabled = false), 3, h(10)))
        // Schedule crossing midnight with a rule inside: Friday 23:30 is in schedule 22:00–07:00 (Fri) → rule applies.
        val night = Schedule(enabled = true, days = setOf(5), startMinute = h(22), endMinute = h(7))
        assertEquals(BlockMode.BLOCK, mode(decide(cfg(block, schedule = night), 6, h(3))))
        assertEquals(Decision.Allow, decide(cfg(block, schedule = night), 6, h(8)))
    }

    @Test
    fun ruleOnAnotherAppDoesNotLeak() {
        val other = BlockedApp("com.other", "Other", rules = listOf(TimeRule(1, RuleMode.BLOCK, all, 0, 0)))
        val c = MonkConfig(apps = listOf(insta, other))
        assertEquals(BlockMode.DELAY, mode(decide(c, 3, h(10))))
        assertEquals(BlockMode.BLOCK, (BlockPolicy.decide(c, "com.other", 1L, 3, h(10), emptyMap()) as Decision.Intercept).effectiveMode)
    }

    // --- store ---

    @Test
    fun upsertRuleReplacesById() {
        val store = MonkStore(InMemoryStore())
        store.upsertApp(insta)
        store.upsertRule(insta.packageName, TimeRule(1, RuleMode.BLOCK, setOf(1), h(6), h(9)))
        store.upsertRule(insta.packageName, TimeRule(2, RuleMode.FREE, setOf(2), h(6), h(9)))
        store.upsertRule(insta.packageName, TimeRule(1, RuleMode.PAUSE, setOf(3), h(7), h(8)))
        val rules = store.config.value.app(insta.packageName)!!.rules
        assertEquals(2, rules.size)
        assertEquals(RuleMode.PAUSE, rules.single { it.id == 1L }.mode)
        assertEquals(setOf(3), rules.single { it.id == 1L }.days)
        // Unknown package: nothing changes, nothing is created.
        store.upsertRule("com.nobody", TimeRule(5, RuleMode.BLOCK, all, 0, 0))
        assertEquals(1, store.config.value.apps.size)
        store.removeRule(insta.packageName, 99)
        assertEquals(2, store.config.value.app(insta.packageName)!!.rules.size)
    }

    @Test
    fun rulesSurviveArchiveRoundTripThroughPersistence() {
        val kv = InMemoryStore()
        val store = MonkStore(kv)
        store.addApp(insta.packageName, "Instagram")
        store.upsertRule(insta.packageName, TimeRule(1, RuleMode.BLOCK, setOf(7), h(23), h(1)))
        store.removeApp(insta.packageName)
        val reloaded = MonkStore(kv)
        assertNull(reloaded.config.value.app(insta.packageName))
        assertTrue(reloaded.restoreIfArchived(insta.packageName))
        val rule = reloaded.config.value.app(insta.packageName)!!.rules.single()
        assertEquals(TimeRule(1, RuleMode.BLOCK, setOf(7), h(23), h(1)), rule)
    }

    // --- brute-force oracle ---

    private class Weekly(val days: Set<Int>, val start: Int, val end: Int) {
        private val len = if (start == end) TimeWindow.DAY else (end - start + TimeWindow.DAY) % TimeWindow.DAY
        private val week = 7 * TimeWindow.DAY
        fun active(weekMinute: Int): Boolean = days.any { d ->
            // start == end is the whole calendar day, not 24 h counted from `start`.
            val from = (d - 1) * TimeWindow.DAY + (if (start == end) 0 else start)
            ((weekMinute - from) % week + week) % week < len
        }
        fun nextChange(weekMinute: Int): Int? {
            val now = active(weekMinute)
            for (k in 1..week) if (active(weekMinute + k) != now) return k
            return null
        }
    }

    private fun check(days: Set<Int>, start: Int, end: Int, day: Int, minute: Int) {
        val ref = Weekly(days, start, end)
        val w = (day - 1) * TimeWindow.DAY + minute
        val label = "days=$days $start-$end at day=$day minute=$minute"
        assertEquals(ref.active(w), TimeWindow.isActive(days, start, end, day, minute), "isActive $label")
        assertEquals(ref.nextChange(w), TimeWindow.minutesToNextChange(days, start, end, day, minute), "nextChange $label")
    }

    @Test
    fun exhaustiveBoundaryGridAgreesWithReferenceModel() {
        val daySets = listOf(all, setOf(1), setOf(7), setOf(5), setOf(6, 7), setOf(1, 3, 5), setOf(2, 3, 4, 5, 6), emptySet())
        val edges = listOf(0, 1, h(6), h(9), h(12), h(22), h(23, 59))
        for (days in daySets) for (s in edges) for (e in edges) for (day in 1..7) {
            val probes = buildSet {
                for (b in listOf(s, e, 0)) for (d in listOf(-1, 0, 1)) add(((b + d) % TimeWindow.DAY + TimeWindow.DAY) % TimeWindow.DAY)
                add(h(12)); add(h(23, 59))
            }
            for (m in probes) check(days, s, e, day, m)
        }
    }

    @Test
    fun randomizedWindowsAgreeWithReferenceModel() {
        val rnd = Random(20260914)
        repeat(4000) {
            val days = (1..7).filter { rnd.nextInt(3) != 0 }.toSet()
            val start = rnd.nextInt(TimeWindow.DAY)
            val end = when (rnd.nextInt(4)) { 0 -> start; 1 -> 0; else -> rnd.nextInt(TimeWindow.DAY) }
            check(days, start, end, rnd.nextInt(1, 8), rnd.nextInt(TimeWindow.DAY))
        }
    }

    @Test
    fun activeRuleAgreesWithReferenceAcrossOverlappingRules() {
        val rnd = Random(7)
        repeat(500) {
            val rules = List(rnd.nextInt(1, 5)) { i ->
                val s = rnd.nextInt(TimeWindow.DAY)
                TimeRule(i.toLong(), RuleMode.entries[rnd.nextInt(3)], (1..7).filter { rnd.nextBoolean() }.toSet(), s, if (rnd.nextInt(5) == 0) s else rnd.nextInt(TimeWindow.DAY))
            }
            val app = insta.copy(rules = rules)
            val day = rnd.nextInt(1, 8); val minute = rnd.nextInt(TimeWindow.DAY)
            val w = (day - 1) * TimeWindow.DAY + minute
            val expected = rules.filter { Weekly(it.days, it.startMinute, it.endMinute).active(w) }.minByOrNull { it.mode.ordinal }
            assertEquals(expected?.mode, app.activeRule(day, minute)?.mode, "rules=$rules day=$day minute=$minute")
        }
    }
}
