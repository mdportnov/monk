package com.mdportnov.monk.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.mdportnov.monk.shared.i18n.strings
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * A confirmation that gives the app its own medicine: the confirm button counts down for
 * [seconds] before it works. Used for everything that weakens protection for the whole list —
 * switching off, taking a break — so no softening is ever one tap.
 */
@Composable
fun CountdownConfirm(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
    seconds: Int = COUNTDOWN_CONFIRM_SECONDS,
) {
    val s = strings
    val haptics = rememberHaptics()
    var remaining by remember { mutableIntStateOf(seconds) }
    // Counts only while Monk is on screen: switching away must not wait the countdown out.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (remaining > 0) { delay(1000); remaining-- } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body)
                if (note != null) Hint(note)
            }
        },
        confirmButton = {
            TextButton(enabled = remaining == 0, onClick = { haptics.confirm(); onConfirm() }) {
                Text(if (remaining == 0) confirmText else s.countdownWait(remaining))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

const val COUNTDOWN_CONFIRM_SECONDS = 10
