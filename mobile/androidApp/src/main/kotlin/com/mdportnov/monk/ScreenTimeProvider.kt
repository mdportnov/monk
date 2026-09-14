package com.mdportnov.monk

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import com.mdportnov.monk.shared.data.ScreenTimeArchive
import com.mdportnov.monk.shared.data.dayBounds
import com.mdportnov.monk.shared.data.lastDates
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.model.DayBounds
import com.mdportnov.monk.shared.model.ScreenTimeReport
import com.mdportnov.monk.shared.model.UsageEvent
import com.mdportnov.monk.shared.model.UsageEventKind
import com.mdportnov.monk.shared.model.foldForegroundByDay
import com.mdportnov.monk.shared.model.mergeScreenTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Screen time from the system's usage-event log, the same source Digital Wellbeing reads.
 *
 * Events, not `queryUsageStats` buckets: the daily buckets roll over whenever the service
 * started that day (15:52 on the owner's phone), so a bucket is not a calendar day. Resume /
 * pause pairs clipped to local midnight are. The event log is kept for about ten days on most
 * devices; days before that come from [archive], Monk's own copy written on every read, and
 * days neither has come back uncovered rather than guessed from weekly buckets.
 */
class ScreenTimeProvider(private val app: Context, private val archive: ScreenTimeArchive) {
    private class Cached(val days: Int, val at: Long, val byPackage: Map<String, Map<String, Long>>, val dates: List<String>, val covered: Set<String>)

    private var cache: Cached? = null
    private val lock = Mutex()

    fun granted(): Boolean {
        val ops = app.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
            else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return mode == AppOpsManager.MODE_ALLOWED ||
            (mode == AppOpsManager.MODE_DEFAULT && app.checkPermission(android.Manifest.permission.PACKAGE_USAGE_STATS, Process.myPid(), Process.myUid()) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    suspend fun report(days: Int, packages: Set<String>): ScreenTimeReport {
        if (!granted()) return ScreenTimeReport.NotGranted
        val wanted = days.coerceIn(1, ScreenTimeArchive.MAX_DAYS)
        val os = withContext(Dispatchers.Default) { folded(minOf(wanted, OS_WINDOW_DAYS)) }
        val visible = withContext(Dispatchers.Default) { visiblePackages(packages) }
        val osByApp = os.byPackage.filterKeys { it in visible }
        // Main thread: the archive is a plain map behind SharedPreferences, like the store.
        withContext(Dispatchers.Main) { archive.record(osByApp, os.covered) }
        val dates = lastDates(wanted)
        val (merged, covered) = mergeScreenTime(osByApp, os.covered, archive.days, dates)
        return ScreenTimeReport(
            supported = true,
            granted = true,
            dates = dates,
            coveredDates = covered,
            byApp = merged,
            watched = packages,
            labels = labels(merged.keys),
            recordedSince = archive.earliestDate,
        )
    }

    /**
     * What counts as "an app" in the list: anything with a launcher icon, plus whatever is on the
     * watch list; minus the packages Monk never gates (launcher, settings, dialer, itself) and
     * keyboards, which sit on top of other apps and would double-count their time.
     */
    private fun visiblePackages(watched: Set<String>): Set<String> {
        val pm = app.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val listed = runCatching { pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL).map { it.activityInfo.packageName } }.getOrDefault(emptyList())
        val imes = runCatching { (app.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).inputMethodList.map { it.packageName } }.getOrDefault(emptyList())
        val excluded = SystemPackages.essential(app) + imes + "android"
        return (listed.toSet() + watched) - excluded
    }

    private val labelCache = HashMap<String, String>()

    private fun labels(packages: Collection<String>): Map<String, String> {
        val pm = app.packageManager
        return packages.associateWith { pkg ->
            labelCache.getOrPut(pkg) {
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            }
        }
    }

    private suspend fun folded(days: Int): Cached = lock.withLock {
        val now = nowMillis()
        val dates = lastDates(days)
        cache?.takeIf { it.days == days && it.dates == dates && now - it.at < TTL_MS }?.let { return it }
        val bounds = dayBounds(dates)
        val events = ArrayList<UsageEvent>()
        var earliest = Long.MAX_VALUE
        val usm = app.getSystemService(UsageStatsManager::class.java)
        if (usm != null) runCatching {
            // One day before the window: an app already on screen at its start still gets its
            // resume event, and the earliest event tells whether the whole window is on record.
            val q = usm.queryEvents(bounds.first().start - DAY_MS, now)
            val e = UsageEvents.Event()
            while (q.getNextEvent(e)) {
                if (e.timeStamp < earliest) earliest = e.timeStamp
                val kind = when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventKind.RESUMED
                    // A stop without a pause happens (Instagram's link-handler stack): both end the activity's turn on screen.
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> UsageEventKind.PAUSED
                    UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP,
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> UsageEventKind.RESET
                    else -> null
                } ?: continue
                events += UsageEvent(if (kind == UsageEventKind.RESET) "" else e.packageName ?: continue, e.className, e.timeStamp, kind)
            }
        }
        val covered = coveredDates(bounds, earliest, now)
        val folded = foldForegroundByDay(events, bounds, now)
        Cached(days, now, folded, dates, covered).also { cache = it }
    }

    /** Clears the cache so the next read after the grant (or a resume) is fresh. */
    fun invalidate() { cache = null }

    fun clearArchive() { archive.clear(); cache = null }

    companion object {
        /** How far back the OS is asked; its log is ~10 days deep, the rest is the archive's job. */
        const val OS_WINDOW_DAYS = 14
        private const val TTL_MS = 60_000L
        private const val DAY_MS = 24 * 60 * 60_000L

        private const val COVERAGE_SLACK_MS = 60 * 60_000L

        /**
         * Days the log covers in full: from the first event the device still holds (the oldest
         * daily file starts mid-day, so that day is dropped) up to today.
         */
        fun coveredDates(bounds: List<DayBounds>, earliestEvent: Long, now: Long): Set<String> =
            if (earliestEvent == Long.MAX_VALUE) emptySet()
            else bounds.filter { earliestEvent <= it.start + COVERAGE_SLACK_MS && it.start <= now }.map { it.date }.toSet()
    }
}
