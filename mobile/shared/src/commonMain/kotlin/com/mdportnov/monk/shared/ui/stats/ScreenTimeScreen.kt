package com.mdportnov.monk.shared.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.localMoment
import com.mdportnov.monk.shared.i18n.Strings
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.ScreenTimeReport
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.platform.MonkPlatform
import com.mdportnov.monk.shared.ui.Motion
import com.mdportnov.monk.shared.ui.TopBarState
import com.mdportnov.monk.shared.ui.components.Counter
import com.mdportnov.monk.shared.ui.components.Hint
import com.mdportnov.monk.shared.ui.components.MonkCard
import com.mdportnov.monk.shared.ui.components.Pill
import com.mdportnov.monk.shared.ui.components.SectionTitle
import com.mdportnov.monk.shared.ui.components.Segments
import com.mdportnov.monk.shared.ui.components.glassTopBarInset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/** Ranges of the full page. [requestDays] is what the platform is asked for: twice the period, so the one before is on hand for the comparison. */
internal enum class TimeRange(val days: Int, val requestDays: Int, val allTime: Boolean = false) {
    Today(1, 2), Week(7, 14), Month(30, 60), All(365, 365, allTime = true);

    fun periodWord(s: Strings) = when (this) { Today -> s.todayWord; Week -> s.thisWeekWord; Month -> s.last30Word; All -> "" }
    fun previousWord(s: Strings) = when (this) { Today -> s.yesterdayWord; Week -> s.lastWeekWord; Month -> s.previous30Word; All -> "" }
}

@Composable
private fun rangeOptions(s: Strings) = listOf(TimeRange.Today to s.statsToday, TimeRange.Week to s.statsWeek, TimeRange.Month to s.stats30, TimeRange.All to s.statsAllTime)

/** The report for [range], refreshed on every return to the foreground and at midnight. */
@Composable
private fun rememberRangeReport(store: MonkStore, platform: MonkPlatform, range: TimeRange): Pair<ScreenTimeReport?, Set<String>> {
    val config by store.config.collectAsStateWithLifecycle()
    val permissions by platform.permissions.collectAsStateWithLifecycle()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        platform.refreshPermissions()
        resumeTick++
        onPauseOrDispose { }
    }
    val today = localMoment().dateIso
    val watched = remember(config.apps) { config.apps.map { it.packageName }.toSet() }
    val report = rememberScreenTime(platform, days = range.requestDays, packages = watched, granted = permissions.usageAccessGranted, tick = resumeTick to today)
    return report to watched
}

/** Page frame shared by the two screens: glass bar with a back arrow, scrolling column under it. */
@Composable
private fun PushedPage(title: String, onClose: () -> Unit, topBar: TopBarState, hazeState: HazeState, content: @Composable () -> Unit) {
    val back = strings.back
    SideEffect {
        topBar.set(
            title = title,
            visible = true,
            level = 10,
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, back) } },
        )
    }
    Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
        Box(Modifier.padding(PaddingValues(top = glassTopBarInset())).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

/**
 * The whole phone's screen time, browsable: a range, the total with the watched apps' share,
 * a day chart where a tap narrows the list to that day, and every app in order of time.
 */
@Composable
fun ScreenTimeScreen(
    store: MonkStore,
    platform: MonkPlatform,
    onClose: () -> Unit,
    onOpenApp: (String) -> Unit,
    topBar: TopBarState,
    hazeState: HazeState,
) {
    val s = strings
    var range by rememberSaveable { mutableStateOf(TimeRange.Week) }
    var selectedDate by rememberSaveable(range) { mutableStateOf<String?>(null) }
    val (report, _) = rememberRangeReport(store, platform, range)

    PushedPage(s.screenTime, onClose, topBar, hazeState) {
        Segments(options = rangeOptions(s), selected = range) { range = it }
        when {
            report == null -> Unit
            !report.granted -> UsageAccessOptInCard(platform)
            else -> {
                val covered = report.coveredDates
                val p = remember(report, range) { periods(report, range.days, range.allTime) }
                val current = p.current
                val shownDates = selectedDate?.let { listOf(it) } ?: current
                val phone = report.phoneTotal(current)
                val watched = report.watchedTotal(current)

                MonkCard {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(s.duration(phone), style = MaterialTheme.typography.headlineMedium)
                            Text(s.wholePhone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(s.duration(watched), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(s.screenTimeWatched, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (phone == 0L) {
                        Hint(if (current.any { it in covered }) s.screenTimeNothing else s.screenTimeNoRecord)
                    } else {
                        val line = if (range.allTime) {
                            "${s.screenTimeLastDays(current.size)} · ${s.perDayAverage(s.duration(phone / current.size.coerceAtLeast(1)))}"
                        } else {
                            screenTimeConclusion(s, phone, if (p.previousCovered) report.phoneTotal(p.previous) else null, range.periodWord(s), range.previousWord(s), current.count { it in covered })
                        }
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                        if (watched > 0) Pill(s.screenTimeListShare((watched * 100 / phone).toInt()), MaterialTheme.colorScheme.primary)
                    }
                    if (range != TimeRange.Today) {
                        val chart = if (range.allTime) current.takeLast(30) else current
                        TimeBarChart(
                            dates = chart,
                            watched = chart.map { report.watchedByDate(it) },
                            phone = chart.map { report.phoneByDate[it] ?: 0L },
                            covered = chart.map { it in covered },
                            selected = chart.indexOf(selectedDate),
                            onSelect = { i -> selectedDate = chart[i].takeIf { it != selectedDate } },
                        )
                        DayLabels(chart)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ChartLegend(MaterialTheme.colorScheme.primary, s.screenTimeWatched)
                            ChartLegend(MaterialTheme.colorScheme.secondaryContainer, s.wholePhone)
                        }
                    }
                    Hint(s.screenTimeHint)
                    CoverageHint(report, current, range.allTime || (range.days > 1 && !p.previousCovered))
                }

                AnimatedVisibility(visible = selectedDate != null, enter = Motion.reveal(), exit = Motion.conceal()) {
                    val d = selectedDate ?: current.last()
                    MonkCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(dayTitle(s, d), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${s.duration(report.phoneByDate[d] ?: 0L)} · ${s.duration(report.watchedByDate(d))} ${s.onYourList}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { selectedDate = null }) { Text(s.clearDay) }
                        }
                    }
                }

                SectionTitle(s.allApps)
                val ranked = remember(report, shownDates) { report.ranked(shownDates) }
                val of = report.phoneTotal(shownDates)
                MonkCard {
                    if (ranked.isEmpty()) Hint(s.screenTimeNothing)
                    ranked.forEach { (pkg, ms) ->
                        key(pkg) {
                            AppTimeRow(
                                report = report,
                                store = store,
                                packageName = pkg,
                                millis = ms,
                                of = of,
                                previous = if (selectedDate == null && p.previousCovered) report.appTotal(pkg, p.previous) else null,
                                onClick = { onOpenApp(pkg) },
                            )
                        }
                    }
                    if (ranked.isNotEmpty()) AppListHint(showDelta = selectedDate == null && p.previousCovered)
                }
            }
        }
    }
}

private fun dayTitle(s: Strings, date: String): String {
    val dow = LocalDate.parse(date).dayOfWeek.isoDayNumber
    return "${s.dayShort[dow - 1]}, ${s.dayDate(date)}"
}

/**
 * One app over the range: total and comparison, its own day chart, the average / longest /
 * quietest day, and — if Monk watches it — the pauses next to the time.
 */
@Composable
fun ScreenTimeAppScreen(
    store: MonkStore,
    platform: MonkPlatform,
    packageName: String,
    onClose: () -> Unit,
    topBar: TopBarState,
    hazeState: HazeState,
) {
    val s = strings
    val config by store.config.collectAsStateWithLifecycle()
    val stats by store.stats.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(TimeRange.Week) }
    val (report, _) = rememberRangeReport(store, platform, range)
    val label = report?.label(packageName) ?: config.app(packageName)?.label ?: packageName
    val onList = config.app(packageName) != null

    PushedPage(label, onClose, topBar, hazeState) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName, 56.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            WatchBadge(onList = onList, onAdd = { store.addApp(packageName, label) })
        }
        Segments(options = rangeOptions(s), selected = range) { range = it }
        when {
            report == null -> Unit
            !report.granted -> UsageAccessOptInCard(platform)
            else -> {
                val covered = report.coveredDates
                val p = remember(report, range) { periods(report, range.days, range.allTime) }
                val current = p.current
                val total = report.appTotal(packageName, current)
                val phone = report.phoneTotal(current)
                val perDay = current.map { report.byApp[packageName]?.get(it) ?: 0L }
                val onRecord = current.filter { it in covered }

                MonkCard {
                    AnimatedContent(targetState = total, transitionSpec = { Motion.fadeThrough() }, label = "total") { t ->
                        Text(s.duration(t), style = MaterialTheme.typography.headlineMedium)
                    }
                    if (total == 0L) {
                        Hint(if (onRecord.isNotEmpty()) s.notOpened else s.screenTimeNoRecord)
                    } else {
                        val line = if (range.allTime) {
                            "${s.screenTimeLastDays(current.size)} · ${s.perDayAverage(s.duration(total / onRecord.size.coerceAtLeast(1)))}"
                        } else {
                            screenTimeConclusion(s, total, if (p.previousCovered) report.appTotal(packageName, p.previous) else null, range.periodWord(s), range.previousWord(s), onRecord.size)
                        }
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                        if (phone > 0) Pill(s.screenTimeShare((total * 100 / phone).toInt()), if (onList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                    }
                    if (range != TimeRange.Today) {
                        val chart = if (range.allTime) current.takeLast(30) else current
                        val values = chart.map { report.byApp[packageName]?.get(it) ?: 0L }
                        // One series: this app's time, in the colour its row carries in the lists.
                        TimeBarChart(
                            dates = chart,
                            watched = values,
                            phone = values,
                            covered = chart.map { it in covered },
                            fgColor = if (onList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        )
                        DayLabels(chart)
                    }
                    CoverageHint(report, current, range.allTime || (range.days > 1 && !p.previousCovered))
                }

                if (range != TimeRange.Today && onRecord.isNotEmpty() && total > 0) {
                    SectionTitle(s.perDay)
                    MonkCard {
                        val longest = current.indices.filter { current[it] in covered }.maxByOrNull { perDay[it] }
                        val quietest = current.indices.filter { current[it] in covered }.minByOrNull { perDay[it] }
                        StatLine(s.averagePerDay, s.duration(total / onRecord.size))
                        if (longest != null) StatLine(s.longestDay, "${s.duration(perDay[longest])} · ${dayTitle(s, current[longest])}")
                        if (quietest != null && quietest != longest) {
                            StatLine(s.quietestDay, (if (perDay[quietest] == 0L) s.notOpened else s.duration(perDay[quietest])) + " · " + dayTitle(s, current[quietest]))
                        }
                        StatLine(s.daysOnRecord, onRecord.size.toString())
                        if (onRecord.size < current.size) Hint(s.averagesHint)
                    }
                }

                SectionTitle(s.monkPauses)
                if (onList) {
                    val a = remember(stats, current) { stats.perApp(current).firstOrNull { it.first == packageName }?.second }
                    MonkCard {
                        if (a == null || a.intercepted == 0) {
                            Hint(s.statsEmpty)
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Counter(a.intercepted, s.intercepted)
                                Counter(a.turnedAway, s.turnedAway, MaterialTheme.colorScheme.tertiary)
                                Counter(a.opened, s.opened, MaterialTheme.colorScheme.primary)
                            }
                            val pending = a.intercepted - a.turnedAway - a.opened
                            if (pending > 0) Hint(s.pendingCount(pending))
                        }
                    }
                } else {
                    MonkCard {
                        Hint(s.notWatchedHint)
                        TextButton(onClick = { store.addApp(packageName, label) }) { Text("+ ${s.addToList}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLine(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
    }
}
