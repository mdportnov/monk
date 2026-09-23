package com.mdportnov.monk

import com.mdportnov.monk.shared.model.Decision

/**
 * The service's decision tree, free of Android types so it runs on the JVM under test.
 *
 * Inputs are window-state events (package + class); outputs go through [Effects]. It owns the
 * three pieces of state that make interception correct: which app the user is *in*
 * ([lastForeground]), whether an app surfaced under the keyguard or during a call and still
 * awaits its verdict ([pending]) and the relaunch debounce ([lastLaunchAt]).
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
        /** Millis until a time rule or the schedule flips the verdict, null if never. */
        fun nextChangeMillis(pkg: String): Long?
        /** The package an intercept screen is currently covering, if any. */
        fun interceptShowingFor(): String?
        /** True while that intercept screen is actually in front (resumed Activity or a live overlay). */
        fun interceptInFront(): Boolean = false
        /** Show the pause screen for [pkg]; the host decides Activity vs overlay and fail-closed. */
        fun intercept(pkg: String, decision: Decision.Intercept)
        /** The pause screen already up over [pkg] shows [decision] now, without counting a new intercept. */
        fun redrawIntercept(pkg: String, decision: Decision.Intercept) = Unit
        /** The overlay must go when the user leaves the app it covers. */
        fun hideOverlay()
        fun schedule(delayMs: Long, action: () -> Unit)
        fun cancelScheduled()
        /**
         * The app in front changed. The host may persist it so a service restarted by the
         * system (low memory, an update) can pick up where it left off via [restore].
         */
        fun rememberForeground(pkg: String?) = Unit
    }

    var lastForeground: String? = null
        private set(value) {
            if (field == value) return
            field = value
            effects.rememberForeground(value)
        }
    private var lastLaunchAt = 0L
    private var pending: String? = null

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
            // Surfaced under the keyguard (and USER_PRESENT never came: some ROMs skip it for a
            // swipe lock) or during a call: the next window of the same app once that is over counts.
            if (pending == pkg && !effects.isKeyguardLocked() && !effects.isInCall()) {
                pending = null
                evaluate(pkg)
            }
            return
        }
        // The app resurfacing above a live intercept (notification deep link, relaunch): re-cover
        // it, but not for the echo events the relaunch itself produces, and not for the windows
        // a cold start keeps opening underneath a pause screen that is still in front — each
        // re-cover would count another pause and another walk-away for one open.
        if (gateOpenFor == pkg && (effects.interceptInFront() || clock() - lastLaunchAt < RELAUNCH_DEBOUNCE_MS)) return
        lastForeground = pkg
        if (effects.isKeyguardLocked() || effects.isInCall()) {
            pending = pkg
            return
        }
        pending = null
        evaluate(pkg)
    }

    /**
     * An app that surfaced while the keyguard was up is judged once the user actually unlocks.
     * Without one pending, the app in front is re-judged anyway: the expiry timer runs on
     * uptime, which stops while the device sleeps, so an allowance that ran out under a locked
     * screen would otherwise outlive itself by however long the phone dozed.
     */
    fun onUserPresent() = reevaluateForeground()

    /**
     * A fresh gate in a restarted process: the host hands back the foreground it persisted.
     * Nothing is judged here; the caller decides when ([reevaluateForeground]). Only accepted
     * while no window event has arrived yet, so a live observation always wins.
     */
    fun restore(pkg: String?) {
        if (lastForeground == null && pkg != null) lastForeground = pkg
    }

    /**
     * Re-check the app in front (allowance expiry, a rule boundary, focus started from a tile,
     * an unlock, a restart). Under the keyguard the verdict waits for the unlock, like a window
     * event would. A pause screen already up over the app stays: judging again would count a
     * second intercept and a walk-away and start its countdown over — opening re-decides anyway.
     */
    fun reevaluateForeground() {
        val pkg = lastForeground ?: return
        if (!effects.isWatched(pkg)) return
        if (effects.isKeyguardLocked()) {
            pending = pkg
            return
        }
        pending = null
        if (effects.interceptShowingFor() == pkg) {
            val decision = effects.decide(pkg)
            if (decision is Decision.Intercept) {
                effects.cancelScheduled()
                effects.redrawIntercept(pkg, decision)
                return
            }
        }
        evaluate(pkg)
    }

    /**
     * The wall clock moved (time zone, DST, manual time): every rule boundary computed before
     * is wrong now. Drop the timer and judge the foreground app under the new clock.
     */
    fun onClockChanged() {
        effects.cancelScheduled()
        reevaluateForeground()
    }

    fun evaluate(pkg: String) {
        effects.cancelScheduled()
        when (val decision = effects.decide(pkg)) {
            Decision.Allow -> {
                // Come back when the allowance ends or a rule / schedule window flips, in case
                // the app is still open: a block starting at 06:00 must start at 06:00.
                if (!effects.isWatched(pkg)) return
                val allowance = effects.allowanceUntil(pkg)?.let { it - clock() }?.takeIf { it > 0 }
                val boundary = effects.nextChangeMillis(pkg)?.takeIf { it > 0 }
                val delay = listOfNotNull(allowance, boundary).minOrNull() ?: return
                effects.schedule(delay + 250) { if (lastForeground == pkg) reevaluateForeground() }
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
