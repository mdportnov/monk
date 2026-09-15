package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.data.InMemoryStore
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.clockAfterWallMinutes
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.BlockPolicy
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.BuiltInRoutines
import com.mdportnov.monk.shared.model.Decision
import com.mdportnov.monk.shared.model.Emoji
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.ProtectionState
import com.mdportnov.monk.shared.model.Routine
import com.mdportnov.monk.shared.model.RoutineMode
import com.mdportnov.monk.shared.model.RoutineRun
import com.mdportnov.monk.shared.model.RoutineWindow
import com.mdportnov.monk.shared.model.RuleMode
import com.mdportnov.monk.shared.model.everAppliesUnder
import com.mdportnov.monk.shared.model.Schedule
import com.mdportnov.monk.shared.model.TimeRule
import com.mdportnov.monk.shared.model.TimeWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Routines are a second layer over each app's own setting, and the contract is one sentence: the
 * stricter of the two wins, and a routine may only ever tighten. Everything here is a way of
 * asking whether that sentence still holds — including on the days it would be inconvenient.
 */
class RoutineTest {
    private val insta = BlockedApp("com.instagram.android", "Instagram")
    private val mail = BlockedApp("com.mail", "Mail")
    private val base = MonkConfig(apps = listOf(insta, mail))
    private val now = 10_000_000L
    private val min = 60_000L

    /** Every day 22:00–07:00, blocking, every app. */
    private fun evening(
        mode: RoutineMode = RoutineMode.BLOCK,
        allApps: Boolean = true,
        packages: Set<String> = emptySet(),
        ignoresBreaks: Boolean = false,
        enabled: Boolean = true,
    ) = Routine(
        id = "custom:evening",
        emoji = "🌙",
        name = "Evening",
        mode = mode,
        allApps = allApps,
        packages = packages,
        windows = listOf(RoutineWindow(id = 1, startMinute = 22 * 60, endMinute = 7 * 60)),
        enabled = enabled,
        ignoresBreaks = ignoresBreaks,
    )

    private fun cfg(vararg routines: Routine, apps: List<BlockedApp> = base.apps) =
        base.copy(apps = apps, routines = routines.toList())

    private fun decide(
        c: MonkConfig,
        pkg: String = insta.packageName,
        day: Int = 3,
        minute: Int = 23 * 60,
        allow: Map<String, Long> = emptyMap(),
        opens: Int = 0,
        at: Long = now,
    ) = BlockPolicy.decide(c, pkg, at, day, minute, allow, opens)

    // --- the merge ------------------------------------------------------------------

    @Test
    fun aBlockingRoutineTurnsAPauseIntoABlockInsideItsHours() {
        val c = cfg(evening())
        val inside = decide(c, minute = 23 * 60)
        assertIs<Decision.Intercept>(inside)
        assertEquals(BlockMode.BLOCK, inside.effectiveMode)
        assertEquals("custom:evening", inside.routine?.id)
        // An hour outside the window and the app is back to its own pause, with nobody to blame.
        val outside = decide(c, minute = 12 * 60)
        assertIs<Decision.Intercept>(outside)
        assertEquals(BlockMode.DELAY, outside.effectiveMode)
        assertNull(outside.routine)
    }

    @Test
    fun aRoutineNeverLoosensWhatTheAppAlreadyClosed() {
        // Pause routine over an app set to Block: still Block, and the routine takes no credit.
        val blocked = insta.copy(mode = BlockMode.BLOCK)
        val c = cfg(evening(mode = RoutineMode.PAUSE), apps = listOf(blocked))
        val d = decide(c)
        assertIs<Decision.Intercept>(d)
        assertEquals(BlockMode.BLOCK, d.effectiveMode)
        assertNull(d.routine)
        // Pause routine over a Block *rule*: same answer.
        val ruled = insta.copy(rules = listOf(TimeRule(1, RuleMode.BLOCK, startMinute = 0, endMinute = 0)))
        val d2 = decide(cfg(evening(mode = RoutineMode.PAUSE), apps = listOf(ruled)))
        assertIs<Decision.Intercept>(d2)
        assertEquals(BlockMode.BLOCK, d2.effectiveMode)
    }

    @Test
    fun aPauseRoutineGivesAFreeHourItsPauseBack() {
        val free = insta.copy(rules = listOf(TimeRule(1, RuleMode.FREE, startMinute = 0, endMinute = 0)))
        assertEquals(Decision.Allow, decide(cfg(apps = listOf(free))))
        val d = decide(cfg(evening(mode = RoutineMode.PAUSE), apps = listOf(free)))
        assertIs<Decision.Intercept>(d)
        assertEquals(BlockMode.DELAY, d.effectiveMode)
        // It is the routine that changed the answer, so the routine is what the screen names.
        assertEquals("custom:evening", d.routine?.id)
    }

    @Test
    fun aRoutineThatMerelyAgreesWithTheAppIsNotBlamed() {
        val d = decide(cfg(evening(mode = RoutineMode.PAUSE)))
        assertIs<Decision.Intercept>(d)
        assertEquals(BlockMode.DELAY, d.effectiveMode)
        assertNull(d.routine)
    }

    @Test
    fun theListSaysWhatThePolicyDoes() {
        // verdictAt is what the app row draws; it must never promise a pause the policy will refuse.
        val c = cfg(evening())
        for (minute in 0 until TimeWindow.DAY step 17) {
            val shown = BlockPolicy.verdictAt(c, insta, 3, minute)
            val decided = decide(c, minute = minute)
            val blocked = decided is Decision.Intercept && decided.effectiveMode == BlockMode.BLOCK
            assertEquals(blocked, shown == RuleMode.BLOCK, "minute $minute")
        }
    }

    // --- scope ----------------------------------------------------------------------

    @Test
    fun theListSaysWhatThePolicyDoesDuringASessionToo() {
        // A session covers no window at all, so a verdict read off the windows alone would show
        // the app's ordinary pause while the app is in fact shut. That is the list lying.
        val focus = BuiltInRoutines.factory(BuiltInRoutines.FOCUS)!!
        val c = base.copy(routines = listOf(focus), run = RoutineRun(focus.id, now, now + 30 * min))
        assertEquals(RuleMode.PAUSE, BlockPolicy.verdictAt(c, insta, 3, 12 * 60))
        assertEquals(RuleMode.BLOCK, BlockPolicy.verdictNow(c, insta, now, 3, 12 * 60))
        val decided = decide(c, minute = 12 * 60)
        assertIs<Decision.Intercept>(decided)
        assertEquals(BlockMode.BLOCK, decided.effectiveMode)
        // And once it has run out, both agree again.
        assertEquals(RuleMode.PAUSE, BlockPolicy.verdictNow(c, insta, now + 31 * min, 3, 12 * 60))
    }

    @Test
    fun theEndOfASessionIsAMomentWorthWakingUpFor() {
        // The gate schedules its next judgement on this number; without the session in it, a
        // session that blocks an app with no rules of its own would end with nobody noticing.
        val focus = BuiltInRoutines.factory(BuiltInRoutines.FOCUS)!!
        val c = base.copy(routines = listOf(focus), run = RoutineRun(focus.id, now, now + 7 * min))
        assertEquals(7 * min, BlockPolicy.millisToNextChange(c, insta, 3, 12 * 60, 0, nowMillis = now))
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        val t0 = nowMillis()
        assertNull(store.millisToNextChange(insta.packageName))
        assertTrue(store.startRoutine(BuiltInRoutines.FOCUS, t0 + 7 * min))
        val next = store.millisToNextChange(insta.packageName)
        assertNotNull(next)
        assertTrue(next <= 7 * min, "next=$next")
    }

    @Test
    fun blockEndsWhenTheLastOfTheTwoLetsGo() {
        // A rule window closing at 18:00 inside a routine that blocks until 22:00 opens nothing
        // at 18:00, and a screen that promised 18:00 would be lying to whoever believed it.
        val rule = TimeRule(1, RuleMode.BLOCK, startMinute = 9 * 60, endMinute = 18 * 60)
        val long = evening().copy(windows = listOf(RoutineWindow(id = 1, startMinute = 9 * 60, endMinute = 22 * 60)))
        val app = insta.copy(rules = listOf(rule))
        val c = cfg(long, apps = listOf(app))
        assertEquals(12 * 60, BlockPolicy.blockEndsInMinutes(c, app, 3, 10 * 60))
        // Without the routine it is the rule's own window again.
        assertEquals(8 * 60, BlockPolicy.blockEndsInMinutes(base.copy(apps = listOf(app)), app, 3, 10 * 60))
    }

    @Test
    fun aSessionCannotBeResetIntoSomethingElseWhileItRuns() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        val focus = store.config.value.routine(BuiltInRoutines.FOCUS)!!
        assertTrue(store.upsertRoutine(focus.copy(manualMinutes = 45)))
        assertTrue(store.startRoutine(BuiltInRoutines.FOCUS, nowMillis() + 30 * min))
        assertFalse(store.resetRoutine(BuiltInRoutines.FOCUS))
        assertEquals(45, store.config.value.routine(BuiltInRoutines.FOCUS)!!.manualMinutes)
    }

    @Test
    fun strictModeRefusesToRemoveAnAppAndSaysSo() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta); upsertApp(mail) }
        store.enableStrict(nowMillis() + 60 * min)
        assertFalse(store.removeApp(insta.packageName))
        assertNotNull(store.config.value.app(insta.packageName))
        assertFalse(store.applyPicker(remove = setOf(mail.packageName), add = emptyList()))
        assertNotNull(store.config.value.app(mail.packageName))
        // Adding is not a softening, so that half of the same edit still lands.
        assertFalse(store.applyPicker(remove = setOf(mail.packageName), add = listOf("com.x" to "X")))
        assertNotNull(store.config.value.app("com.x"))
    }

    @Test
    fun anAppCanJoinTheListAndARoutineInOneAct() {
        val store = MonkStore(InMemoryStore())
        val hand = evening(allApps = false, packages = emptySet())
        assertTrue(store.upsertRoutine(hand))
        // Nothing watched yet: the app arrives on the list and in the routine together.
        assertTrue(store.addAppsToRoutine(hand.id, listOf(insta.packageName to "Instagram")))
        assertNotNull(store.config.value.app(insta.packageName))
        assertTrue(insta.packageName in store.config.value.routine(hand.id)!!.packages)
        assertEquals(BlockMode.BLOCK, (decide(store.config.value, minute = 23 * 60) as Decision.Intercept).effectiveMode)

        // A routine that covers everything needs no scope change; the app still joins the list.
        val all = evening().copy(id = "custom:all")
        assertTrue(store.upsertRoutine(all))
        assertTrue(store.addAppsToRoutine(all.id, listOf(mail.packageName to "Mail")))
        assertNotNull(store.config.value.app(mail.packageName))
        assertTrue(store.config.value.routine(all.id)!!.allApps)
        assertTrue(store.config.value.routine(all.id)!!.packages.isEmpty())

        // Widening is a tightening, so strict mode has no reason to refuse it.
        store.enableStrict(nowMillis() + 60 * min)
        assertTrue(store.addAppsToRoutine(hand.id, listOf(mail.packageName to "Mail")))
        assertTrue(mail.packageName in store.config.value.routine(hand.id)!!.packages)
        assertFalse(store.addAppsToRoutine("custom:gone", listOf("com.x" to "X")))
    }

    @Test
    fun aRowSaysNothingIsHappeningWhenNothingIs() {
        // Outside the base hours, with no routine covering it, the app's own setting is not what
        // would happen — and a list that shows it anyway is the whole schedule-versus-routine muddle.
        val office = Schedule(enabled = true, days = setOf(1, 2, 3, 4, 5), startMinute = 9 * 60, endMinute = 18 * 60)
        val bare = base.copy(schedule = office)
        assertNull(BlockPolicy.verdictNow(bare, insta, now, 3, 20 * 60))
        assertEquals(RuleMode.PAUSE, BlockPolicy.verdictNow(bare, insta, now, 3, 12 * 60))
        // A routine covering it out there is what brings the row back to life.
        val covered = cfg(evening()).copy(schedule = office)
        assertEquals(RuleMode.BLOCK, BlockPolicy.verdictNow(covered, insta, now, 3, 23 * 60))
    }

    @Test
    fun aBreakOutsideTheBaseHoursDoesNotLeaveTheCardClaimingARoutine() {
        // Base hours 09:00–18:00; a break taken at 17:30 runs past them. At 23:00 the evening
        // routine's window is open, but the break has lifted it — and every screen used to keep
        // saying it was holding while the gate let each app through. That is the one direction of
        // error that costs the user something.
        val office = Schedule(enabled = true, days = setOf(1, 2, 3, 4, 5, 6, 7), startMinute = 9 * 60, endMinute = 18 * 60)
        val c = cfg(evening()).copy(schedule = office, pausedUntil = now + 8 * 60 * min)
        assertEquals(Decision.Allow, decide(c, minute = 23 * 60))
        assertEquals(ProtectionState.SCHEDULED_OFF, c.state(now, 3, 23 * 60))
        assertNull(c.leadingRoutine(now, 3, 23 * 60))
        assertEquals(emptyList(), c.routinesInForce(now, 3, 23 * 60))
        // One that holds through a break is in force, and the state says so.
        val holds = cfg(evening(ignoresBreaks = true)).copy(schedule = office, pausedUntil = now + 8 * 60 * min)
        assertEquals(ProtectionState.ROUTINE, holds.state(now, 3, 23 * 60))
        assertEquals(BlockMode.BLOCK, (decide(holds, minute = 23 * 60) as Decision.Intercept).effectiveMode)
    }

    @Test
    fun noScreenClaimsARoutineTheSwitchHasTurnedOff() {
        val off = cfg(evening()).copy(enabled = false)
        assertFalse(off.routineHolds(off.routine("custom:evening")!!, now))
        assertEquals(emptyList(), off.routinesInForce(now, 3, 23 * 60))
        assertNull(off.leadingRoutine(now, 3, 23 * 60))
        // And the row shows nothing rather than a red Block with the routine's face on it.
        assertNull(BlockPolicy.layersAt(off, insta, now, 3, 23 * 60).verdict)
        assertNull(BlockPolicy.layersAt(off, insta, now, 3, 23 * 60).routine)
        assertEquals(Decision.Allow, decide(off, minute = 23 * 60))
    }

    @Test
    fun theRowAndTheGateAgreeInEveryGlobalState() {
        // One arithmetic, one answer: whatever the row shows must be what opening the app does.
        val office = Schedule(enabled = true, days = setOf(1, 2, 3, 4, 5, 6, 7), startMinute = 9 * 60, endMinute = 18 * 60)
        val variants = listOf(
            "plain" to cfg(evening()),
            "scheduled" to cfg(evening()).copy(schedule = office),
            "off" to cfg(evening()).copy(enabled = false),
            "break" to cfg(evening()).copy(pausedUntil = now + min),
            "break+holds" to cfg(evening(ignoresBreaks = true)).copy(pausedUntil = now + min),
            "strict" to cfg(evening()).copy(strictUntil = now + min, enabled = false),
            "session" to cfg(evening()).copy(run = RoutineRun("custom:evening", now, now + 30 * min)),
        )
        for ((name, c) in variants) {
            for (minute in listOf(3 * 60, 12 * 60, 20 * 60, 23 * 60)) {
                val shown = BlockPolicy.layersAt(c, insta, now, 3, minute).verdict
                val decided = decide(c, minute = minute)
                val gate = when {
                    decided is Decision.Intercept && decided.effectiveMode == BlockMode.BLOCK -> RuleMode.BLOCK
                    decided is Decision.Intercept -> RuleMode.PAUSE
                    else -> null
                }
                // FREE never intercepts, so the row saying "free" and the gate allowing agree.
                val rowSaysNothing = shown == null || shown == RuleMode.FREE
                if (gate == null) assertTrue(rowSaysNothing, "$name @$minute showed $shown but the gate allows")
                else assertEquals(gate, shown, "$name @$minute")
            }
        }
    }

    @Test
    fun theBaseHoursMayBeTightenedUnderStrictMode() {
        val office = Schedule(enabled = true, days = setOf(1, 2, 3, 4, 5), startMinute = 9 * 60, endMinute = 18 * 60)
        fun strictStore() = MonkStore(InMemoryStore()).apply {
            upsertApp(insta)
            setSchedule(office)
            enableStrict(nowMillis() + 60 * min)
        }
        // Switching them off means every minute of the week — a tightening, so it is allowed…
        val a = strictStore()
        assertTrue(a.setSchedule(office.copy(enabled = false)))
        // …and putting them back is the softening, so from there it is refused.
        assertFalse(a.setSchedule(office))
        // Widening the window is allowed; narrowing it, or dropping a day, is not.
        assertTrue(strictStore().setSchedule(office.copy(startMinute = 8 * 60)))
        assertFalse(strictStore().setSchedule(office.copy(startMinute = 10 * 60)))
        assertFalse(strictStore().setSchedule(office.copy(days = setOf(1, 2, 3))))
    }

    @Test
    fun aRuleThatCanNeverFireUnderTheBaseHoursIsKnownToBeDead() {
        val office = Schedule(enabled = true, days = setOf(1, 2, 3, 4, 5), startMinute = 9 * 60, endMinute = 18 * 60)
        val night = TimeRule(1, RuleMode.BLOCK, startMinute = 22 * 60, endMinute = 23 * 60)
        val noon = TimeRule(2, RuleMode.BLOCK, startMinute = 12 * 60, endMinute = 13 * 60)
        val weekend = TimeRule(3, RuleMode.BLOCK, days = setOf(6, 7), startMinute = 12 * 60, endMinute = 13 * 60)
        assertFalse(night.everAppliesUnder(office))
        assertTrue(noon.everAppliesUnder(office))
        assertFalse(weekend.everAppliesUnder(office))
        // With no base hours every rule can fire.
        assertTrue(night.everAppliesUnder(Schedule()))
    }

    @Test
    fun aBoundaryTimeLandsOnTheMinuteAndNotAHairBeforeIt() {
        // "Back at 08:59" for hours that start at 09:00: the old arithmetic read the clock twice
        // and subtracted, so it landed a millisecond short and formatted as the minute before.
        for (minutes in listOf(1, 7, 60, 754, 12 * 60, 25 * 60)) {
            val at = clockAfterWallMinutes(minutes)
            assertEquals(0, at % 60_000L, "$minutes min landed at ${formatClock(at)} + ${at % 60_000L} ms")
        }
        // And it is still the right moment: one minute ahead is one minute later on the clock.
        val a = clockAfterWallMinutes(5)
        val b = clockAfterWallMinutes(6)
        assertEquals(60_000L, b - a)
    }

    @Test
    fun aRoutineReachesOnlyTheAppsItCovers() {
        val c = cfg(evening(allApps = false, packages = setOf(insta.packageName)))
        assertEquals(BlockMode.BLOCK, (decide(c) as Decision.Intercept).effectiveMode)
        assertEquals(BlockMode.DELAY, (decide(c, pkg = mail.packageName) as Decision.Intercept).effectiveMode)
    }

    @Test
    fun aRoutineWithAnEmptyHandPickedScopeDoesNothingAtAll() {
        val empty = evening(allApps = false, packages = emptySet())
        assertTrue(empty.coversNothing)
        val c = cfg(empty)
        assertEquals(BlockMode.DELAY, (decide(c) as Decision.Intercept).effectiveMode)
        assertEquals(ProtectionState.ON, c.state(now, 3, 23 * 60))
        // Nor can such a routine be started, or hold the master switch down if one were forced in.
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta); upsertRoutine(empty) }
        assertFalse(store.startRoutine(empty.id, nowMillis() + 30 * min))
        store.updateConfig { it.copy(run = RoutineRun(empty.id, nowMillis(), nowMillis() + 30 * min)) }
        assertNull(store.config.value.activeRun(nowMillis()))
        assertTrue(store.switchOff())
    }

    @Test
    fun aPackageTheRoutineListsButNobodyWatchesIsKeptAndIgnored() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        val r = evening(allApps = false, packages = setOf(insta.packageName))
        assertTrue(store.upsertRoutine(r))
        store.removeApp(insta.packageName)
        // The routine still names it, so re-adding the app rejoins it — but it counts for nothing now.
        assertTrue(insta.packageName in store.config.value.routine(r.id)!!.packages)
        assertEquals(emptyList(), store.appsCovered(store.config.value.routine(r.id)!!))
        store.addApp(insta.packageName, "Instagram")
        assertEquals(listOf(insta.packageName), store.appsCovered(store.config.value.routine(r.id)!!))
    }

    // --- the layers above it --------------------------------------------------------

    @Test
    fun aRoutineWorksOutsideProtectionHours() {
        val office = Schedule(enabled = true, days = setOf(1, 2, 3, 4, 5), startMinute = 9 * 60, endMinute = 18 * 60)
        val c = cfg(evening()).copy(schedule = office)
        // 23:00 on a Wednesday is outside protection hours; without a routine nothing is intercepted.
        assertEquals(Decision.Allow, decide(base.copy(schedule = office), minute = 23 * 60))
        val d = decide(c, minute = 23 * 60)
        assertIs<Decision.Intercept>(d)
        assertEquals(BlockMode.BLOCK, d.effectiveMode)
        assertEquals(ProtectionState.ROUTINE, c.state(now, 3, 23 * 60))
        // And at an hour it does not cover, out there, the schedule is still the last word.
        assertEquals(Decision.Allow, decide(c, minute = 20 * 60))
        assertEquals(ProtectionState.SCHEDULED_OFF, c.state(now, 3, 20 * 60))
    }

    @Test
    fun aBreakLiftsAScheduledRoutineUnlessItSaysOtherwise() {
        val onBreak = cfg(evening()).copy(pausedUntil = now + 5 * min)
        assertEquals(Decision.Allow, decide(onBreak))
        assertEquals(ProtectionState.BREAK, onBreak.state(now, 3, 23 * 60))

        val holds = cfg(evening(ignoresBreaks = true)).copy(pausedUntil = now + 5 * min)
        val d = decide(holds)
        assertIs<Decision.Intercept>(d)
        assertEquals(BlockMode.BLOCK, d.effectiveMode)
        assertEquals(ProtectionState.ROUTINE, holds.state(now, 3, 23 * 60))
        // The app's own layer is still lifted, though: a break is a break for everything else.
        assertEquals(BlockMode.BLOCK, (decide(holds, pkg = mail.packageName) as Decision.Intercept).effectiveMode)
    }

    @Test
    fun theMasterSwitchTurnsAScheduledRoutineOffButNotASession() {
        val off = cfg(evening()).copy(enabled = false)
        assertEquals(Decision.Allow, decide(off))
        assertEquals(ProtectionState.OFF, off.state(now, 3, 23 * 60))

        val session = cfg(evening()).copy(enabled = false, run = RoutineRun("custom:evening", now, now + 30 * min))
        val d = decide(session)
        assertIs<Decision.Intercept>(d)
        assertEquals(BlockMode.BLOCK, d.effectiveMode)
        assertEquals(ProtectionState.ROUTINE, session.state(now, 3, 12 * 60))
    }

    @Test
    fun aLockedAppAndStrictModeKeepTheirRoutinesThroughOffAndBreaks() {
        val locked = insta.copy(locked = true)
        val c = cfg(evening(), apps = listOf(locked)).copy(enabled = false, pausedUntil = now + 5 * min)
        assertEquals(BlockMode.BLOCK, (decide(c) as Decision.Intercept).effectiveMode)
        val strict = cfg(evening()).copy(enabled = false, pausedUntil = now + 5 * min, strictUntil = now + 60 * min)
        assertEquals(BlockMode.BLOCK, (decide(strict) as Decision.Intercept).effectiveMode)
    }

    @Test
    fun aSessionRunsEvenOutsideItsOwnHoursAndEndsOnTheClock() {
        val c = cfg(evening()).copy(run = RoutineRun("custom:evening", now, now + 30 * min))
        // Midday, nowhere near 22:00, and still blocked: that is what starting it by hand means.
        assertEquals(BlockMode.BLOCK, (decide(c, minute = 12 * 60) as Decision.Intercept).effectiveMode)
        assertEquals(BlockMode.DELAY, (decide(c, minute = 12 * 60, at = now + 31 * min) as Decision.Intercept).effectiveMode)
    }

    // --- allowances and limits ------------------------------------------------------

    @Test
    fun aBlockingRoutineBeatsAnAllowanceHandedOutBeforeItOpened() {
        val allow = mapOf(insta.packageName to now + 5 * min)
        assertEquals(Decision.Allow, decide(cfg(), allow = allow))
        val d = decide(cfg(evening()), allow = allow)
        assertIs<Decision.Intercept>(d)
        assertEquals(BlockMode.BLOCK, d.effectiveMode)
        // A pausing routine does not: the five minutes were paid for honestly.
        assertEquals(Decision.Allow, decide(cfg(evening(mode = RoutineMode.PAUSE)), allow = allow))
    }

    @Test
    fun theDailyLimitStillCountsUnderAPausingRoutine() {
        val limited = insta.copy(dailyLimit = 2)
        val c = cfg(evening(mode = RoutineMode.PAUSE), apps = listOf(limited))
        val under = decide(c, opens = 1)
        assertIs<Decision.Intercept>(under)
        assertFalse(under.limitReached)
        val over = decide(c, opens = 2)
        assertIs<Decision.Intercept>(over)
        assertTrue(over.limitReached)
        assertEquals(BlockMode.BLOCK, over.effectiveMode)
    }

    // --- sessions at the store ------------------------------------------------------

    @Test
    fun onlyOneSessionAtATimeAndOnlyEverLonger() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        val t0 = nowMillis()
        assertTrue(store.startRoutine(BuiltInRoutines.FOCUS, t0 + 30 * min))
        // A different routine has to wait; the running one can be extended but never shortened.
        assertTrue(store.setRoutineEnabled(BuiltInRoutines.EVENING, true))
        assertFalse(store.startRoutine(BuiltInRoutines.EVENING, t0 + 60 * min))
        assertFalse(store.startRoutine(BuiltInRoutines.FOCUS, t0 + 10 * min))
        assertTrue(store.startRoutine(BuiltInRoutines.FOCUS, t0 + 90 * min))
        assertEquals(t0 + 90 * min, store.config.value.run?.until)
        // Extending keeps the moment it began, so the ring on the card keeps counting from there.
        assertTrue(store.config.value.run!!.startedAt <= t0 + min)
    }

    @Test
    fun aSessionCannotBeStartedForARoutineThatIsSwitchedOff() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        assertFalse(store.config.value.routine(BuiltInRoutines.EVENING)!!.enabled)
        assertFalse(store.startRoutine(BuiltInRoutines.EVENING, nowMillis() + 30 * min))
        assertTrue(store.setRoutineEnabled(BuiltInRoutines.EVENING, true))
        assertTrue(store.startRoutine(BuiltInRoutines.EVENING, nowMillis() + 30 * min))
    }

    @Test
    fun startingASessionEndsABreakStartsItsCooldownAndDropsOnlyItsOwnAllowances() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta); upsertApp(mail) }
        val t0 = nowMillis()
        val scoped = evening(allApps = false, packages = setOf(insta.packageName))
        assertTrue(store.upsertRoutine(scoped))
        store.grantAllowance(insta.packageName, 5, now = t0)
        store.grantAllowance(mail.packageName, 5, now = t0)
        assertTrue(store.pauseProtection(t0 + 30 * min))
        assertTrue(store.startRoutine(scoped.id, t0 + 15 * min))
        assertEquals(0L, store.config.value.pausedUntil)
        assertTrue(store.config.value.lastBreakEndedAt >= t0)
        assertTrue(store.config.value.nextBreakAt(t0 + min) >= t0 + MonkConfig.BREAK_COOLDOWN_MS)
        assertNull(store.allowances.value[insta.packageName])
        assertNotNull(store.allowances.value[mail.packageName])
    }

    @Test
    fun aSessionRefusesBreaksAndTheMasterSwitchWhileItRuns() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        val t0 = nowMillis()
        assertTrue(store.startRoutine(BuiltInRoutines.FOCUS, t0 + 30 * min))
        assertFalse(store.canStartBreak())
        assertFalse(store.pauseProtection(t0 + 5 * min))
        assertFalse(store.switchOff())
        assertTrue(store.config.value.enabled)
    }

    @Test
    fun aSessionCannotBeSoftenedWhileItRuns() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        assertTrue(store.startRoutine(BuiltInRoutines.FOCUS, nowMillis() + 30 * min))
        val focus = store.config.value.routine(BuiltInRoutines.FOCUS)!!
        assertFalse(store.upsertRoutine(focus.copy(mode = RoutineMode.PAUSE)))
        assertFalse(store.setRoutineEnabled(BuiltInRoutines.FOCUS, false))
        assertFalse(store.upsertRoutine(focus.copy(allApps = false, packages = emptySet())))
        // Tightening is still allowed, and so is changing what does not weaken it.
        assertTrue(store.upsertRoutine(focus.copy(ignoresBreaks = true, name = "Deep work")))
        assertEquals("Deep work", store.config.value.routine(BuiltInRoutines.FOCUS)!!.name)
    }

    // --- strict mode ----------------------------------------------------------------

    @Test
    fun strictModeLetsARoutineBeTightenedAndNeverSoftened() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        val mild = evening(mode = RoutineMode.PAUSE)
        assertTrue(store.upsertRoutine(mild))
        store.enableStrict(nowMillis() + 60 * min)

        assertFalse(store.setRoutineEnabled(mild.id, false))
        assertFalse(store.upsertRoutine(mild.copy(windows = emptyList())))
        assertFalse(store.upsertRoutine(mild.copy(allApps = false, packages = setOf(insta.packageName))))
        assertFalse(store.deleteRoutine(mild.id))

        assertTrue(store.upsertRoutine(mild.copy(mode = RoutineMode.BLOCK)))
        val wider = mild.copy(mode = RoutineMode.BLOCK, windows = mild.windows + RoutineWindow(id = 2, startMinute = 12 * 60, endMinute = 14 * 60))
        assertTrue(store.upsertRoutine(wider))
        // A brand-new routine is more protection, so strict mode has no reason to refuse it.
        assertTrue(store.upsertRoutine(evening().copy(id = "custom:other")))
    }

    @Test
    fun strictnessComparesEveryMinuteOfTheWeekNotJustTheNumbers() {
        val night = evening() // 22:00–07:00 every day
        val shorter = night.copy(windows = listOf(RoutineWindow(id = 1, startMinute = 23 * 60, endMinute = 7 * 60)))
        val longer = night.copy(windows = listOf(RoutineWindow(id = 1, startMinute = 21 * 60, endMinute = 7 * 60)))
        assertFalse(shorter.isAtLeastAsStrictAs(night))
        assertTrue(longer.isAtLeastAsStrictAs(night))
        // Same hours, fewer days: not as strict, whatever the clock says.
        val weekdays = night.copy(windows = listOf(night.windows[0].copy(days = setOf(1, 2, 3, 4, 5))))
        assertFalse(weekdays.isAtLeastAsStrictAs(night))
        assertTrue(night.isAtLeastAsStrictAs(weekdays))
        // Switching off is the softening that hides as a flag.
        assertFalse(night.copy(enabled = false).isAtLeastAsStrictAs(night))
        assertTrue(night.isAtLeastAsStrictAs(night.copy(enabled = false)))
        // Scope and the break promise count too.
        assertFalse(night.copy(allApps = false, packages = setOf("a")).isAtLeastAsStrictAs(night))
        assertTrue(night.copy(allApps = false, packages = setOf("a", "b")).isAtLeastAsStrictAs(night.copy(allApps = false, packages = setOf("a"))))
        assertFalse(night.isAtLeastAsStrictAs(night.copy(ignoresBreaks = true)))
    }

    // --- built-ins, seeding, migration ----------------------------------------------

    @Test
    fun theBuiltInsAreSeededToppedUpAndCannotBeDeleted() {
        val store = MonkStore(InMemoryStore())
        assertEquals(BuiltInRoutines.ids, store.config.value.routines.filter { it.builtIn }.map { it.id }.toSet())
        // Only Focus is on out of the box: an update must change nobody's behaviour by itself.
        assertEquals(setOf(BuiltInRoutines.FOCUS), store.config.value.routines.filter { it.enabled }.map { it.id }.toSet())
        assertFalse(store.deleteRoutine(BuiltInRoutines.EVENING))
        // One gone missing from a config comes back at its factory settings, keeping the rest.
        store.updateConfig { c -> c.copy(routines = c.routines.filter { it.id != BuiltInRoutines.EVENING }) }
        val topped = store.config.value.normalized(nowMillis())
        assertNotNull(topped.routine(BuiltInRoutines.EVENING))
        assertEquals(BuiltInRoutines.all.size, topped.routines.size)
    }

    @Test
    fun resettingABuiltInRestoresItsHoursAndKeepsWhetherItIsOn() {
        val store = MonkStore(InMemoryStore())
        val evening = store.config.value.routine(BuiltInRoutines.EVENING)!!
        assertTrue(store.upsertRoutine(evening.copy(enabled = true, emoji = "🎧", name = "Mine", windows = emptyList())))
        assertTrue(store.resetRoutine(BuiltInRoutines.EVENING))
        val back = store.config.value.routine(BuiltInRoutines.EVENING)!!
        assertEquals(BuiltInRoutines.factory(BuiltInRoutines.EVENING)!!.windows, back.windows)
        assertEquals("", back.name)
        assertTrue(back.enabled)
    }

    @Test
    fun aFocusSessionFromAnOlderBuildBecomesASessionOfTheFocusRoutine() {
        val legacy = MonkConfig(apps = listOf(insta), focusUntil = now + 20 * min, focusStartedAt = now - 10 * min)
        val migrated = legacy.normalized(now)
        assertEquals(BuiltInRoutines.FOCUS, migrated.run?.routineId)
        assertEquals(now + 20 * min, migrated.run?.until)
        assertEquals(now - 10 * min, migrated.run?.startedAt)
        assertEquals(0L, migrated.focusUntil)
        assertEquals(ProtectionState.ROUTINE, migrated.state(now, 3, 12 * 60))
        assertEquals(BlockMode.BLOCK, (decide(migrated, minute = 12 * 60) as Decision.Intercept).effectiveMode)
        // One that had already run out leaves nothing behind.
        val stale = MonkConfig(apps = listOf(insta), focusUntil = now - min).normalized(now)
        assertNull(stale.run)
        assertEquals(ProtectionState.ON, stale.state(now, 3, 12 * 60))
    }

    @Test
    fun normalisingIsIdempotentAndRepairsWhatItFinds() {
        val messy = MonkConfig(
            apps = listOf(insta),
            routines = listOf(
                evening().copy(id = "custom:dup"),
                evening().copy(id = "custom:dup", name = "second"),
                Routine(id = "", name = "nameless"),
                evening().copy(id = BuiltInRoutines.FOCUS, builtIn = false, mode = RoutineMode.PAUSE),
                Routine(
                    id = "custom:broken",
                    builtIn = true,
                    emoji = "not an emoji",
                    name = "   padded   ",
                    manualMinutes = 99_999,
                    windows = listOf(RoutineWindow(id = 1, days = emptySet(), startMinute = 5000, endMinute = -3)),
                ),
            ),
        )
        val once = messy.normalized(now)
        assertEquals(once, once.normalized(now))
        // Built-ins first, in their own order, marked as built-in whatever the config claimed.
        assertEquals(BuiltInRoutines.all.map { it.id }, once.routines.take(4).map { it.id })
        assertTrue(once.routines.take(4).all { it.builtIn })
        // A built-in the user edited keeps the edit; the impostor flag on a custom one is dropped.
        assertEquals(RoutineMode.PAUSE, once.routine(BuiltInRoutines.FOCUS)!!.mode)
        assertFalse(once.routine("custom:broken")!!.builtIn)
        // Duplicates collapse to the first, the id-less one is dropped.
        assertEquals(1, once.routines.count { it.id == "custom:dup" })
        assertEquals("Evening", once.routine("custom:dup")!!.name)
        assertNull(once.routines.firstOrNull { it.id.isBlank() })
        val broken = once.routine("custom:broken")!!
        assertEquals("", broken.emoji)
        assertEquals("padded", broken.name)
        assertEquals(Routine.MAX_MANUAL_MINUTES, broken.manualMinutes)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), broken.windows[0].days)
        assertTrue(broken.windows[0].startMinute in 0 until TimeWindow.DAY)
        assertTrue(broken.windows[0].endMinute in 0 until TimeWindow.DAY)
    }

    @Test
    fun aSessionForARoutineThatIsGoneIsNotASession() {
        val orphan = base.copy(run = RoutineRun("custom:vanished", now, now + 30 * min)).normalized(now)
        assertNull(orphan.run)
        assertNull(orphan.activeRun(now))
        assertEquals(ProtectionState.ON, orphan.state(now, 3, 12 * 60))
    }

    @Test
    fun aClockJumpCarriesTheSessionWithIt() {
        val c = cfg(evening()).copy(run = RoutineRun("custom:evening", now, now + 30 * min))
        val forward = c.shifted(2 * 60 * min)
        assertEquals(now + 2 * 60 * min + 30 * min, forward.run?.until)
        assertEquals(now + 2 * 60 * min, forward.run?.startedAt)
        assertNotNull(forward.activeRun(now + 2 * 60 * min))
        assertNull(forward.activeRun(now + 2 * 60 * min + 31 * min))
    }

    // --- windows --------------------------------------------------------------------

    @Test
    fun windowsAcrossMidnightBelongToTheDayTheyStartOn() {
        val friday = evening().copy(windows = listOf(RoutineWindow(id = 1, days = setOf(5), startMinute = 22 * 60, endMinute = 7 * 60)))
        assertTrue(friday.isOpen(5, 23 * 60))
        assertTrue(friday.isOpen(6, 3 * 60))
        assertFalse(friday.isOpen(6, 23 * 60))
        assertFalse(friday.isOpen(5, 8 * 60))
    }

    @Test
    fun openUntilWalksToTheEndOfTheUnionOfWindows() {
        val split = evening().copy(
            windows = listOf(
                RoutineWindow(id = 1, startMinute = 9 * 60, endMinute = 12 * 60),
                RoutineWindow(id = 2, startMinute = 12 * 60, endMinute = 14 * 60),
            ),
        )
        // Two windows that touch are one stretch: 10:00 runs to 14:00, not to noon.
        assertEquals(4 * 60, split.openUntilMinutes(3, 10 * 60))
        assertNull(split.openUntilMinutes(3, 15 * 60))
        // All day, every day, never closes.
        val always = evening().copy(windows = listOf(RoutineWindow(id = 1, startMinute = 0, endMinute = 0)))
        assertNull(always.openUntilMinutes(3, 10 * 60))
    }

    @Test
    fun aRoutineBoundaryIsAReJudgeMoment() {
        val c = cfg(evening())
        // 21:00, and the routine opens at 22:00: the app in front must be judged again then.
        assertEquals(60, BlockPolicy.minutesToNextChange(c, insta, 3, 21 * 60))
        // A routine that covers another app entirely is none of this app's business.
        val elsewhere = cfg(evening(allApps = false, packages = setOf(mail.packageName)))
        assertNull(BlockPolicy.minutesToNextChange(elsewhere, insta, 3, 21 * 60))
        // So is one that is switched off.
        assertNull(BlockPolicy.minutesToNextChange(cfg(evening(enabled = false)), insta, 3, 21 * 60))
    }

    // --- the sign -------------------------------------------------------------------

    @Test
    fun theSignKeepsOneWholeEmojiAndNothingElse() {
        assertEquals("🌙", Emoji.firstCluster("🌙"))
        assertEquals("🌙", Emoji.firstCluster("  🌙  "))
        // Only the first: a keyboard that pastes a row of them still leaves one.
        assertEquals("🌙", Emoji.firstCluster("🌙🌅💼"))
        // Not text, not a stray digit, not punctuation.
        assertEquals("", Emoji.firstCluster("abc"))
        assertEquals("", Emoji.firstCluster("7"))
        assertEquals("", Emoji.firstCluster("!"))
        assertEquals("", Emoji.firstCluster(""))
        assertEquals("", Emoji.firstCluster("   "))
        // The pieces that belong to one emoji stay with it.
        assertEquals("👍🏽", Emoji.firstCluster("👍🏽"))
        assertEquals("👨‍👩‍👧", Emoji.firstCluster("👨‍👩‍👧"))
        assertEquals("🇬🇧", Emoji.firstCluster("🇬🇧"))
        assertEquals("✍️", Emoji.firstCluster("✍️"))
        assertEquals("1️⃣", Emoji.firstCluster("1️⃣"))
        // A flag followed by another flag is still one flag.
        assertEquals("🇬🇧", Emoji.firstCluster("🇬🇧🇫🇷"))
        // Whatever comes after the emoji is not part of the sign.
        assertEquals("🌙", Emoji.firstCluster("🌙 evening"))
        assertTrue(Emoji.suggestions.all { Emoji.firstCluster(it) == it })
    }

    @Test
    fun aRoutineIsRepairedBeforeItIsStored() {
        val store = MonkStore(InMemoryStore())
        val messy = evening().copy(id = "custom:messy", emoji = "zzz", name = " Loud name ")
        assertTrue(store.upsertRoutine(messy))
        val stored = store.config.value.routine("custom:messy")!!
        assertEquals("", stored.emoji)
        assertEquals("Loud name", stored.name)
        assertFalse(stored.builtIn)
    }

    @Test
    fun thereIsACeilingOnHowManyRoutinesCanBeKept() {
        val store = MonkStore(InMemoryStore())
        var added = 0
        while (store.upsertRoutine(evening().copy(id = "custom:$added"))) added++
        assertEquals(Routine.MAX_ROUTINES, store.config.value.routines.size)
        // The built-ins are inside the ceiling, and none of them was pushed out to make room.
        assertTrue(BuiltInRoutines.ids.all { store.config.value.routine(it) != null })
    }

    // --- what the surfaces read -----------------------------------------------------

    @Test
    fun theLeadingRoutineIsTheSessionElseTheStrictestOpenOne() {
        val soft = evening(mode = RoutineMode.PAUSE).copy(id = "custom:soft")
        val hard = evening(mode = RoutineMode.BLOCK).copy(id = "custom:hard")
        val c = cfg(soft, hard)
        assertEquals("custom:hard", c.leadingRoutine(now, 3, 23 * 60)?.id)
        val session = c.copy(run = RoutineRun("custom:soft", now, now + 30 * min))
        assertEquals("custom:soft", session.leadingRoutine(now, 3, 23 * 60)?.id)
        assertNull(c.leadingRoutine(now, 3, 12 * 60))
    }

    @Test
    fun theStatePrefersASessionThenTheSwitchThenTheSchedule() {
        val office = Schedule(enabled = true, days = setOf(1, 2, 3, 4, 5), startMinute = 9 * 60, endMinute = 18 * 60)
        val c = cfg(evening()).copy(schedule = office)
        assertEquals(ProtectionState.ON, c.state(now, 3, 12 * 60))
        assertEquals(ProtectionState.ROUTINE, c.state(now, 3, 23 * 60))
        assertEquals(ProtectionState.SCHEDULED_OFF, c.state(now, 3, 20 * 60))
        assertEquals(ProtectionState.OFF, c.copy(enabled = false).state(now, 3, 23 * 60))
        val session = c.copy(enabled = false, run = RoutineRun("custom:evening", now, now + 30 * min))
        assertEquals(ProtectionState.ROUTINE, session.state(now, 3, 20 * 60))
        // Strict mode never decides what happens to an app, so a routine is the louder headline.
        assertEquals(ProtectionState.ROUTINE, c.copy(strictUntil = now + 60 * min).state(now, 3, 23 * 60))
        assertEquals(ProtectionState.STRICT, c.copy(strictUntil = now + 60 * min).state(now, 3, 12 * 60))
    }

    @Test
    fun theCardNamesARoutineOnlyWhileItHasSomethingToSay() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        val r = evening()
        assertTrue(store.upsertRoutine(r))
        // Its window is open now or it is not; either way the end time is wall-clock, never a guess.
        val end = store.routineEndsAt(store.config.value.routine(r.id)!!)
        val open = r.isOpen(com.mdportnov.monk.shared.data.localMoment().dayIso, com.mdportnov.monk.shared.data.localMoment().minuteOfDay)
        if (open) assertNotNull(end) else assertNull(end)
        // A session end is the session's own instant, whatever the window says.
        assertTrue(store.startRoutine(r.id, nowMillis() + 42 * min))
        assertEquals(store.config.value.run?.until, store.routineEndsAt(store.config.value.routine(r.id)!!))
    }
}
