package com.mdportnov.monk.shared.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.ui.components.LabeledRow
import com.mdportnov.monk.shared.ui.components.MonkCard
import com.mdportnov.monk.shared.ui.components.SectionTitle
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(store: MonkStore, modifier: Modifier = Modifier) {
    val s = strings
    val config by store.config.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(s.tabSettings, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 4.dp))

        SectionTitle(s.defaults)
        MonkCard {
            Text(s.defaultsHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.delayLength, style = MaterialTheme.typography.bodyLarge)
            DurationPicker(
                value = config.defaultDelaySeconds,
                range = 3..120,
                unit = s.seconds,
                onChange = { v -> store.updateConfig { it.copy(defaultDelaySeconds = v) } },
            )
            Text(s.allowLength, style = MaterialTheme.typography.bodyLarge)
            DurationPicker(
                value = config.defaultAllowMinutes,
                range = 1..60,
                unit = s.minutes,
                onChange = { v -> store.updateConfig { it.copy(defaultAllowMinutes = v) } },
            )
        }

        SectionTitle(s.scheduleTitle)
        MonkCard {
            LabeledRow(title = s.scheduleTitle, subtitle = s.scheduleHint) {
                Switch(
                    checked = config.schedule.enabled,
                    onCheckedChange = { on -> store.updateConfig { it.copy(schedule = it.schedule.copy(enabled = on)) } },
                )
            }
            if (config.schedule.enabled) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.dayShort.forEachIndexed { i, label ->
                        val day = i + 1
                        val on = day in config.schedule.days
                        FilterChip(
                            selected = on,
                            // At least one day stays selected: an empty set would silently pause protection forever.
                            enabled = !(on && config.schedule.days.size == 1),
                            onClick = {
                                store.updateConfig {
                                    val days = if (on) it.schedule.days - day else it.schedule.days + day
                                    it.copy(schedule = it.schedule.copy(days = days))
                                }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                TimePickerRow(s.from, config.schedule.startMinute) { m ->
                    store.updateConfig { it.copy(schedule = it.schedule.copy(startMinute = m)) }
                }
                TimePickerRow(s.to, config.schedule.endMinute) { m ->
                    store.updateConfig { it.copy(schedule = it.schedule.copy(endMinute = m)) }
                }
                Text(s.scheduleAllDay, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle(s.about)
        MonkCard {
            Text(s.aboutText, style = MaterialTheme.typography.bodyMedium)
            Text(s.language, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Slider with the live value on the right. Commits on release so the store isn't spammed. */
@Composable
fun DurationPicker(value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onChange(draft.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${draft.roundToInt()} $unit",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 12.dp).width(64.dp),
        )
    }
}

@Composable
private fun TimePickerRow(label: String, minute: Int, onChange: (Int) -> Unit) {
    var draft by remember(minute) { mutableFloatStateOf(minute.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(40.dp))
        Slider(
            value = draft,
            onValueChange = { draft = (it / 15f).roundToInt() * 15f },
            onValueChangeFinished = { onChange(draft.roundToInt().coerceIn(0, 24 * 60 - 15)) },
            valueRange = 0f..(24f * 60f - 15f),
            modifier = Modifier.weight(1f),
        )
        Text(formatMinute(draft.roundToInt()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp).width(64.dp))
    }
}

fun formatMinute(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
