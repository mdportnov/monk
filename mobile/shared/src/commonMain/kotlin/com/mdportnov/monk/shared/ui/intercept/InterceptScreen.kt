package com.mdportnov.monk.shared.ui.intercept

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.Intention
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.ui.theme.MonkColors
import com.mdportnov.monk.shared.ui.theme.MonkTheme
import kotlinx.coroutines.delay

/**
 * The splash that lands on top of a watched app. Always dark: it is meant to feel like a pause,
 * not like another screen of the app underneath.
 */
@Composable
fun InterceptScreen(
    packageName: String,
    label: String,
    mode: BlockMode,
    delaySeconds: Int,
    allowMinutes: Int,
    limitReached: Boolean,
    dailyLimit: Int?,
    askIntention: Boolean,
    onOpen: (Intention?) -> Unit,
    onDismiss: () -> Unit,
) {
    MonkTheme(darkTheme = true) {
        val s = strings
        val blocked = mode == BlockMode.BLOCK || limitReached
        var remaining by remember(packageName, delaySeconds) { mutableIntStateOf(if (blocked) 0 else delaySeconds) }
        var intention by remember(packageName) { mutableStateOf<Intention?>(null) }
        // Ticks only while RESUMED: pulling the notification shade over the pause must not wait it out.
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(packageName, delaySeconds, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (remaining > 0) {
                    delay(1000)
                    remaining--
                }
            }
        }
        val countdownDone = !blocked && remaining == 0
        val ready = countdownDone && (!askIntention || intention != null)

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0B0D12), Color(0xFF141a2a), Color(0xFF0B0D12))))
                .safeDrawingPadding(),
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BreathingOrb(active = !blocked && remaining > 0) {
                        AppIcon(packageName, 56.dp)
                    }
                    Spacer(Modifier.height(40.dp))
                    when {
                        limitReached -> {
                            Title(s.interceptLimitTitle(label))
                            Spacer(Modifier.height(12.dp))
                            Sub(s.interceptLimitHint(dailyLimit ?: 0))
                        }
                        blocked -> {
                            Title(s.interceptBlockedTitle(label))
                            Spacer(Modifier.height(12.dp))
                            Sub(s.interceptBlockedHint)
                        }
                        else -> {
                            Title(if (remaining > 0) s.interceptBreathe else s.interceptQuestion)
                            Spacer(Modifier.height(12.dp))
                            Sub(if (remaining > 0) label else s.interceptQuestionApp(label))
                        }
                    }
                    AnimatedVisibility(visible = countdownDone && askIntention) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(28.dp))
                            Text(s.interceptWhy, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                                Intention.entries.forEach { i ->
                                    FilterChip(
                                        selected = intention == i,
                                        onClick = { intention = i },
                                        label = { Text(s.intention(i)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MonkColors.Violet.copy(alpha = 0.3f),
                                            selectedLabelColor = MonkColors.Fog,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!blocked) {
                        Button(
                            onClick = { onOpen(intention) },
                            enabled = ready,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MonkColors.Fog,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Text(if (remaining > 0) s.interceptWait(remaining) else s.openFor(allowMinutes))
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MonkColors.Blue, contentColor = MonkColors.Ink),
                    ) {
                        Text(if (blocked) s.interceptBack else s.interceptNotNow, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MonkColors.Fog, textAlign = TextAlign.Center)
}

@Composable
private fun Sub(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun BreathingOrb(active: Boolean, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val s = if (active) scale else 0.95f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Box(
            Modifier
                .size(220.dp)
                .scale(s)
                .background(Brush.radialGradient(listOf(MonkColors.Violet.copy(alpha = 0.35f), Color.Transparent)), CircleShape),
        )
        Box(
            Modifier
                .size(150.dp)
                .scale(s)
                .background(Brush.radialGradient(listOf(MonkColors.Blue.copy(alpha = 0.55f), MonkColors.Blue.copy(alpha = 0.08f))), CircleShape),
        )
        content()
    }
}
