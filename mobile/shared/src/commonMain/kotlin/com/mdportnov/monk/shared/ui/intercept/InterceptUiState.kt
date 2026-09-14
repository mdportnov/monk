package com.mdportnov.monk.shared.ui.intercept

import com.mdportnov.monk.shared.model.BlockMode

/** Everything the pause screen needs, computed once by whoever decided to intercept. */
data class InterceptUiState(
    val packageName: String,
    val label: String,
    val mode: BlockMode,
    val delaySeconds: Int,
    val allowMinutes: Int,
    val limitReached: Boolean,
    val dailyLimit: Int?,
    /** Epoch millis of the focus session end, when one is running. */
    val focusUntil: Long?,
    val timesToday: Int,
    val askIntention: Boolean,
    /** The user's own line; empty = built-in copy. */
    val message: String,
    /** Epoch millis when the active block rule window closes, if a rule is what blocks. */
    val ruleBlockedUntil: Long? = null,
    /** "system", "en" or "ru" — the app's own choice, not the device's. */
    val language: String = "system",
)
