package com.mdportnov.monk.shared.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.lastDates
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.DayStats
import com.mdportnov.monk.shared.model.Intention
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.ui.components.Counter
import com.mdportnov.monk.shared.ui.components.Hint
import com.mdportnov.monk.shared.ui.components.MonkCard
import com.mdportnov.monk.shared.ui.components.SectionTitle

private enum class Range(val days: Int) { Today(1), Week(7), All(90) }

@Composable
fun StatsScreen(store: MonkStore, scrollState: ScrollState, contentPadding: PaddingValues) {
    val s = strings
    val stats by store.stats.collectAsStateWithLifecycle()
    val config by store.config.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(Range.Week) }
    val dates = remember(range, stats) { lastDates(range.days) }
    val days = remember(dates, stats) { dates.map { stats.day(it) } }
    val intercepted = days.sumOf { it.intercepted }
    val away = days.sumOf { it.turnedAway }
    val opened = days.sumOf { it.opened }
    val labels = remember(config.apps) { config.apps.associate { it.packageName to it.label } }

    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(s.tabStats, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 4.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val items = listOf(Range.Today to s.statsToday, Range.Week to s.statsWeek, Range.All to s.statsAllTime)
            items.forEachIndexed { i, (r, label) ->
                SegmentedButton(selected = range == r, onClick = { range = r }, shape = SegmentedButtonDefaults.itemShape(i, items.size)) { Text(label) }
            }
        }

        MonkCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Counter(intercepted, s.intercepted)
                Counter(away, s.turnedAway, MaterialTheme.colorScheme.tertiary)
                Counter(opened, s.opened, MaterialTheme.colorScheme.primary)
            }
            if (intercepted > 0) {
                val rate = away.toFloat() / intercepted
                LinearProgressIndicator(
                    progress = { rate },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                )
                Hint(s.successRate((rate * 100).toInt()))
            } else {
                Hint(s.statsEmpty)
            }
            Hint(s.walkedAwayHint)
        }

        if (intercepted > 0) {
            if (range != Range.Today) {
                SectionTitle(s.byDay)
                MonkCard {
                    val shown = if (range == Range.All) days.takeLast(30) else days
                    BarChart(shown)
                    if (shown.size <= 14) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            shown.forEach { d ->
                                val dow = LocalDate.parse(d.date).dayOfWeek.isoDayNumber
                                Text(
                                    s.dayShort[dow - 1].take(if (shown.size > 7) 1 else 2),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Legend(MaterialTheme.colorScheme.tertiary, s.turnedAway)
                        Legend(MaterialTheme.colorScheme.primary, s.opened)
                    }
                }
            }

            SectionTitle(s.byApp)
            MonkCard {
                val perApp = remember(stats, dates) { stats.perApp(dates) }
                if (perApp.isEmpty()) Hint(s.noData)
                val max = perApp.maxOfOrNull { it.second.intercepted }?.coerceAtLeast(1) ?: 1
                perApp.forEach { (pkg, a) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, 32.dp)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(labels[pkg] ?: pkg, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(
                                    "${a.turnedAway * 100 / a.intercepted.coerceAtLeast(1)}%",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(s.pauses(a.intercepted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            StackedBar(
                                total = max,
                                away = a.turnedAway,
                                opened = a.opened,
                                awayColor = MaterialTheme.colorScheme.tertiary,
                                openedColor = MaterialTheme.colorScheme.primary,
                                track = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }
                }
            }

            SectionTitle(s.byHour)
            MonkCard {
                Hint(s.byHourHint)
                HourChart(remember(stats, dates) { stats.byHour(dates) })
            }

            val reasons = remember(stats, dates) { stats.reasons(dates) }
            if (reasons.isNotEmpty()) {
                SectionTitle(s.reasons)
                MonkCard {
                    val total = reasons.values.sum().coerceAtLeast(1)
                    Intention.entries
                        .map { it to (reasons[it.name] ?: 0) }
                        .filter { it.second > 0 }
                        .sortedByDescending { it.second }
                        .forEach { (i, n) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.intention(i), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("${n * 100 / total}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LinearProgressIndicator(
                                progress = { n.toFloat() / total },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(10.dp).height(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StackedBar(total: Int, away: Int, opened: Int, awayColor: Color, openedColor: Color, track: Color) {
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(4.dp.toPx())
        drawRoundRect(track, Offset.Zero, size, r)
        val wAway = size.width * away / total
        val wOpen = size.width * opened / total
        if (wAway > 0) drawRoundRect(awayColor, Offset.Zero, Size(wAway, size.height), r)
        if (wOpen > 0) drawRoundRect(openedColor, Offset(wAway, 0f), Size(wOpen, size.height), r)
    }
}

@Composable
private fun BarChart(days: List<DayStats>) {
    val away = MaterialTheme.colorScheme.tertiary
    val opened = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = (days.maxOfOrNull { it.turnedAway + it.opened } ?: 1).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (days.size - 1)) / days.size
        val r = CornerRadius(3.dp.toPx())
        days.forEachIndexed { i, d ->
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), r)
            val hAway = size.height * d.turnedAway / max
            val hOpen = size.height * d.opened / max
            if (hOpen > 0) drawRoundRect(opened, Offset(x, size.height - hOpen), Size(w, hOpen), r)
            if (hAway > 0) drawRoundRect(away, Offset(x, size.height - hOpen - hAway), Size(w, hAway), r)
        }
    }
}

@Composable
private fun HourChart(hours: List<Int>) {
    val bar = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val max = (hours.maxOrNull() ?: 1).coerceAtLeast(1)
    Column {
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val gap = 2.dp.toPx()
            val w = (size.width - gap * 23) / 24
            val r = CornerRadius(2.dp.toPx())
            hours.forEachIndexed { h, n ->
                val x = h * (w + gap)
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), r)
                val hh = size.height * n / max
                if (hh > 0) drawRoundRect(bar, Offset(x, size.height - hh), Size(w, hh), r)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = label) }
        }
    }
}
