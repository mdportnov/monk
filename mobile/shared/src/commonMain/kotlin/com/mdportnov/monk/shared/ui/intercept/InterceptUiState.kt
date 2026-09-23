package com.mdportnov.monk.shared.ui.intercept

import com.mdportnov.monk.shared.model.BlockMode

/** Everything the pause screen needs, computed once by whoever decided to intercept. */
data class InterceptUiState(
    /** The intercept this state belongs to; the pause clock lives and dies with it. */
    val token: Long = 0L,
    val packageName: String,
    val label: String,
    val mode: BlockMode,
    val delaySeconds: Int,
    val allowMinutes: Int,
    val limitReached: Boolean,
    val dailyLimit: Int?,
    /**
     * The routine that shut this app, when one did. Only set where the routine is what made the
     * verdict stricter than the app's own setting, so the screen never blames a routine for a
     * block the app was already under.
     */
    val routineName: String? = null,
    val routineEmoji: String = "",
    /** Epoch millis when that routine lets go, or null when nothing on the clock ends it. */
    val routineUntil: Long? = null,
    /** Started by hand rather than opened by its own hours: a promise, not a schedule. */
    val routineManual: Boolean = false,
    val timesToday: Int,
    val askIntention: Boolean,
    /** The user's own line; empty = built-in copy. */
    val message: String,
    /** Epoch millis when the active block rule window closes, if a rule is what blocks. */
    val ruleBlockedUntil: Long? = null,
    /** "system", "en" or "ru" — the app's own choice, not the device's. */
    val language: String = "system",
)
