package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.data.InMemoryStore
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.model.AppDayStats
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.BlockPolicy
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.DayStats
import com.mdportnov.monk.shared.model.RuleMode
import com.mdportnov.monk.shared.model.Stats
import com.mdportnov.monk.shared.model.ThemeMode
import com.mdportnov.monk.shared.model.TimeRule
import com.mdportnov.monk.shared.model.TimeWindow
import kotlin.random.Random
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class StressAndCornerCaseTest {
    private val insta = BlockedApp("com.instagram.android", "Instagram", BlockMode.DELAY)

    // --- persistence: forward compatibility ---

    @Test
    fun unknownEnumConstantFromANewerVersionDoesNotDropTheWatchList() {
        val kv = InMemoryStore()
        // A newer build added RuleMode.WARN and ThemeMode.OLED; the user then installed this build.
        kv.putString(
            "config",
            """{"schemaVersion":1,"apps":[{"packageName":"com.instagram.android","label":"Instagram","mode":"BLOCK",
               "rules":[{"id":1,"mode":"WARN","days":[1],"startMinute":60,"endMinute":120}]}],"theme":"OLED"}""",
        )
        var failed: String? = null
        val store = MonkStore(kv, onLoadFailure = { key, _ -> failed = key })
        assertNull(failed, "a single unknown enum constant must not throw the whole config away")
        val app = assertNotNull(store.config.value.app("com.instagram.android"))
        assertEquals(BlockMode.BLOCK, app.mode)
        assertEquals(1, app.rules.size)
        assertEquals(RuleMode.BLOCK, app.rules.single().mode) // property default
        assertEquals(ThemeMode.SYSTEM, store.config.value.theme)
    }

    // --- archive cap ---

    @Test
    fun archiveCapIsFifoAndDropsTheOldestSettingsSilently() {
        val store = MonkStore(InMemoryStore())
        repeat(101) { i ->
            store.addApp("pkg$i", "App $i")
            store.upsertApp(store.config.value.app("pkg$i")!!.copy(dailyLimit = i + 1))
            store.removeApp("pkg$i")
        }
        assertEquals(100, store.config.value.archivedApps.size)
        assertNull(store.config.value.archived("pkg0"), "the oldest archived entry is the one evicted")
        assertEquals(2, store.config.value.archived("pkg1")?.dailyLimit)
        assertEquals(101, store.config.value.archived("pkg100")?.dailyLimit)
        // Re-adding pkg0 comes back with factory settings: the archive promise is broken at the cap.
        store.addApp("pkg0", "App 0")
        assertNull(store.config.value.app("pkg0")!!.dailyLimit)
    }

    // --- block coverage that never ends ---

    @Test
    fun permanentBlockRuleReportsNoEndWithinAWeek() {
        val always = insta.copy(rules = listOf(TimeRule(1, RuleMode.BLOCK, startMinute = 0, endMinute = 0)))
        assertEquals(RuleMode.BLOCK, always.activeRule(3, 12 * 60)?.mode)
        // null here means "not within 7 days", the same null the caller gets for "no block rule at
        // all" — InterceptSession.uiFor treats it as the latter and draws a pause countdown.
        assertNull(blockEnd(always, 3, 12 * 60))
        val sixDays = insta.copy(rules = (1..6).map { d -> TimeRule(d.toLong(), RuleMode.BLOCK, setOf(d), 0, 0) })
        assertEquals(6 * TimeWindow.DAY - 12 * 60, blockEnd(sixDays, 1, 12 * 60))
    }

    // --- blockEndsInMinutes: cost and a boundary-walk reference ---

    /** The merged walk over an app on its own: no routines, no schedule, so rules alone decide. */
    private fun blockEnd(app: BlockedApp, dayIso: Int, minuteOfDay: Int): Int? =
        BlockPolicy.blockEndsInMinutes(MonkConfig(apps = listOf(app)), app, dayIso, minuteOfDay)

    /** Block coverage only changes at a rule boundary or at midnight; step boundary to boundary. */
    private fun blockEndsByBoundaries(app: BlockedApp, dayIso: Int, minuteOfDay: Int): Int? {
        if (app.activeRule(dayIso, minuteOfDay)?.mode != RuleMode.BLOCK) return null
        val bounds = (app.rules.flatMap { listOf(it.startMinute, it.endMinute) } + 0).distinct()
        var day = dayIso
        var minute = minuteOfDay
        var elapsed = 0
        while (true) {
            val step = bounds.minOf { b -> if (b > minute) b - minute else TimeWindow.DAY - minute + b }
            elapsed += step
            if (elapsed > 7 * TimeWindow.DAY) return null
            minute = (minute + step) % TimeWindow.DAY
            if (minute == 0) day = TimeWindow.nextDay(day)
            if (app.activeRule(day, minute)?.mode != RuleMode.BLOCK) return elapsed
        }
    }

    private fun randomRules(rnd: Random, n: Int) = (1..n).map { i ->
        TimeRule(
            id = i.toLong(),
            mode = RuleMode.entries[rnd.nextInt(3)],
            days = (1..7).filter { rnd.nextBoolean() }.toSet().ifEmpty { setOf(rnd.nextInt(1, 8)) },
            startMinute = rnd.nextInt(0, TimeWindow.DAY),
            endMinute = rnd.nextInt(0, TimeWindow.DAY),
        )
    }

    @Test
    fun boundaryWalkAgreesWithMinuteWalkOverRandomRuleSets() {
        val rnd = Random(20260914)
        var checked = 0
        repeat(300) {
            val app = insta.copy(rules = randomRules(rnd, rnd.nextInt(1, 51)))
            repeat(20) {
                val day = rnd.nextInt(1, 8)
                val minute = rnd.nextInt(0, TimeWindow.DAY)
                assertEquals(
                    blockEnd(app, day, minute),
                    blockEndsByBoundaries(app, day, minute),
                    "rules=${app.rules} day=$day minute=$minute",
                )
                checked++
            }
        }
        assertTrue(checked >= 6000)
    }

    @Test
    fun blockEndsCostWithFiftyOverlappingRules() {
        // Fifty BLOCK rules that together cover the whole week except one minute: the worst case,
        // a walk of ~10 080 minutes × activeRule over 50 rules on the main thread.
        val rules = (0 until 50).map { i ->
            TimeRule(i.toLong(), RuleMode.BLOCK, startMinute = (i * 29) % TimeWindow.DAY, endMinute = (i * 29 + 700) % TimeWindow.DAY)
        }
        val app = insta.copy(rules = rules)
        val mark = TimeSource.Monotonic.markNow()
        var last: Int? = null
        repeat(10) { last = blockEnd(app, 1, 0) }
        val minuteWalk = mark.elapsedNow() / 10
        val mark2 = TimeSource.Monotonic.markNow()
        var last2: Int? = null
        repeat(10) { last2 = blockEndsByBoundaries(app, 1, 0) }
        val boundaryWalk = mark2.elapsedNow() / 10
        assertEquals(last, last2)
        println("blockEndsInMinutes 50 rules: minute walk=$minuteWalk, boundary walk=$boundaryWalk, result=$last")
    }

    // --- stats at scale: 30 apps × 90 days ---

    private fun bigStats(apps: Int, days: Int, from: String = "2026-06-16"): Stats {
        val start = LocalDate.parse(from)
        return Stats(
            days = (0 until days).map { d ->
                val date = start.plus(d, DateTimeUnit.DAY).toString()
                val byApp = (0 until apps).associate { a ->
                    "com.example.app$a" to AppDayStats(intercepted = 12, turnedAway = 7, opened = 5, reasons = mapOf("BORED" to 2, "HABIT" to 3))
                }
                DayStats(date, intercepted = 12 * apps, turnedAway = 7 * apps, opened = 5 * apps, byApp = byApp, byHour = (0 until 24).associateWith { 15 })
            },
        )
    }

    @Test
    fun ninetyDaysOfThirtyAppsPayloadAndBumpCost() {
        val kv = InMemoryStore()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val stats = bigStats(apps = 30, days = 90)
        val blob = json.encodeToString(Stats.serializer(), stats)
        kv.putString("stats", blob)
        val mark = TimeSource.Monotonic.markNow()
        val store = MonkStore(kv)
        val load = mark.elapsedNow()
        assertEquals(90, store.stats.value.days.size)
        val mark2 = TimeSource.Monotonic.markNow()
        repeat(10) { store.recordIntercepted("com.example.app0") }
        val bump = mark2.elapsedNow() / 10
        val dates = store.stats.value.days.map { it.date }
        val mark3 = TimeSource.Monotonic.markNow()
        val perApp = store.stats.value.perApp(dates)
        val reasons = store.stats.value.reasons(dates)
        val byHour = store.stats.value.byHour(dates)
        val streak = store.stats.value.walkAwayStreak(dates)
        val aggregate = mark3.elapsedNow()
        assertEquals(30, perApp.size)
        // The bump added today as a 91st day, so the oldest day (with its reasons) was evicted.
        assertEquals(2 * 30 * 89, reasons["BORED"])
        assertEquals(24, byHour.size)
        assertTrue(streak >= 89)
        assertTrue(store.stats.value.days.size <= 91)
        println("stats 30 apps × 90 days: payload=${blob.length / 1024} KB, load=$load, bump(encode+write)=$bump, aggregate(perApp+reasons+byHour+streak)=$aggregate")
    }

    @Test
    fun statsKeepAtMostNinetyDaysAndPreferTheNewestDates() {
        val kv = InMemoryStore()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        // 90 old days plus one dated far in the future (a clock/zone move already wrote it).
        val old = bigStats(apps = 1, days = 90, from = "2020-01-01")
        val future = DayStats("2999-12-31", intercepted = 1)
        kv.putString("stats", json.encodeToString(Stats.serializer(), old.copy(days = old.days + future)))
        val store = MonkStore(kv)
        store.recordIntercepted("com.example.app0")
        val days = store.stats.value.days
        assertEquals(90, days.size)
        assertEquals("2999-12-31", days.last().date, "a future-dated day is never evicted: it is always the newest")
        assertNull(days.firstOrNull { it.date == "2020-01-01" })
    }
}
