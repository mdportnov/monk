package com.mdportnov.monk.shared.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import com.mdportnov.monk.shared.ui.Motion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The standard card. With [onClick] it becomes a tappable surface (ripple, haptic tick) that leads somewhere. */
@Composable
fun MonkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val m = modifier.fillMaxWidth().animateContentSize(Motion.contentSize)
    if (onClick != null) {
        val h = rememberHaptics()
        Card(onClick = { h.select(); onClick() }, modifier = m, colors = colors, elevation = elevation) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    } else {
        Card(modifier = m, colors = colors, elevation = elevation) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 6.dp, top = 6.dp),
    )
}

/** Small status pill: text on a tinted background, no interaction. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .background(color.copy(alpha = 0.14f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) icon()
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = color)
    }
}

@Composable
fun LabeledRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Counter(value: Int, label: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // A new number rolls in from the side it grew towards, the old one leaving the other way.
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val up = if (targetState > initialState) 1 else -1
                (fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { up * it / 2 }) togetherWith
                    (fadeOut(Motion.exit()) + slideOutVertically(Motion.exit()) { -up * it / 2 })
            },
            label = "counter",
        ) { v -> Text(v.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), color = color) }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
