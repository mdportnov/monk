package com.mdportnov.monk.shared.model

/**
 * Screen time as the OS measured it (Android: UsageStatsManager, behind "Usage access"), topped
 * up from Monk's own archive for the days the OS has already forgotten.
 * Millis of foreground time per local day; [dates] is the requested window, oldest first, ending
 * today; [coveredDates] is the part of it that is on record (OS log or archive).
 */
data class ScreenTimeReport(
    val supported: Boolean,
    val granted: Boolean,
    val dates: List<String> = emptyList(),
    val coveredDates: Set<String> = emptySet(),
    /** Every app on the phone (launcher, system UI, keyboards, Monk itself excluded): package → date → foreground millis. */
    val byApp: Map<String, Map<String, Long>> = emptyMap(),
    /** The packages on the watch list; the rest of [byApp] is "the phone". */
    val watched: Set<String> = emptySet(),
    /** Display names for [byApp]; a package that is gone from the phone falls back to its name. */
    val labels: Map<String, String> = emptyMap(),
    /** First day the archive holds, if any: what "all time" honestly means. */
    val recordedSince: String? = null,
) {
    val available get() = supported && granted

    /** Whole phone per day, derived once. */
    val phoneByDate: Map<String, Long> by lazy {
        val out = HashMap<String, Long>()
        byApp.values.forEach { perDay -> perDay.forEach { (d, ms) -> out[d] = (out[d] ?: 0L) + ms } }
        out
    }

    fun label(packageName: String) = labels[packageName] ?: packageName

    fun appTotal(packageName: String, dates: Collection<String>): Long =
        byApp[packageName]?.let { m -> dates.sumOf { m[it] ?: 0L } } ?: 0L

    fun watchedTotal(dates: Collection<String>): Long = watched.sumOf { appTotal(it, dates) }

    fun watchedByDate(date: String): Long = watched.sumOf { byApp[it]?.get(date) ?: 0L }

    fun phoneTotal(dates: Collection<String>): Long = dates.sumOf { phoneByDate[it] ?: 0L }

    /** Apps with any time over [dates], most used first. */
    fun ranked(dates: Collection<String>): List<Pair<String, Long>> =
        byApp.keys.map { it to appTotal(it, dates) }.filter { it.second > 0 }.sortedByDescending { it.second }

    companion object {
        val Unsupported = ScreenTimeReport(supported = false, granted = false)
        val NotGranted = ScreenTimeReport(supported = true, granted = false)
    }
}

/**
 * Lays the OS log over the archive for [dates]: a day the OS still covers comes from the OS
 * (it is the fresher, complete measurement), any other day comes from the archive if it has
 * one. Returns the merged per-app map and the set of days that ended up on record.
 */
fun mergeScreenTime(
    osByApp: Map<String, Map<String, Long>>,
    osCovered: Set<String>,
    archive: Map<String, Map<String, Long>>,
    dates: List<String>,
): Pair<Map<String, Map<String, Long>>, Set<String>> {
    val out = HashMap<String, HashMap<String, Long>>()
    val covered = HashSet<String>()
    for (d in dates) {
        val source: Map<String, Long> = when {
            d in osCovered -> {
                covered += d
                osByApp.mapNotNull { (pkg, perDay) -> perDay[d]?.let { pkg to it } }.toMap()
            }
            d in archive -> { covered += d; archive.getValue(d) }
            else -> continue
        }
        source.forEach { (pkg, ms) -> if (ms > 0) out.getOrPut(pkg) { HashMap() }[d] = ms }
    }
    return out to covered
}

/** [RESET]: nothing is on screen any more — shutdown, boot, screen off, lock screen up. */
enum class UsageEventKind { RESUMED, PAUSED, RESET }

/** One foreground-lifecycle event, as the platform reports it. */
data class UsageEvent(val packageName: String, val className: String?, val timestamp: Long, val kind: UsageEventKind)

/** A local day as an epoch-millis half-open range [start, end). */
data class DayBounds(val date: String, val start: Long, val end: Long)

/**
 * Folds resume / pause events into foreground millis per package per local day.
 *
 * A package is in the foreground from the moment its first activity resumes until its last
 * resumed activity pauses or stops, so an activity handing over to another one of the same
 * app does not end the session. The foreground is exclusive: another package resuming ends
 * every other open session at that moment (Instagram opens a link-handler stack of four
 * activities and stops three of them without ever pausing them; only the next app's resume
 * says the truth). [UsageEventKind.RESET] (shutdown, boot, screen off, lock) closes everything,
 * which also bounds the damage of a pause event the OS never wrote (a picture-in-picture window
 * left open stays "resumed" for days); a session still open when the list ends is closed at
 * [now]. Sessions are clipped to [days], so a stretch that crosses midnight lands on both days
 * in the right proportion.
 */
fun foldForegroundByDay(events: List<UsageEvent>, days: List<DayBounds>, now: Long): Map<String, Map<String, Long>> {
    val result = HashMap<String, HashMap<String, Long>>()
    val active = HashMap<String, MutableSet<String>>()
    val openSince = HashMap<String, Long>()

    fun add(pkg: String, start: Long, end: Long) {
        if (end <= start) return
        val perDay = result.getOrPut(pkg) { HashMap() }
        for (d in days) {
            val from = maxOf(start, d.start)
            val to = minOf(end, d.end)
            if (to > from) perDay[d.date] = (perDay[d.date] ?: 0L) + (to - from)
        }
    }

    fun close(pkg: String, at: Long) {
        openSince.remove(pkg)?.let { add(pkg, it, at) }
        active.remove(pkg)
    }

    for (e in events.sortedBy { it.timestamp }) {
        when (e.kind) {
            UsageEventKind.RESUMED -> {
                openSince.keys.filter { it != e.packageName }.forEach { close(it, e.timestamp) }
                active.getOrPut(e.packageName) { HashSet() }.add(e.className ?: "")
                openSince.getOrPut(e.packageName) { e.timestamp }
            }
            UsageEventKind.PAUSED -> {
                val set = active[e.packageName] ?: continue
                if (e.className == null) set.clear() else set.remove(e.className)
                if (set.isEmpty()) close(e.packageName, e.timestamp)
            }
            UsageEventKind.RESET -> openSince.keys.toList().forEach { close(it, e.timestamp) }
        }
    }
    openSince.keys.toList().forEach { close(it, now) }
    return result
}
