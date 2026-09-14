package com.mdportnov.monk.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.ui.theme.MonkColors

/**
 * "How Monk works" as a sheet that slides up from the bottom: the loop, why a pause breaks it,
 * what the data says, then the three steps. Designed, not a system dialog.
 */
@Composable
fun HowItWorksSheet(onDismiss: () -> Unit) {
    val s = strings
    MonkSheet(onDismiss = onDismiss) { hide ->
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(s.howTitle, style = MaterialTheme.typography.headlineSmall)
            Chapter(Icons.Outlined.Loop, s.howLoopTitle, s.howLoopBody, MonkColors.Rose)
            LoopStrip()
            Chapter(Icons.Outlined.PauseCircle, s.howWhyTitle, s.howWhyBody, MonkColors.Blue)
            Chapter(Icons.Outlined.Science, s.howDataTitle, s.howDataBody, MonkColors.Mint)
            Chapter(Icons.Outlined.Shield, s.howStatesTitle, s.howStatesBody, MonkColors.Violet)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.howUseTitle, style = MaterialTheme.typography.titleMedium)
                listOf(s.howStep1, s.howStep2, s.howStep3).forEachIndexed { i, line ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                        }
                        Text(line.substringAfter(". "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Hint(s.howStep4)
            }
            Button(onClick = hide, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(s.gotIt) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Chapter(icon: ImageVector, title: String, body: String, accent: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(36.dp).background(accent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Cue → craving → action → reward, drawn as four beads on a gradient line. */
@Composable
private fun LoopStrip() {
    val s = strings
    val labels = s.loopSteps
    Column(Modifier.fillMaxWidth().padding(start = 50.dp)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(MonkColors.Rose, MonkColors.Violet, MonkColors.Blue))))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
