package com.mdportnov.monk.shared.ui.components

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Whether the user wants haptic ticks; provided at the root from config. */
val LocalHapticsEnabled = compositionLocalOf { true }

/** Thin wrapper so every control asks the same way and respects the setting. */
class Haptics(private val hf: HapticFeedback, private val enabled: Boolean) {
    fun tick() { if (enabled) hf.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
    fun select() { if (enabled) hf.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    fun toggle(on: Boolean) { if (enabled) hf.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff) }
    fun confirm() { if (enabled) hf.performHapticFeedback(HapticFeedbackType.Confirm) }
    fun reject() { if (enabled) hf.performHapticFeedback(HapticFeedbackType.Reject) }
}

@Composable
fun rememberHaptics(): Haptics {
    val hf = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(hf, enabled) { Haptics(hf, enabled) }
}

/** Material Switch that ticks on toggle. */
@Composable
fun HapticSwitch(checked: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    val h = rememberHaptics()
    Switch(checked = checked, enabled = enabled, modifier = modifier, onCheckedChange = { on -> h.toggle(on); onCheckedChange(on) })
}
