package com.mdportnov.monk

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.mdportnov.monk.shared.MonkRuntime
import com.mdportnov.monk.shared.model.Decision

/**
 * Watches window changes and, when a watched app comes to the front, drops [InterceptActivity]
 * on top of it. Reads only the package name of the window: canRetrieveWindowContent is off in
 * the service config, so screen content never reaches this process.
 */
class MonkAccessibilityService : AccessibilityService() {
    private var lastForeground: String? = null
    private val ignored = HashSet<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        ignored.clear()
        ignored += packageName
        ignored += "com.android.systemui"
        ignored += "android"
        ignored += launcherPackages(this)
        ignored += imePackages(this)
        Log.i(TAG, "connected, ignoring ${ignored.size} system packages")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg in ignored) {
            lastForeground = pkg
            return
        }
        if (pkg == lastForeground) return
        lastForeground = pkg
        if (InterceptGate.showing == pkg) return
        if (!MonkRuntime.isInitialized) return

        when (val decision = MonkRuntime.store.decide(pkg)) {
            Decision.Allow -> Unit
            is Decision.Intercept -> {
                Log.i(TAG, "intercepting ${decision.app.packageName} (${decision.app.mode})")
                MonkRuntime.store.recordIntercepted()
                InterceptGate.showing = pkg
                val intent = Intent(this, InterceptActivity::class.java)
                    .putExtra(InterceptActivity.EXTRA_PACKAGE, pkg)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION,
                    )
                runCatching { startActivity(intent) }.onFailure {
                    Log.w(TAG, "could not start intercept screen", it)
                    InterceptGate.showing = null
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val TAG = "MonkA11y"

        fun launcherPackages(context: Context): Set<String> {
            val pm = context.packageManager
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            return pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }
                .toSet()
        }

        fun imePackages(context: Context): Set<String> {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            return imm.inputMethodList.map { it.packageName }.toSet()
        }
    }
}
