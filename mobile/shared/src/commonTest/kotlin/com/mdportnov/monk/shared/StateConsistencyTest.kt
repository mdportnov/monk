package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.data.InMemoryStore
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.localMoment
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.BlockPolicy
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.Decision
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.ProtectionState
import com.mdportnov.monk.shared.model.RuleMode
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
 * One state for every surface: the card, the compact bar, the tile and the notification all
 * read [MonkConfig.state], so what they show is what [BlockPolicy] does.
 */
class StateConsistencyTest {
    private val insta = BlockedApp("com.instagram.android", "Instagram")
    private val base = MonkConfig(apps = listOf(insta))
    private val now = 10_000_000L
    private val min = 60_000L
    private val office = Schedule(enabled = true, days = setOf(1, 2, 3, 4, 5), startMinute = 9 * 60, endMinute = 18 * 60)

    private fun decide(c: MonkConfig, day: Int = 3, minute: Int = 12 * 60, allow: Map<String, Long> = emptyMap()) =
        BlockPolicy.decide(c, insta.packageName, now, day, minute, allow)

    @Test
    fun stateFollowsThePolicyPrecedence() {
        assertEquals(ProtectionState.ON, base.state(now, 3, 12 * 60))
        assertEquals(ProtectionState.OFF, base.copy(enabled = false).state(now, 3, 12 * 60))
        assertEquals(ProtectionState.STRICT, base.copy(strictUntil = now + min).state(now, 3, 12 * 60))
        assertEquals(ProtectionState.BREAK, base.copy(pausedUntil = now + min).state(now, 3, 12 * 60))
        assertEquals(ProtectionState.FOCUS, base.copy(focusUntil = now + min).state(now, 3, 12 * 60))
        val scheduled = base.copy(schedule = office)
        assertEquals(ProtectionState.ON, scheduled.state(now, 3, 12 * 60))
        assertEquals(ProtectionState.SCHEDULED_OFF, scheduled.state(now, 3, 20 * 60))
        assertEquals(ProtectionState.SCHEDULED_OFF, scheduled.state(now, 6, 12 * 60))
    }

    @Test
    fun aBreakOvertakenByTheScheduleShowsAsOffBySchedule() {
        // Break started at 17:50 for an hour; at 18:00 the schedule turns protection off anyway.
        val c = base.copy(schedule = office, pausedUntil = now + 50 * min, pauseStartedAt = now - 10 * min)
        assertEquals(ProtectionState.BREAK, c.state(now, 3, 17 * 60 + 55))
        assertEquals(ProtectionState.SCHEDULED_OFF, c.state(now, 3, 18 * 60 + 5))
        assertEquals(Decision.Allow, decide(c, minute = 18 * 60 + 5))
        // The break is still a break where the schedule would otherwise protect.
        assertEquals(Decision.Allow, decide(c, minute = 17 * 60 + 55))
    }

    @Test
    fun focusBlocksOutsideTheScheduleAndTheStateSaysSo() {
        val c = base.copy(schedule = office, focusUntil = now + 30 * min)
        assertEquals(ProtectionState.FOCUS, c.state(now, 6, 12 * 60))
        val d = decide(c, day = 6)
        assertIs<Decision.Intercept>(d)
        assertTrue(d.focus)
        // A break underneath a focus session is not what the user sees either.
        assertEquals(ProtectionState.FOCUS, c.copy(pausedUntil = now + min).state(now, 3, 12 * 60))
    }

    @Test
    fun strictDoesNotOverrideTheSchedule() {
        val c = base.copy(schedule = office, strictUntil = now + 60 * min)
        assertEquals(ProtectionState.SCHEDULED_OFF, c.state(now, 6, 12 * 60))
        assertEquals(Decision.Allow, decide(c, day = 6))
        assertEquals(ProtectionState.STRICT, c.state(now, 3, 12 * 60))
        assertIs<Decision.Intercept>(decide(c, day = 3))
    }

    @Test
    fun noBreakOutsideTheSchedule() {
        val c = base.copy(schedule = office)
        assertTrue(c.canStartBreak(now, 3, 12 * 60))
        assertFalse(c.canStartBreak(now, 3, 20 * 60))
        assertFalse(c.canStartBreak(now, 7, 12 * 60))
        assertTrue(base.canStartBreak(now, 7, 12 * 60))
    }

    @Test
    fun storeRefusesABreakWhileTheScheduleHasProtectionOff() {
        val m = localMoment()
        val otherDay = TimeWindow.nextDay(m.dayIso)
        val store = MonkStore(InMemoryStore()).apply {
            upsertApp(insta)
            updateConfig { it.copy(schedule = Schedule(enabled = true, days = setOf(otherDay), startMinute = 0, endMinute = 0)) }
        }
        assertFalse(store.canStartBreak())
        assertFalse(store.pauseProtection(nowMillis() + 15 * min))
        assertEquals(0L, store.config.value.pausedUntil)
        store.updateConfig { it.copy(schedule = Schedule(enabled = false)) }
        assertTrue(store.canStartBreak())
        assertTrue(store.pauseProtection(nowMillis() + 15 * min))
    }

    @Test
    fun focusAndStrictCutABreakShortAndStartItsCooldown() {
        val focus = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        val t0 = nowMillis()
        assertTrue(focus.pauseProtection(t0 + 30 * min))
        focus.startFocus(t0 + 15 * min)
        assertEquals(0L, focus.config.value.pausedUntil)
        assertTrue(focus.config.value.lastBreakEndedAt >= t0)
        assertTrue(focus.config.value.nextBreakAt(t0 + 16 * min) >= t0 + MonkConfig.BREAK_COOLDOWN_MS)

        val strict = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        assertTrue(strict.pauseProtection(t0 + 30 * min))
        strict.enableStrict(t0 + 60 * min)
        assertEquals(0L, strict.config.value.pausedUntil)
        assertTrue(strict.config.value.lastBreakEndedAt >= t0)
        assertTrue(strict.config.value.enabled)
    }

    @Test
    fun switchingOffDuringABreakEndsItByHand() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        val t0 = nowMillis()
        assertTrue(store.pauseProtection(t0 + 30 * min))
        assertTrue(store.switchOff())
        val c = store.config.value
        assertFalse(c.enabled)
        assertEquals(0L, c.pausedUntil)
        assertTrue(c.lastBreakEndedAt >= t0)
        store.switchOn()
        assertTrue(store.config.value.enabled)
        // On again does not hand out a fresh break right away: the cooldown from the cut-short one holds.
        assertFalse(store.canStartBreak())
    }

    @Test
    fun startFocusWhileOffTurnsProtectionOnToStay() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta); switchOff() }
        val t0 = nowMillis()
        store.startFocus(t0 + 15 * min)
        assertTrue(store.config.value.enabled)
        assertIs<Decision.Intercept>(store.decide(insta.packageName))
        // The state after the session is "on", not the "off" it started from; the dialog says so.
        assertEquals(ProtectionState.ON, store.config.value.copy(focusUntil = 0).state(t0, 3, 12 * 60))
    }

    @Test
    fun switchingToBlockEndsALiveAllowance() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        store.grantAllowance(insta.packageName, 5)
        assertNotNull(store.activeAllowances()[insta.packageName])
        assertEquals(Decision.Allow, store.decide(insta.packageName))
        store.upsertApp(insta.copy(mode = BlockMode.BLOCK))
        assertNull(store.activeAllowances()[insta.packageName])
        val d = store.decide(insta.packageName)
        assertIs<Decision.Intercept>(d)
        assertEquals(BlockMode.BLOCK, d.effectiveMode)
        // Any other edit leaves the allowance alone.
        store.grantAllowance(insta.packageName, 5)
        store.upsertApp(store.config.value.app(insta.packageName)!!.copy(delaySeconds = 30))
        assertNotNull(store.activeAllowances()[insta.packageName])
    }

    @Test
    fun aBlockRuleOpenNowEndsALiveAllowanceALaterOneDoesNot() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        val m = localMoment()
        store.grantAllowance(insta.packageName, 5)
        val later = TimeRule(1, RuleMode.BLOCK, days = setOf(TimeWindow.nextDay(m.dayIso)), startMinute = 0, endMinute = 0)
        store.upsertRule(insta.packageName, later)
        assertNotNull(store.activeAllowances()[insta.packageName])
        val nowRule = TimeRule(2, RuleMode.BLOCK, days = setOf(1, 2, 3, 4, 5, 6, 7), startMinute = 0, endMinute = 0)
        store.upsertRule(insta.packageName, nowRule)
        assertNull(store.activeAllowances()[insta.packageName])
    }

    @Test
    fun allowanceOutlivesAModeChangeOnlyWhenItStaysHonest() {
        // Policy: a Block window ignores an allowance; app mode Block does not. The list hides the
        // pill in the first case and the store drops the allowance in the second.
        val block = TimeRule(1, RuleMode.BLOCK, startMinute = 0, endMinute = 0)
        val c = base.copy(apps = listOf(insta.copy(rules = listOf(block))))
        assertIs<Decision.Intercept>(decide(c, allow = mapOf(insta.packageName to now + min)))
        val blockMode = base.copy(apps = listOf(insta.copy(mode = BlockMode.BLOCK)))
        assertEquals(Decision.Allow, decide(blockMode, allow = mapOf(insta.packageName to now + min)))
    }

    @Test
    fun limitAndPauseSettingsApplyOnlyWhereTheAppCanOpen() {
        val plainBlock = insta.copy(mode = BlockMode.BLOCK, dailyLimit = 3)
        assertFalse(plainBlock.canOpen)
        assertFalse(plainBlock.hasPauseScreen)
        assertFalse(plainBlock.limitApplies)
        val withFree = plainBlock.copy(rules = listOf(TimeRule(1, RuleMode.FREE, startMinute = 20 * 60, endMinute = 21 * 60)))
        assertTrue(withFree.canOpen)
        assertFalse(withFree.hasPauseScreen)
        assertTrue(withFree.limitApplies)
        val withPause = plainBlock.copy(rules = listOf(TimeRule(1, RuleMode.PAUSE, startMinute = 20 * 60, endMinute = 21 * 60)))
        assertTrue(withPause.hasPauseScreen)
        assertTrue(withPause.limitApplies)
        assertTrue(insta.hasPauseScreen)
        assertTrue(insta.canOpen)
        assertFalse(insta.limitApplies)
        // And the policy agrees: the limit is reached inside the Free window of a Block app.
        val d = BlockPolicy.decide(base.copy(apps = listOf(withFree)), insta.packageName, now, 3, 20 * 60 + 30, emptyMap(), opensToday = 3)
        assertIs<Decision.Intercept>(d)
        assertTrue(d.limitReached)
    }

    @Test
    fun dailyLimitBeatsFreeRuleAndFocusBeatsBoth() {
        val free = TimeRule(1, RuleMode.FREE, startMinute = 0, endMinute = 0)
        val c = base.copy(apps = listOf(insta.copy(dailyLimit = 1, rules = listOf(free))))
        assertEquals(Decision.Allow, BlockPolicy.decide(c, insta.packageName, now, 3, 12 * 60, emptyMap(), opensToday = 0))
        val limited = BlockPolicy.decide(c, insta.packageName, now, 3, 12 * 60, emptyMap(), opensToday = 1)
        assertIs<Decision.Intercept>(limited)
        assertTrue(limited.limitReached)
        val focused = BlockPolicy.decide(c.copy(focusUntil = now + min), insta.packageName, now, 3, 12 * 60, emptyMap(), opensToday = 0)
        assertIs<Decision.Intercept>(focused)
        assertTrue(focused.focus)
    }

    @Test
    fun lockedAppStillFollowsTheSchedule() {
        val c = base.copy(apps = listOf(insta.copy(locked = true)), schedule = office, enabled = false)
        assertIs<Decision.Intercept>(decide(c, day = 3))
        assertEquals(Decision.Allow, decide(c, day = 6))
    }

    @Test
    fun uninstallKeepsTheLockInTheArchiveButReinstallDropsIt() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta.copy(locked = true)) }
        assertTrue(store.archiveIfWatched(insta.packageName))
        assertTrue(store.config.value.archived(insta.packageName)!!.locked)
        assertTrue(store.restoreIfArchived(insta.packageName))
        assertFalse(store.config.value.app(insta.packageName)!!.locked)
    }

    @Test
    fun statsKeepTheLabelAfterTheAppIsForgotten() {
        val store = MonkStore(InMemoryStore()).apply { upsertApp(insta) }
        store.recordIntercepted(insta.packageName)
        assertEquals("Instagram", store.stats.value.labels[insta.packageName])
        assertTrue(store.archiveIfWatched(insta.packageName))
        store.forgetArchived(insta.packageName)
        assertNull(store.config.value.archived(insta.packageName))
        assertEquals("Instagram", store.stats.value.labels[insta.packageName])
        store.resetStats()
        assertTrue(store.stats.value.labels.isEmpty())
    }

    @Test
    fun timersThatNeedNoReJudgeAreNotVerdictChanges() {
        // A focus or strict end never flips Allow → Intercept for an app the user is inside, so
        // only rule / schedule boundaries and a break end are re-judge moments.
        val c = base.copy(schedule = office, focusUntil = now + 5 * min, strictUntil = now + 7 * min)
        assertEquals(6 * 60 * 60_000L, BlockPolicy.millisToNextChange(c, insta, 3, 12 * 60, 0, nowMillis = now))
        val onBreak = c.copy(focusUntil = 0, pausedUntil = now + 2 * min)
        assertEquals(2 * min, BlockPolicy.millisToNextChange(onBreak, insta, 3, 12 * 60, 0, nowMillis = now))
        val scheduledOff = c.copy(focusUntil = 0)
        assertEquals(13 * 60 * 60_000L, BlockPolicy.millisToNextChange(scheduledOff, insta, 3, 20 * 60, 0, nowMillis = now))
    }

    @Test
    fun clockShiftKeepsTheBreakButNotTheSchedule() {
        val c = base.copy(schedule = office, pausedUntil = now + 10 * min, pauseStartedAt = now)
        val shifted = c.shifted(3 * 60 * min)
        assertEquals(now + 3 * 60 * min + 10 * min, shifted.pausedUntil)
        // Wall clock now says 20:05: the schedule is off whatever the break says.
        assertEquals(ProtectionState.SCHEDULED_OFF, shifted.state(now + 3 * 60 * min, 3, 20 * 60 + 5))
        assertEquals(ProtectionState.BREAK, shifted.state(now + 3 * 60 * min, 3, 15 * 60))
    }
}
