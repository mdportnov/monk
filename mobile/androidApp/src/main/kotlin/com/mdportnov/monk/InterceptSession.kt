package com.mdportnov.monk

import android.content.Context
import android.content.Intent
import com.mdportnov.monk.shared.MonkRuntime
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.MonkConfig

/**
 * One intercept, independent of how it is drawn (Activity or accessibility overlay): resolves
 * the watched app, counts the stats exactly once, and performs the two outcomes.
 */
class InterceptSession private constructor(
    private val context: Context,
    val packageName: String,
    val app: BlockedApp,
    val config: MonkConfig,
    val limitReached: Boolean,
    val timesToday: Int,
) {
    private var decided = false

    val focusUntil: Long? get() = config.focusUntil.takeIf { config.isFocus(System.currentTimeMillis()) }

    fun open(reason: String?) {
        if (decided) return
        decided = true
        val store = MonkRuntime.store
        store.grantAllowance(packageName, config.allowFor(app))
        store.recordOpened(packageName, reason)
    }

    fun dismiss() {
        if (decided) return
        decided = true
        MonkRuntime.store.recordTurnedAway(packageName)
        goHome()
    }

    /** Whether a decision was already made (used to avoid double counting on teardown). */
    val isDecided get() = decided

    /** Brings the watched app back in front when something else took its place meanwhile. */
    fun relaunchIfHidden() {
        if (MonkAccessibilityService.currentForeground() == packageName) return
        context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { runCatching { context.startActivity(it) } }
    }

    private fun goHome() {
        if (MonkAccessibilityService.goHome()) return
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(home) }
    }

    companion object {
        /** null when the package is no longer watched. Records "intercepted". */
        fun start(context: Context, packageName: String): InterceptSession? {
            val store = MonkRuntime.store
            val config = store.config.value
            val app = config.app(packageName) ?: return null
            val limitReached = app.dailyLimit?.let { store.opensToday(packageName) >= it } ?: false
            store.recordIntercepted(packageName)
            return InterceptSession(context.applicationContext, packageName, app, config, limitReached, store.interceptsToday(packageName))
        }
    }
}
