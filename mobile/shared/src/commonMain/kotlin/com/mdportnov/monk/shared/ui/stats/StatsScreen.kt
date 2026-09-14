package com.mdportnov.monk.shared.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.localMoment
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.DayStats
import com.mdportnov.monk.shared.ui.components.MonkCard
import com.mdportnov.monk.shared.ui.components.SectionTitle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

@Composable
fun StatsScreen(store: MonkStore, modifier: Modifier = Modifier) {
    val s = strings
    val stats by store.stats.collectAsStateWithLifecycle()
    val today = stats.day(localMoment().dateIso)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(s.tabStats, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 4.dp))

        SectionTitle(s.statsToday)
        MonkCard { Counters(today.intercepted, today.turnedAway, today.opened) }

        SectionTitle(s.statsAllTime)
        MonkCard { Counters(stats.totalIntercepted, stats.totalTurnedAway, stats.totalOpened) }

        SectionTitle(s.last14)
        MonkCard {
            if (stats.days.isEmpty()) {
                Text(s.statsEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val days = lastDays(14).map { stats.day(it) }
                BarChart(days)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Legend(MaterialTheme.colorScheme.tertiary, s.turnedAway)
                    Legend(MaterialTheme.colorScheme.primary, s.opened)
                }
            }
        }
    }
}

@Composable
private fun Counters(intercepted: Int, turnedAway: Int, opened: Int) {
    val s = strings
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Counter(intercepted, s.intercepted, MaterialTheme.colorScheme.onSurface)
        Counter(turnedAway, s.turnedAway, MaterialTheme.colorScheme.tertiary)
        Counter(opened, s.opened, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Counter(value: Int, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(10.dp).height(10.dp)) { drawCircle(color) }
        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BarChart(days: List<DayStats>) {
    val away = MaterialTheme.colorScheme.tertiary
    val opened = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = (days.maxOfOrNull { it.turnedAway + it.opened } ?: 1).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (days.size - 1)) / days.size
        days.forEachIndexed { i, d ->
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(4.dp.toPx()))
            val hAway = size.height * d.turnedAway / max
            val hOpen = size.height * d.opened / max
            if (hOpen > 0) drawRoundRect(opened, Offset(x, size.height - hOpen), Size(w, hOpen), CornerRadius(4.dp.toPx()))
            if (hAway > 0) drawRoundRect(away, Offset(x, size.height - hOpen - hAway), Size(w, hAway), CornerRadius(4.dp.toPx()))
        }
    }
}

private fun lastDays(n: Int): List<String> {
    val today = LocalDate.parse(localMoment().dateIso)
    return (n - 1 downTo 0).map { back -> today.minus(back, DateTimeUnit.DAY).toString() }
}
