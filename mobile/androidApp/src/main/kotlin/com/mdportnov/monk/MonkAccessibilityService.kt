package com.mdportnov.monk

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.mdportnov.monk.shared.MonkRuntime
import com.mdportnov.monk.shared.model.Decision
import java.lang.ref.WeakReference

/**
 * Watches window changes and, when a watched app comes to the front, drops [InterceptActivity]
 * on top of it. Reads only the package + class name of the window: canRetrieveWindowContent is
 * off in the service config, so screen content never reaches this process.
 *
 * Starting an Activity from a bound accessibility service is exempt from background-launch
 * limits (Android 10–16). Some OEM ROMs still drop it silently, so the launch is fail-closed: if
 * the intercept screen has not resumed within [LAUNCH_GUARD_MS] the user is sent Home instead.
 *
 * All callbacks run on the main thread, which is also where the UI mutates MonkStore.
 */
class MonkAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    /** The last Activity window we saw that belongs to a "real" app (not launcher/system/IME). */
    private var lastForeground: String? = null
    private var lastLaunchAt = 0L
    private var launchers: Set<String> = emptySet()
    private var imes: Set<String> = emptySet()
    private var transient: Set<String> = emptySet()

    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshSystemSets()
    }

    /** Re-checks the foreground app when its allowance runs out while it is still open. */
    private val allowanceExpiry = Runnable { lastForeground?.let { evaluate(it) } }

    /** Fail-closed guard: fires if InterceptActivity never showed up. */
    private val launchGuard = Runnable {
        val pkg = InterceptGate.showing ?: return@Runnable
        if (!InterceptGate.resumed) {
            Log.w(TAG, "intercept screen for $pkg never resumed; sending Home")
            InterceptGate.showing = null
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        refreshSystemSets()
        registerReceiver(
            packagesChanged,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
        )
        Log.d(TAG, "connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(packagesChanged) }
        instance = null
        return super.onUnbind(intent)
    }

    private fun refreshSystemSets() {
        launchers = SystemPackages.launchers(this)
        imes = imePackages(this)
        transient = SystemPackages.essential(this) + imes + setOf("android")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg in launchers) {
            // Home: whatever was open has been left; the next entry is a fresh decision.
            lastForeground = pkg
            handler.removeCallbacks(allowanceExpiry)
            return
        }
        // Shade, keyguard, keyboard, permission dialogs, dialer: neither "entering an app" nor
        // "leaving" one. They do not touch lastForeground so the allowance timer keeps running.
        if (pkg in transient) return
        // Dialogs, popups, PiP and bubbles arrive with the same event type. Only a real Activity
        // window counts as "the user opened this app".
        if (!isActivityWindow(pkg, event.className?.toString())) return

        val gateOpenFor = InterceptGate.showing
        if (pkg == lastForeground && gateOpenFor != pkg) return
        // The app resurfacing above a live intercept (notification deep link, relaunch): re-cover
        // it, but not for the echo events the relaunch itself produces.
        if (gateOpenFor == pkg && System.currentTimeMillis() - lastLaunchAt < RELAUNCH_DEBOUNCE_MS) return
        lastForeground = pkg
        if (isKeyguardLocked() || isInCall()) return
        evaluate(pkg)
    }

    private fun evaluate(pkg: String) {
        if (!MonkRuntime.isInitialized) return
        handler.removeCallbacks(allowanceExpiry)
        val store = MonkRuntime.store
        when (val decision = store.decide(pkg)) {
            Decision.Allow -> {
                // Inside an allowance: come back when it ends, in case the app is still open.
                if (store.config.value.app(pkg) == null) return
                val until = store.allowances.value[pkg] ?: return
                val delay = until - System.currentTimeMillis()
                if (delay > 0) handler.postDelayed(allowanceExpiry, delay + 250)
            }
            is Decision.Intercept -> {
                Log.d(TAG, "intercepting ${decision.app.packageName} (${decision.app.mode})")
                InterceptGate.showing = pkg
                InterceptGate.resumed = false
                val intent = Intent(this, InterceptActivity::class.java)
                    .putExtra(InterceptActivity.EXTRA_PACKAGE, pkg)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION,
                    )
                lastLaunchAt = System.currentTimeMillis()
                val started = runCatching { startActivity(intent) }.isSuccess
                if (!started) {
                    Log.w(TAG, "startActivity threw; sending Home")
                    InterceptGate.showing = null
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
                handler.removeCallbacks(launchGuard)
                handler.postDelayed(launchGuard, LAUNCH_GUARD_MS)
            }
        }
    }

    private fun isActivityWindow(pkg: String, className: String?): Boolean {
        if (className.isNullOrEmpty()) return false
        return runCatching { packageManager.getActivityInfo(ComponentName(pkg, className), 0) }.isSuccess
    }

    private fun isKeyguardLocked(): Boolean =
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    private fun isInCall(): Boolean {
        val mode = (getSystemService(Context.AUDIO_SERVICE) as AudioManager).mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "MonkA11y"
        private const val LAUNCH_GUARD_MS = 1500L
        private const val RELAUNCH_DEBOUNCE_MS = 1000L

        @Volatile private var instance: WeakReference<MonkAccessibilityService>? = null

        /** Home via the accessibility API: no task/affinity surprises, no chooser, works on every OEM. */
        fun goHome(): Boolean = instance?.get()?.performGlobalAction(GLOBAL_ACTION_HOME) == true

        /** Package of the app under the intercept screen, as the service last saw it. */
        fun currentForeground(): String? = instance?.get()?.lastForeground

        private fun imePackages(context: Context): Set<String> {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            return imm.inputMethodList.map { it.packageName }.toSet()
        }
    }
}

/**
 * Packages Monk must never gate: blocking them would lock the user out of the phone (Settings,
 * launcher), out of an emergency call (dialer, telecom, in-call UI) or out of Monk itself.
 * Applied both to the "Add apps" list and to the service's ignore set, so an edited or restored
 * config cannot brick the device either.
 */
object SystemPackages {
    private val fixed = setOf(
        "com.android.settings",
        "com.android.settings.intelligence",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.emergency",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.intentresolver",
    )

    fun launchers(context: Context): Set<String> =
        resolveAll(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

    fun essential(context: Context): Set<String> {
        val out = HashSet(fixed)
        out += context.packageName
        out += launchers(context)
        out += resolveAll(context, Intent(android.provider.Settings.ACTION_SETTINGS))
        out += resolveAll(context, Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        out += resolveAll(context, Intent(Intent.ACTION_DIAL))
        runCatching {
            (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
        }.getOrNull()?.let { out += it }
        return out
    }

    private fun resolveAll(context: Context, intent: Intent): Set<String> =
        runCatching {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }
                .toSet()
        }.getOrDefault(emptySet())
}
