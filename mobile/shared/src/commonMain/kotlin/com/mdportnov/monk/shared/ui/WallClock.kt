package com.mdportnov.monk.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mdportnov.monk.shared.data.nowMillis
import kotlinx.coroutines.delay

/**
 * Wall-clock millis for screens that show "what is in force now". Ticks on each minute boundary,
 * since rules, routines and breaks all flip on a whole minute, so a Block starting at 06:00 shows
 * at 06:00 and not up to half a minute later; and at each of [deadlines] (a break, strict mode,
 * a session, an allowance ending at an odd second). Re-read at once when [deadlines] change (a
 * timer started, moved by a clock change) and when the screen comes back.
 */
@Composable
fun rememberNow(deadlines: List<Long?> = emptyList()): Long {
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(deadlines) {
        while (true) {
            val t = nowMillis()
            now = t
            val nextMinute = MINUTE_MS - t % MINUTE_MS + 50
            val nextDeadline = deadlines.filterNotNull().filter { it > t }.minOfOrNull { it - t + 50 }
            delay(minOf(nextMinute, nextDeadline ?: nextMinute))
        }
    }
    LifecycleResumeEffect(Unit) {
        now = nowMillis()
        onPauseOrDispose { }
    }
    return now
}

private const val MINUTE_MS = 60_000L
