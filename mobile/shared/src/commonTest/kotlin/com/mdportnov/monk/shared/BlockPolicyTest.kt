package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.data.InMemoryStore
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.BlockPolicy
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.Decision
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.Schedule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class BlockPolicyTest {
    private val insta = BlockedApp("com.instagram.android", "Instagram", BlockMode.DELAY)
    private val config = MonkConfig(apps = listOf(insta))

    private fun decide(cfg: MonkConfig = config, pkg: String = insta.packageName, now: Long = 1_000_000L, day: Int = 3, minute: Int = 12 * 60, allow: Map<String, Long> = emptyMap()) =
        BlockPolicy.decide(cfg, pkg, now, day, minute, allow)

    @Test
    fun interceptsWatchedApp() {
        val d = decide()
        assertIs<Decision.Intercept>(d)
        assertEquals(insta, d.app)
    }

    @Test
    fun ignoresUnknownAndDisabled() {
        assertEquals(Decision.Allow, decide(pkg = "com.example.other"))
        assertEquals(Decision.Allow, decide(cfg = config.copy(enabled = false)))
    }

    @Test
    fun allowanceWinsUntilExpiry() {
        assertEquals(Decision.Allow, decide(now = 100, allow = mapOf(insta.packageName to 200)))
        assertIs<Decision.Intercept>(decide(now = 200, allow = mapOf(insta.packageName to 200)))
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
    fun storeRoundTripsConfigAndAllowances() {
        val kv = InMemoryStore()
        val store = MonkStore(kv)
        store.upsertApp(insta.copy(mode = BlockMode.BLOCK, delaySeconds = 30))
        store.grantAllowance(insta.packageName, minutes = 5, now = 0)
        val reloaded = MonkStore(kv)
        assertEquals(BlockMode.BLOCK, reloaded.config.value.app(insta.packageName)?.mode)
        assertEquals(30, reloaded.config.value.app(insta.packageName)?.delaySeconds)
        assertEquals(5 * 60_000L, reloaded.allowances.value[insta.packageName])
    }
}
