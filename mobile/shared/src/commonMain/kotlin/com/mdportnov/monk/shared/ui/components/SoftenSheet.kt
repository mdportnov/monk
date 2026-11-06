package com.mdportnov.monk.shared.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.ui.Motion
import kotlin.math.PI
import kotlin.math.cos

/** One way to soften protection for a while: its label and the moment it ends. */
class SoftenOption(val key: Int, val label: String, val until: (now: Long) -> Long)

/**
 * The breath before protection is softened — a break, or the switch off. One ten-second breath
 * (four in, six out) runs around a ring while the choice is held back; the line under it says,
 * in clock time, when protection comes back, so nobody switches off without knowing it returns.
 * The breath counts only while Monk is on screen: switching away must not wait it out.
 * [seconds] 0 skips the breath, for the cases where nothing is being softened.
 */
@Composable
fun SoftenSheet(
    title: String,
    body: String,
    options: List<SoftenOption>,
    initial: Int,
    confirmText: String,
    onConfirm: (until: Long) -> Unit,
    onDismiss: () -> Unit,
    notes: List<String> = emptyList(),
    seconds: Int = COUNTDOWN_CONFIRM_SECONDS,
) {
    val s = strings
    val haptics = rememberHaptics()
    val total = seconds * 1000L
    var elapsed by rememberSaveable { mutableLongStateOf(0L) }
    var picked by rememberSaveable { mutableIntStateOf(initial) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, total) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameMillis { it }
            while (elapsed < total) {
                withFrameMillis { frame ->
                    elapsed = (elapsed + (frame - last)).coerceAtMost(total)
                    last = frame
                }
            }
        }
    }
    val remaining by remember(total) { derivedStateOf { ((total - elapsed + 999) / 1000).toInt() } }
    val done = remaining == 0
    LaunchedEffect(remaining) { if (remaining in 1 until seconds) haptics.tick() else if (done && seconds > 0) haptics.select() }
    val option = options.firstOrNull { it.key == picked } ?: options.first()
    // Read once per second, not per frame: the return time moves with the clock while the sheet is open.
    val now = remember(remaining, picked) { nowMillis() }
    val until = option.until(now)

    MonkSheet(onDismiss = onDismiss) { hide ->
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
            if (options.size > 1) {
                Segments(options = options.map { it.key to it.label }, selected = option.key, onSelect = { picked = it })
            }
            if (seconds > 0) BreathRing(progress = { elapsed / total.toFloat() }, elapsedMs = { elapsed }, remaining = remaining, done = done)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    s.comesBackAt(formatClock(until)),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    s.comesBackIn(s.duration((until - now).coerceAtLeast(60_000L))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
            notes.forEach { Hint(it) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Staying is the easy, filled button; softening is the one that waits.
                Button(onClick = { hide() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(s.stayProtected) }
                OutlinedButton(
                    onClick = { haptics.confirm(); onConfirm(option.until(nowMillis())); hide() },
                    enabled = done,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(),
                ) {
                    AnimatedContent(targetState = done, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "softenLabel") { ready ->
                        Text(if (ready) confirmText else s.countdownWait(remaining), maxLines = 1)
                    }
                }
            }
        }
    }
}

private const val INHALE_SHARE = 0.4f

private fun easeInOutSine(x: Float): Float = -(cos(PI.toFloat() * x) - 1f) / 2f

/** The ring fills over the countdown while the disc inside breathes once: four seconds in, six out. */
@Composable
private fun BreathRing(progress: () -> Float, elapsedMs: () -> Long, remaining: Int, done: Boolean) {
    val s = strings
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val settle by animateFloatAsState(if (done) 0f else 1f, Motion.standard(Motion.Long), label = "settle")
    val inhaling by remember { derivedStateOf { (elapsedMs() % BREATH_MS) / BREATH_MS.toFloat() < INHALE_SHARE } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(132.dp)
                    .graphicsLayer {
                        val t = (elapsedMs() % BREATH_MS) / BREATH_MS.toFloat()
                        val phase = if (t < INHALE_SHARE) easeInOutSine(t / INHALE_SHARE) else 1f - easeInOutSine((t - INHALE_SHARE) / (1f - INHALE_SHARE))
                        val sc = 1f - settle * 0.12f + settle * 0.18f * phase
                        scaleX = sc
                        scaleY = sc
                    }
                    .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.28f), tertiary.copy(alpha = 0.10f), Color.Transparent)), CircleShape),
            )
            Canvas(Modifier.size(176.dp)) {
                val stroke = 4.dp.toPx()
                drawArc(track, -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                val sweep = 360f * progress()
                if (sweep > 0f) drawArc(
                    brush = Brush.sweepGradient(listOf(primary, tertiary, primary)),
                    startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            AnimatedContent(targetState = done, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "ringCenter") { finished ->
                if (finished) {
                    Icon(Icons.Outlined.Check, null, tint = primary, modifier = Modifier.size(40.dp))
                } else {
                    Text(
                        "$remaining",
                        style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        AnimatedContent(targetState = if (done) 2 else if (inhaling) 0 else 1, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "breathWord") { phase ->
            Text(
                when (phase) { 0 -> s.inhale; 1 -> s.exhale; else -> s.breathDone },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val BREATH_MS = 10_000L
