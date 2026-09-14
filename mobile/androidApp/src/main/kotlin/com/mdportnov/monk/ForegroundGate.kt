package com.mdportnov.monk

import com.mdportnov.monk.shared.model.Decision

/**
 * The service's decision tree, free of Android types so it runs on the JVM under test.
 *
 * Inputs are window-state events (package + class); outputs go through [Effects]. It owns the
 * three pieces of state that make interception correct: which app the user is *in*
 * ([lastForeground]), whether an app surfaced under the keyguard ([pendingAfterUnlock]) and the
 * relaunch debounce ([lastLaunchAt]).
 */
class ForegroundGate(
    private val ownPackage: String,
    private val ownMainActivity: String,
    private val effects: Effects,
    private val clock: () -> Long,
) {
    interface Effects {
        fun isLauncher(pkg: String): Boolean
        fun isTransient(pkg: String): Boolean
        fun isActivityWindow(pkg: String, className: String?): Boolean
        fun isKeyguardLocked(): Boolean
        fun isInCall(): Boolean
        fun decide(pkg: String): Decision
        fun isWatched(pkg: String): Boolean
        fun allowanceUntil(pkg: String): Long?
        /** The package an intercept screen is currently covering, if any. */
        fun interceptShowingFor(): String?
        /** Show the pause screen for [pkg]; the host decides Activity vs overlay and fail-closed. */
        fun intercept(pkg: String, decision: Decision.Intercept)
        /** The overlay must go when the user leaves the app it covers. */
        fun hideOverlay()
        fun schedule(delayMs: Long, action: () -> Unit)
        fun cancelScheduled()
    }

    var lastForeground: String? = null
        private set
    private var lastLaunchAt = 0L
    private var pendingAfterUnlock: String? = null

    fun onWindow(pkg: String, className: String?) {
        // An overlay covers exactly one app; anything else surfacing means the user left it.
        effects.interceptShowingFor()?.let { covered ->
            if (pkg != ownPackage && pkg != covered && (effects.isLauncher(pkg) || effects.isActivityWindow(pkg, className))) {
                effects.hideOverlay()
            }
        }
        if (pkg == ownPackage) {
            // Monk's own UI in front means the watched app is not; the intercept screen itself is
            // transparent (it sits on top of the app it covers).
            if (className == ownMainActivity) {
                lastForeground = pkg
                effects.cancelScheduled()
            }
            return
        }
        if (effects.isLauncher(pkg)) {
            lastForeground = pkg
            effects.cancelScheduled()
            return
        }
        // Shade, keyguard, keyboard, permission dialogs, dialer: neither entering nor leaving an app.
        if (effects.isTransient(pkg)) return
        // Dialogs, popups, PiP and bubbles arrive with the same event type; only Activities count.
        if (!effects.isActivityWindow(pkg, className)) return

        val gateOpenFor = effects.interceptShowingFor()
        if (pkg == lastForeground && gateOpenFor != pkg) {
            // Surfaced under the keyguard earlier and USER_PRESENT never came (some ROMs skip it
            // for a swipe lock): the next window of the same app on an unlocked screen counts.
            if (pendingAfterUnlock == pkg && !effects.isKeyguardLocked()) {
                pendingAfterUnlock = null
                evaluate(pkg)
            }
            return
        }
        // The app resurfacing above a live intercept (notification deep link, relaunch): re-cover
        // it, but not for the echo events the relaunch itself produces.
        if (gateOpenFor == pkg && clock() - lastLaunchAt < RELAUNCH_DEBOUNCE_MS) return
        lastForeground = pkg
        if (effects.isKeyguardLocked()) {
            pendingAfterUnlock = pkg
            return
        }
        if (effects.isInCall()) return
        evaluate(pkg)
    }

    /** An app that surfaced while the keyguard was up is judged once the user actually unlocks. */
    fun onUserPresent() {
        val pkg = pendingAfterUnlock ?: return
        pendingAfterUnlock = null
        if (pkg == lastForeground) evaluate(pkg)
    }

    /** Re-check the app in front (allowance expiry, focus started from a tile). */
    fun reevaluateForeground() {
        lastForeground?.let { if (effects.isWatched(it)) evaluate(it) }
    }

    fun evaluate(pkg: String) {
        effects.cancelScheduled()
        when (val decision = effects.decide(pkg)) {
            Decision.Allow -> {
                // Inside an allowance: come back when it ends, in case the app is still open.
                if (!effects.isWatched(pkg)) return
                val until = effects.allowanceUntil(pkg) ?: return
                val delay = until - clock()
                if (delay > 0) effects.schedule(delay + 250) { lastForeground?.let { if (it == pkg) evaluate(it) } }
            }
            is Decision.Intercept -> {
                lastLaunchAt = clock()
                effects.intercept(pkg, decision)
            }
        }
    }

    companion object {
        const val RELAUNCH_DEBOUNCE_MS = 1000L
    }
}
