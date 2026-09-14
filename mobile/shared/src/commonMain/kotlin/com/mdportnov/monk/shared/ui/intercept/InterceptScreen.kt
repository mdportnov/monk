package com.mdportnov.monk.shared.ui.intercept

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.mdportnov.monk.shared.data.formatClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.Intention
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.ui.Motion
import com.mdportnov.monk.shared.ui.theme.MonkColors
import com.mdportnov.monk.shared.ui.theme.MonkTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.util.lerp
import com.mdportnov.monk.shared.ui.components.smoothstep
import com.mdportnov.monk.shared.ui.rememberFrameClock
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos

/**
 * The splash that lands on top of a watched app. Always dark: it is meant to feel like a pause,
 * not like another screen of the app underneath.
 */
@Composable
fun InterceptScreen(
    state: InterceptUiState,
    onOpen: (Intention?) -> Unit,
    onDismiss: () -> Unit,
) {
    val packageName = state.packageName
    val label = state.label
    val mode = state.mode
    val delaySeconds = state.delaySeconds
    val allowMinutes = state.allowMinutes
    val limitReached = state.limitReached
    val dailyLimit = state.dailyLimit
    val focusUntil = state.focusUntil
    val timesToday = state.timesToday
    val askIntention = state.askIntention
    val message = state.message
    val ruleBlockedUntil = state.ruleBlockedUntil
    MonkTheme(darkTheme = true, language = state.language) {
        val s = strings
        val focus = focusUntil != null
        val blocked = mode == BlockMode.BLOCK || limitReached || focus || ruleBlockedUntil != null
        // The pause is timed by a clock that runs only while RESUMED (pulling the shade over
        // the pause must not wait it out) and advances per frame, so the ring fills smoothly
        // while the second label still ticks.
        val clock = remember(packageName, delaySeconds) { PauseClock(if (blocked) 0f else delaySeconds * 1000f) }
        var intention by remember(packageName) { mutableStateOf<Intention?>(null) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(clock, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var last = withFrameNanos { it }
                while (clock.elapsed < clock.totalMs) {
                    withFrameNanos { now ->
                        clock.elapsed = minOf(clock.totalMs, clock.elapsed + (now - last) / 1_000_000f)
                        last = now
                    }
                }
            }
        }
        val remaining by remember(clock) { derivedStateOf { clock.secondsLeft } }
        val countdownDone = !blocked && remaining == 0
        val ready = countdownDone && (!askIntention || intention != null)
        val haptic = LocalHapticFeedback.current
        LaunchedEffect(countdownDone) { if (countdownDone && delaySeconds > 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        // Entrance: the window is already ink; everything on it comes in as one composition,
        // orb first, words, then the buttons — read in graphics layers, never recomposed.
        val entered = remember { Animatable(0f) }
        // Starts only once the first frame has actually been drawn (ink, everything at alpha 0),
        // so a slow first frame of a cold process never eats the entrance.
        val firstDraw = remember { CompletableDeferred<Unit>() }
        LaunchedEffect(Unit) {
            firstDraw.await()
            withFrameNanos { }
            entered.animateTo(1f, Motion.enter(Motion.Long))
        }
        val density = LocalDensity.current

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0B0D12), Color(0xFF141a2a), Color(0xFF0B0D12))))
                .drawWithContent { drawContent(); firstDraw.complete(Unit) }
                .safeDrawingPadding(),
        ) {
            // Landscape phones and small windows: a smaller orb and a scrollable column instead
            // of clipped buttons. Tablets: the column stays phone-wide in the middle.
            val orb = minOf(220.dp, maxHeight * 0.3f)
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 520.dp)
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Spacer(Modifier.weight(1f, fill = true))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.graphicsLayer {
                            val e = smoothstep(0f, 0.7f, entered.value)
                            alpha = e
                            scaleX = 0.92f + 0.08f * e
                            scaleY = 0.92f + 0.08f * e
                        },
                    ) {
                        BreathingOrb(
                            active = !blocked && remaining > 0,
                            progress = { clock.fraction },
                            timed = !blocked && delaySeconds > 0,
                            done = countdownDone,
                            size = orb,
                        ) { AppIcon(packageName, orb * 0.25f) }
                    }
                    Spacer(Modifier.height(40.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer {
                            val e = smoothstep(0.2f, 0.9f, entered.value)
                            alpha = e
                            translationY = (1f - e) * 12.dp.toPx()
                        },
                    ) {
                    when {
                        focus -> {
                            Title(s.interceptFocusTitle(formatClock(focusUntil ?: 0L)))
                            Spacer(Modifier.height(12.dp))
                            Sub(s.interceptFocusHint)
                        }
                        ruleBlockedUntil != null && !limitReached -> {
                            Title(s.interceptRuleTitle(label, formatClock(ruleBlockedUntil)))
                            Spacer(Modifier.height(12.dp))
                            Sub(message.ifBlank { s.interceptRuleHint })
                        }
                        limitReached -> {
                            Title(s.interceptLimitTitle(label))
                            Spacer(Modifier.height(12.dp))
                            Sub(s.interceptLimitHint(dailyLimit ?: 0))
                        }
                        blocked -> {
                            Title(s.interceptBlockedTitle(label))
                            Spacer(Modifier.height(12.dp))
                            Sub(message.ifBlank { s.interceptBlockedHint })
                        }
                        else -> {
                            AnimatedContent(targetState = remaining > 0, transitionSpec = { Motion.fadeThrough() }, label = "question") { waiting ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Title(if (waiting) s.interceptBreathe else s.interceptQuestion)
                                    Spacer(Modifier.height(12.dp))
                                    Sub(if (waiting) label else s.interceptQuestionApp(label))
                                }
                            }
                            if (message.isNotBlank()) {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "“$message”",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                                    color = MonkColors.Fog.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    if (timesToday > 1 && !focus) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.timesToday(timesToday), style = MaterialTheme.typography.labelMedium, color = MonkColors.Violet)
                    }
                    AnimatedVisibility(visible = countdownDone && askIntention, enter = Motion.reveal(), exit = Motion.conceal()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(28.dp))
                            Text(s.interceptWhy, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                                Intention.entries.forEachIndexed { index, i ->
                                    // Each chip pops in a beat after the previous one.
                                    AnimatedVisibility(
                                        visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                                        enter = Motion.appear(delay = index * 60),
                                    ) {
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
                    }
                }
                Spacer(Modifier.weight(1f, fill = true))
                Column(
                    Modifier.fillMaxWidth().graphicsLayer {
                        val e = smoothstep(0.4f, 1f, entered.value)
                        alpha = e
                        translationY = (1f - e) * 16.dp.toPx()
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                            AnimatedContent(targetState = remaining > 0, transitionSpec = { Motion.fadeThrough() }, label = "open") { waiting ->
                                Text(if (waiting) s.interceptWait(remaining.coerceAtLeast(1)) else s.openFor(allowMinutes))
                            }
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
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MonkColors.Fog, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Sub(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

/** Elapsed time of the pause, in the screen's own frames. */
private class PauseClock(val totalMs: Float) {
    var elapsed by mutableFloatStateOf(0f)
    val fraction: Float get() = if (totalMs <= 0f) 1f else (elapsed / totalMs).coerceIn(0f, 1f)
    val secondsLeft: Int get() = ceil((totalMs - elapsed) / 1000f).toInt().coerceAtLeast(0)
}

private fun easeInOutSine(x: Float): Float = -(cos(PI.toFloat() * x) - 1f) / 2f

/**
 * The orb. One breath = 4 s in, 6 s out, eased like lungs rather than a metronome; the two
 * light blobs drift on their own slow orbits. Breath and drift are read off the frame clock
 * inside graphics layers, so nothing recomposes per frame and nothing restarts when the state
 * flips. When the pause ends the breath eases out into the resting size, the ring completes
 * and dissolves, and the inhale/exhale label fades — no swap.
 */
@Composable
private fun BreathingOrb(
    active: Boolean,
    progress: () -> Float,
    timed: Boolean,
    done: Boolean,
    size: Dp,
    content: @Composable () -> Unit,
) {
    val s = strings
    val breathT by rememberFrameClock(10_000)
    val orbit1 by rememberFrameClock(14_000)
    val orbit2 by rememberFrameClock(23_000)
    // How much of the breath is in the scale: 1 while pausing, easing to 0 (rest) once done.
    val breathMix by animateFloatAsState(if (active) 1f else 0f, Motion.standard(Motion.Long), label = "breath")
    // The ring holds full for a beat, then dissolves.
    val ringAlpha by animateFloatAsState(
        if (done || !timed) 0f else 1f,
        if (done) tween(Motion.Long, delayMillis = 240, easing = Motion.EmphasizedAccelerate) else Motion.standard(),
        label = "ring",
    )
    val inhaling by remember { derivedStateOf { breathT < 0.4f } }
    fun breathPhase(t: Float): Float = if (t < 0.4f) easeInOutSine(t / 0.4f) else 1f - easeInOutSine((t - 0.4f) / 0.6f)
    val breathe: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
        val sc = lerp(0.95f, 0.86f + 0.2f * breathPhase(breathT), breathMix)
        scaleX = sc
        scaleY = sc
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            // Halo
            Box(
                Modifier.size(size).graphicsLayer(breathe)
                    .background(Brush.radialGradient(listOf(MonkColors.Violet.copy(alpha = 0.30f), Color.Transparent)), CircleShape),
            )
            // Two light blobs inside a soft disc, each on its own slow orbit
            Box(Modifier.size(size * 0.72f).graphicsLayer(breathe).clip(CircleShape)) {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1B2140), Color(0xFF0E1120)))))
                Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = orbit1 * 360f }) {
                    val r = this.size.width
                    drawCircle(Brush.radialGradient(listOf(MonkColors.Blue.copy(alpha = 0.85f), Color.Transparent), center = Offset(r * 0.32f, r * 0.30f), radius = r * 0.6f), radius = r * 0.6f, center = Offset(r * 0.32f, r * 0.30f))
                }
                Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = 360f - orbit2 * 360f }) {
                    val r = this.size.width
                    drawCircle(Brush.radialGradient(listOf(MonkColors.Violet.copy(alpha = 0.8f), Color.Transparent), center = Offset(r * 0.70f, r * 0.66f), radius = r * 0.55f), radius = r * 0.55f, center = Offset(r * 0.70f, r * 0.66f))
                }
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.White.copy(alpha = 0.06f), radius = this.size.width / 2, style = Stroke(1.dp.toPx()))
                }
            }
            // Countdown ring: fills as the pause elapses, continuously.
            if (timed) {
                Canvas(Modifier.size(size * 0.86f).graphicsLayer { alpha = ringAlpha }) {
                    val stroke = 3.dp.toPx()
                    drawArc(MonkColors.Fog.copy(alpha = 0.10f), -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                    val sweep = 360f * progress()
                    if (sweep > 0f) drawArc(
                        brush = Brush.sweepGradient(listOf(MonkColors.Blue, MonkColors.Violet, MonkColors.Blue)),
                        startAngle = -90f, sweepAngle = sweep, useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            content()
        }
        // A fixed slot for the breath label, so its coming and going never moves the layout.
        Spacer(Modifier.height(6.dp))
        Box(Modifier.height(20.dp).graphicsLayer { alpha = breathMix }, contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = inhaling, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }, label = "breathLabel") { inh ->
                Text(if (inh) s.inhale else s.exhale, style = MaterialTheme.typography.labelMedium, color = MonkColors.Fog.copy(alpha = 0.55f))
            }
        }
    }
}
