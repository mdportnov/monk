package com.mdportnov.monk.shared.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.i18n.Strings
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.ScreenTimeReport
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.platform.MonkPlatform
import com.mdportnov.monk.shared.ui.LocalOpenRoute
import com.mdportnov.monk.shared.ui.Route
import com.mdportnov.monk.shared.ui.components.Hint
import com.mdportnov.monk.shared.ui.components.MonkCard
import com.mdportnov.monk.shared.ui.components.Pill
import com.mdportnov.monk.shared.ui.components.SectionTitle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.math.abs

/**
 * The platform's screen-time report for the last [days] days, re-read when the inputs change
 * and on [tick]. The previous report stays on screen while a new one is computed, so a tab
 * switch never flashes an empty card.
 */
@Composable
fun rememberScreenTime(platform: MonkPlatform, days: Int, packages: Set<String>, granted: Boolean, tick: Any): ScreenTimeReport? {
    var report by remember { mutableStateOf<ScreenTimeReport?>(null) }
    LaunchedEffect(platform, days, packages, granted, tick) {
        report = when {
            !platform.supportsBlocking -> ScreenTimeReport.Unsupported
            !granted -> ScreenTimeReport.NotGranted
            else -> platform.screenTime(days, packages)
        }
    }
    return report
}

/** Differences under this are "about the same": a couple of minutes is noise, not a trend. */
internal const val SAME_THRESHOLD_MS = 5 * 60_000L

/** How many apps the Stats tab shows before "Show all". */
private const val SECTION_TOP_N = 5

/**
 * "2 h 10 min this week, 35 min less than last week" — or the daily average when the previous
 * period is not fully on record, so the comparison is never made against a half-empty week.
 */
internal fun screenTimeConclusion(
    s: Strings,
    current: Long,
    previous: Long?,
    periodWord: String,
    previousWord: String,
    coveredDaysInPeriod: Int,
): String {
    val head = "${s.duration(current)} $periodWord"
    if (previous != null) {
        val diff = current - previous
        val tail = when {
            abs(diff) < SAME_THRESHOLD_MS -> s.sameAs(previousWord)
            diff < 0 -> s.lessThan(s.duration(-diff), previousWord)
            else -> s.moreThan(s.duration(diff), previousWord)
        }
        return "$head, $tail"
    }
    if (coveredDaysInPeriod > 1) return "$head, ${s.perDayAverage(s.duration(current / coveredDaysInPeriod))}"
    return head
}

/** The two halves of a report window: the period shown and the period before it, for the comparison. */
internal class Periods(val current: List<String>, val previous: List<String>, val previousCovered: Boolean)

internal fun periods(report: ScreenTimeReport, periodDays: Int, allTime: Boolean): Periods {
    val covered = report.coveredDates
    val current = if (allTime) report.dates.filter { it in covered } else report.dates.takeLast(periodDays)
    val previous = if (allTime) emptyList() else report.dates.dropLast(periodDays).takeLast(periodDays)
    return Periods(current, previous, previous.isNotEmpty() && previous.all { it in covered })
}

/**
 * Screen time in the Stats tab: the watched apps' total against the phone's, one sentence that
 * says whether it is going up or down, a day-by-day chart, and the phone's top apps — every app,
 * not only the watched ones, so the biggest sink is never hidden by not being on the list.
 * Without usage access the card explains what it is and offers the system page.
 */
@Composable
fun ScreenTimeSection(
    platform: MonkPlatform,
    store: MonkStore,
    report: ScreenTimeReport?,
    periodDays: Int,
    allTime: Boolean,
) {
    val s = strings
    if (report == null || !report.supported) return
    val open = LocalOpenRoute.current
    SectionTitle(s.screenTime)
    if (!report.granted) {
        UsageAccessOptInCard(platform)
        return
    }

    val covered = report.coveredDates
    val p = remember(report, periodDays, allTime) { periods(report, periodDays, allTime) }
    val current = p.current
    val watched = report.watchedTotal(current)
    val phone = report.phoneTotal(current)
    val periodWord = if (periodDays == 1) s.todayWord else s.thisWeekWord
    val previousWord = if (periodDays == 1) s.yesterdayWord else s.lastWeekWord
    val ranked = remember(report, current) { report.ranked(current) }

    MonkCard(onClick = { open(Route.ScreenTime) }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(s.duration(watched), style = MaterialTheme.typography.headlineMedium)
                Text(s.screenTimeWatched, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(s.duration(phone), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.wholePhone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (phone == 0L) {
            Hint(if (current.any { it in covered }) s.screenTimeNothing else s.screenTimeNoRecord)
        } else if (watched == 0L) {
            Hint(s.screenTimeEmpty)
        } else {
            val line = if (allTime) {
                "${s.screenTimeLastDays(current.size)} · ${s.perDayAverage(s.duration(watched / current.size.coerceAtLeast(1)))}"
            } else {
                screenTimeConclusion(s, watched, if (p.previousCovered) report.watchedTotal(p.previous) else null, periodWord, previousWord, current.count { it in covered })
            }
            Text(line, style = MaterialTheme.typography.bodyMedium)
            Pill(s.screenTimeShare((watched * 100 / phone).toInt()), MaterialTheme.colorScheme.primary)
        }
        if (periodDays > 1 || allTime) {
            val shown = if (allTime) report.dates.takeLast(30) else current
            TimeBarChart(
                dates = shown,
                watched = shown.map { report.watchedByDate(it) },
                phone = shown.map { report.phoneByDate[it] ?: 0L },
                covered = shown.map { it in covered },
            )
            DayLabels(shown)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartLegend(MaterialTheme.colorScheme.primary, s.screenTimeWatched)
                ChartLegend(MaterialTheme.colorScheme.secondaryContainer, s.wholePhone)
            }
        }
        Hint(s.screenTimeHint)
        CoverageHint(report, current, allTime || (periodDays > 1 && !p.previousCovered))
    }

    if (ranked.isNotEmpty()) {
        MonkCard {
            ranked.take(SECTION_TOP_N).forEach { (pkg, ms) ->
                key(pkg) {
                    AppTimeRow(
                        report = report,
                        store = store,
                        packageName = pkg,
                        millis = ms,
                        of = phone,
                        previous = if (p.previousCovered) report.appTotal(pkg, p.previous) else null,
                        onClick = { open(Route.ScreenTimeApp(pkg)) },
                    )
                }
            }
            AppListHint(showDelta = p.previousCovered)
            TextButton(onClick = { open(Route.ScreenTime) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (ranked.size > SECTION_TOP_N) "${s.showAll} · ${ranked.size}" else s.showAll)
            }
        }
    }
}

/** What the bar, the "Add" and the +/− pills in an app list mean; the pills line only when a previous period exists. */
@Composable
internal fun AppListHint(showDelta: Boolean) {
    val s = strings
    Hint(if (showDelta) "${s.appTimeHint} ${s.deltaHint}" else s.appTimeHint)
}

/** The card that asks for usage access; shared by the Stats tab and the full page. */
@Composable
internal fun UsageAccessOptInCard(platform: MonkPlatform) {
    val s = strings
    MonkCard {
        Text(s.screenTimeOptInTitle, style = MaterialTheme.typography.titleMedium)
        Text(s.screenTimeOptInBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = platform::openUsageAccessSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(s.allowUsageAccess) }
    }
}

/**
 * Says plainly how far back the numbers go: the first day on record when the period reaches
 * past it, otherwise nothing. Never a made-up "last N days" when the record is shorter.
 */
@Composable
internal fun CoverageHint(report: ScreenTimeReport, current: List<String>, show: Boolean) {
    val s = strings
    if (!show) return
    val first = current.firstOrNull { it in report.coveredDates } ?: return
    val missingBefore = current.first() != first
    when {
        missingBefore -> Hint(s.notRecordedBefore(s.dayDate(first)))
        report.recordedSince != null -> Hint(s.recordedSince(s.dayDate(report.recordedSince)))
    }
}

/**
 * One app: icon, name, time, its share of [of], the change against the previous period, and
 * whether Monk watches it — with a one-tap way to start.
 */
@Composable
internal fun AppTimeRow(
    report: ScreenTimeReport,
    store: MonkStore,
    packageName: String,
    millis: Long,
    of: Long,
    previous: Long?,
    onClick: (() -> Unit)?,
) {
    val s = strings
    val config by store.config.collectAsStateWithLifecycle()
    val onList = config.app(packageName) != null
    val label = report.label(packageName)
    Row(
        (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName, 32.dp)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (previous != null) {
                    val diff = millis - previous
                    if (abs(diff) >= SAME_THRESHOLD_MS) {
                        Pill(
                            (if (diff < 0) "−" else "+") + s.duration(abs(diff)),
                            if (diff < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Text(s.duration(millis), style = MaterialTheme.typography.titleSmall, color = if (onList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShareBar(
                    fraction = millis.toFloat() / of.coerceAtLeast(1L),
                    color = if (onList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    track = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                WatchBadge(onList = onList, onAdd = { store.addApp(packageName, label) })
            }
        }
    }
}

/** "on your list" or a small "Add" — adding is allowed even under strict mode; only removal is not. */
@Composable
internal fun WatchBadge(onList: Boolean, onAdd: () -> Unit) {
    val s = strings
    if (onList) {
        Pill(s.onYourList, MaterialTheme.colorScheme.primary)
    } else {
        // A real touch target (40 dp tall) around a small label.
        Text(
            "+ ${s.addToList}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onAdd).heightIn(min = 40.dp).padding(horizontal = 8.dp).wrapContentHeight(Alignment.CenterVertically),
        )
    }
}

@Composable
internal fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(10.dp).height(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ShareBar(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(4.dp.toPx())
        drawRoundRect(track, Offset.Zero, size, r)
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0) drawRoundRect(color, Offset.Zero, Size(w, size.height), r)
    }
}

/** Weekday initials under a chart of up to two weeks; longer ranges get first/last dates. */
@Composable
internal fun DayLabels(dates: List<String>) {
    val s = strings
    if (dates.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (dates.size <= 14) {
            dates.forEach { d ->
                val dow = LocalDate.parse(d).dayOfWeek.isoDayNumber
                Text(
                    s.dayShort[dow - 1].take(if (dates.size > 7) 1 else 2),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Text(s.dayDate(dates.first()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.dayDate(dates.last()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Per day: the phone's whole screen time as a pale column, the watched apps' share on top of it.
 * Uncovered days stay as bare track. With [onSelect] a tap picks a day; the others step back.
 */
@Composable
internal fun TimeBarChart(
    dates: List<String>,
    watched: List<Long>,
    phone: List<Long>,
    covered: List<Boolean>,
    selected: Int = -1,
    onSelect: ((Int) -> Unit)? = null,
    fgColor: Color = MaterialTheme.colorScheme.primary,
) {
    val fg = fgColor
    val bg = MaterialTheme.colorScheme.secondaryContainer
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val outline = MaterialTheme.colorScheme.onSurface
    val max = (phone.indices.maxOfOrNull { maxOf(phone[it], watched[it]) } ?: 1L).coerceAtLeast(1L)
    val n = dates.size.coerceAtLeast(1)
    val tap = if (onSelect == null) Modifier else Modifier.pointerInput(n) {
        detectTapGestures { off ->
            val gap = 4.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            onSelect((off.x / (w + gap)).toInt().coerceIn(0, n - 1))
        }
    }
    Canvas(Modifier.fillMaxWidth().height(120.dp).then(tap)) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val r = CornerRadius(3.dp.toPx())
        for (i in 0 until n) {
            val x = i * (w + gap)
            val dim = selected >= 0 && selected != i
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), r)
            if (i >= covered.size || !covered[i]) continue
            val hAll = size.height * phone[i] / max
            if (hAll > 0) drawRoundRect(bg, Offset(x, size.height - hAll), Size(w, hAll), r, alpha = if (dim) 0.45f else 1f)
            val hW = size.height * watched[i] / max
            if (hW > 0) drawRoundRect(fg, Offset(x, size.height - hW), Size(w, hW), r, alpha = if (dim) 0.45f else 1f)
            if (selected == i) drawRoundRect(outline, Offset(x, size.height - 3.dp.toPx()), Size(w, 3.dp.toPx()), r)
        }
    }
}
