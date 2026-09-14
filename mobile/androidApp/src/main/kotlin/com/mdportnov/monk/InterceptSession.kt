package com.mdportnov.monk

import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.Decision
import com.mdportnov.monk.shared.model.RuleMode
import com.mdportnov.monk.shared.ui.intercept.InterceptUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One intercept, independent of how it is drawn (Activity or accessibility overlay). Created by
 * the service from the [Decision] it already made, counted exactly once by [InterceptRegistry],
 * and identified by a [token] so a stale instance can never speak for a fresh one.
 */
class InterceptSession(
    val token: Long,
    val packageName: String,
    val app: BlockedApp,
    private val store: MonkStore,
    decision: Decision.Intercept,
    private val nav: Host,
) {
    /** What the session needs from the platform. */
    interface Host {
        fun goHome()
        fun foregroundPackage(): String?
        fun launchApp(packageName: String)
        fun armForeground()
    }

    private var decided = false
    val isDecided get() = decided

    private val _ui = MutableStateFlow(uiFor(decision))
    val ui: StateFlow<InterceptUiState> = _ui

    private fun uiFor(decision: Decision.Intercept): InterceptUiState {
        val config = store.config.value
        val now = System.currentTimeMillis()
        return InterceptUiState(
            packageName = packageName,
            label = app.label,
            mode = decision.effectiveMode,
            delaySeconds = config.delayFor(app),
            allowMinutes = config.allowFor(app),
            limitReached = decision.limitReached,
            dailyLimit = app.dailyLimit,
            focusUntil = config.focusUntil.takeIf { decision.focus && config.isFocus(now) },
            timesToday = store.interceptsToday(packageName),
            askIntention = config.askIntention,
            message = config.pauseMessage,
            ruleBlockedUntil = if (decision.rule?.mode == RuleMode.BLOCK && !decision.focus) store.blockEndsAt(packageName) else null,
            language = config.language,
        )
    }

    /**
     * Opens the app for the allowance window. Re-decides first: a focus session started from a
     * tile, or a daily limit reached elsewhere, must win over the screen that was drawn earlier.
     * Returns false (and redraws as blocked) when opening is no longer allowed.
     */
    fun open(reason: String?): Boolean {
        if (decided) return true
        // Uninstalled or removed from the list while the screen was up: nothing to open, nothing to count.
        if (store.config.value.app(packageName) == null) { decided = true; return true }
        val now = store.decide(packageName)
        if (now is Decision.Intercept && now.effectiveMode == BlockMode.BLOCK) {
            _ui.value = uiFor(now)
            return false
        }
        decided = true
        store.grantAllowance(packageName, store.config.value.allowFor(app))
        store.recordOpened(packageName, reason)
        // The app stays in front after this, so no window event will arm the expiry / boundary
        // timer; ask the gate to judge it now (Allow) and schedule the next check.
        nav.armForeground()
        return true
    }

    fun dismiss() {
        if (decided) return
        decided = true
        store.recordTurnedAway(packageName)
        nav.goHome()
    }

    /** The screen went away without a choice (home gesture, back, screen off): the app was not opened, so it counts as walking away. */
    fun abandon() {
        if (decided) return
        decided = true
        store.recordTurnedAway(packageName)
    }

    /** Brings the watched app back in front when something else took its place meanwhile. */
    fun relaunchIfHidden() {
        if (nav.foregroundPackage() == packageName) return
        nav.launchApp(packageName)
    }
}

/**
 * Live sessions by token. The service creates and counts them; the Activity / overlay looks
 * them up. Holding sessions here (not in the Activity) means a configuration change or a slow
 * cold start never spawns a second countdown or a second "intercepted".
 */
class InterceptRegistry(private val store: MonkStore) {
    private val sessions = HashMap<Long, InterceptSession>()
    private var nextToken = System.currentTimeMillis()

    fun create(app: BlockedApp, decision: Decision.Intercept, host: InterceptSession.Host): InterceptSession {
        val token = ++nextToken
        store.recordIntercepted(app.packageName)
        return InterceptSession(token, app.packageName, app, store, decision, host).also { sessions[token] = it }
    }

    operator fun get(token: Long): InterceptSession? = sessions[token]
    fun remove(token: Long) { sessions.remove(token) }
}
