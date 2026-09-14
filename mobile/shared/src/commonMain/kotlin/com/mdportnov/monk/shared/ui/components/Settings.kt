package com.mdportnov.monk.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.mdportnov.monk.shared.ui.Motion
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * Settings rows follow Material list items: a 24 dp leading icon centred on the row, a text
 * column (title, then a one-line description 2 dp below), a trailing control centred on the row.
 * No tinted circles and no custom layout: with descriptions kept to one or two short lines the
 * centred icon is exactly where the eye expects it.
 */

private val HPad = 16.dp
private val VPad = 12.dp
private val IconSize = 24.dp
private val IconGap = 16.dp

/** A settings group: rows separated by hairlines, no inner padding of its own. */
@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(Motion.contentSize),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column { content() }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = HPad))
}

@Composable
private fun Leading(icon: ImageVector?) {
    if (icon == null) return
    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize).padding(end = 0.dp))
}

@Composable
private fun Texts(title: String?, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (title != null) Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = if (title != null) 2.dp else 0.dp),
            )
        }
    }
}

/** Icon · title / description · trailing control. The standard row. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = HPad, vertical = VPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IconGap),
    ) {
        Leading(icon)
        Texts(title, subtitle, Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

/** Title / description on top, then free-form content stacked below (buttons, toggles, fields). */
@Composable
fun SettingBlock(icon: ImageVector? = null, title: String? = null, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = HPad, vertical = VPad),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null || subtitle != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IconGap)) {
                Leading(icon)
                Texts(title, subtitle, Modifier.weight(1f))
            }
        }
        content()
    }
}

/** Title + value pill on one line, description under it, a full-width slider below. Commits on release. */
@Composable
fun SliderSetting(
    title: String,
    hint: String?,
    value: Int,
    unit: String,
    range: IntRange,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    step: Int = 1,
    format: (Int) -> String = { "$it $unit" },
    onChange: (Int) -> Unit,
) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val h = rememberHaptics()
    Column(Modifier.fillMaxWidth().padding(horizontal = HPad, vertical = VPad)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IconGap)) {
            Leading(icon)
            Texts(title, hint, Modifier.weight(1f))
            Pill(format(draft.roundToInt()), if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = draft,
            enabled = enabled,
            onValueChange = { v ->
                val next = ((v / step).roundToInt() * step).toFloat()
                if (next != draft) h.tick()
                draft = next
            },
            onValueChangeFinished = { h.select(); onChange(draft.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/**
 * A row of exclusive choices: one filled pill glides to the chosen one instead of each segment
 * flipping its colour, and labels shrink rather than wrap.
 */
@Composable
fun <T> Segments(options: List<Pair<T, String>>, selected: T, enabled: Boolean = true, onSelect: (T) -> Unit) {
    val h = rememberHaptics()
    val shape = RoundedCornerShape(50)
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val position by animateFloatAsState(index.toFloat(), Motion.standard(), label = "segment")
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.38f)
    val fill = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (enabled) 1f else 0.5f)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(40.dp).clip(shape).border(1.dp, outline, shape),
        contentAlignment = Alignment.CenterStart,
    ) {
        val slot = maxWidth / options.size
        val stride = with(LocalDensity.current) { slot.toPx() }
        Box(
            Modifier
                .offset { IntOffset((stride * position).roundToInt(), 0) }
                .width(slot)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(shape)
                .background(fill),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, label) ->
                val active = i == index
                val fg by animateColorAsState(
                    when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        active -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    Motion.color, label = "segmentFg",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (value != selected) h.select()
                            onSelect(value)
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides fg) { FitText(label) }
                }
            }
        }
    }
}
