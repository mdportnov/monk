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
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.mdportnov.monk.shared.model.Decision
import java.lang.ref.WeakReference

/**
 * Adapter between the accessibility API and [ForegroundGate]: feeds window-state events in,
 * carries the effects out (Activity launch with a fail-closed guard, overlay, Home, timers).
 * Reads only package + class of each window: canRetrieveWindowContent is off in the service
 * config, so screen content never reaches this process.
 *
 * Starting an Activity from a bound accessibility service is exempt from background-launch
 * limits (Android 10–16). Some OEM ROMs still drop it silently, so the launch is fail-closed: if
 * the intercept screen has not resumed within the guard window, the overlay takes over, and
 * Home is the last resort.
 *
 * All callbacks run on the main thread, which is also where the UI mutates MonkStore.
 */
class MonkAccessibilityService : AccessibilityService(), ForegroundGate.Effects, InterceptSession.Host {
    private val handler = Handler(Looper.getMainLooper())
    private val graph by lazy { monkGraph }
    private val overlay by lazy { InterceptOverlay(this, graph.intercepts) }
    private val gate by lazy { ForegroundGate(packageName, MainActivity::class.java.name, this, System::currentTimeMillis) }

    private var launchers: Set<String> = emptySet()
    private var transientPkgs: Set<String> = emptySet()
    private val activityWindowCache = LruCache<String, Boolean>(256)
    private var scheduled: Runnable? = null

    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // PACKAGE_CHANGED is chatty (component toggles); one refresh per burst is plenty.
            handler.removeCallbacks(refreshSets)
            handler.postDelayed(refreshSets, 500)
        }
    }
    private val refreshSets = Runnable { refreshSystemSets() }

    private val userPresent = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = gate.onUserPresent()
    }

    // --- lifecycle ---

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        refreshSystemSets()
        ContextCompat.registerReceiver(
            this, packagesChanged,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(this, userPresent, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        MonkNotifications.clearServiceOff(this)
        graph.platform.refreshPermissions()
        Log.d(TAG, "connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        overlay.hide()
        runCatching { unregisterReceiver(packagesChanged) }
        runCatching { unregisterReceiver(userPresent) }
        instance = null
        val app = applicationContext
        // A fresh Handler on purpose: the one above was just wiped. Posted after the unbind
        // settles, so the system already knows whether we are really off.
        Handler(Looper.getMainLooper()).postDelayed({
            MonkNotifications.serviceOff(app)
            app.monkGraph.platform.refreshPermissions()
        }, 3_000)
        return super.onUnbind(intent)
    }

    private fun refreshSystemSets() {
        launchers = SystemPackages.launchers(this)
        val imes = (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).inputMethodList.map { it.packageName }
        transientPkgs = SystemPackages.essential(this, launchers) + imes + "android"
        activityWindowCache.evictAll()
    }

    // --- events in ---

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Overlay under the shade / a system dialog: freeze the countdown, like an Activity would.
        if (overlay.isShowing) {
            if (pkg == "com.android.systemui") overlay.pause() else if (pkg == packageName) overlay.resume()
        }
        gate.onWindow(pkg, event.className?.toString())
    }

    override fun onInterrupt() = Unit

    // --- ForegroundGate.Effects ---

    override fun isLauncher(pkg: String) = pkg in launchers
    override fun isTransient(pkg: String) = pkg in transientPkgs

    override fun isActivityWindow(pkg: String, className: String?): Boolean {
        if (className.isNullOrEmpty()) return false
        val key = "$pkg/$className"
        activityWindowCache.get(key)?.let { return it }
        val isActivity = runCatching { packageManager.getActivityInfo(ComponentName(pkg, className), 0) }.isSuccess
        activityWindowCache.put(key, isActivity)
        return isActivity
    }

    override fun isKeyguardLocked() = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    override fun isInCall(): Boolean {
        val mode = (getSystemService(Context.AUDIO_SERVICE) as AudioManager).mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE
    }

    override fun decide(pkg: String) = graph.store.decide(pkg)
    override fun isWatched(pkg: String) = graph.store.config.value.app(pkg) != null
    override fun allowanceUntil(pkg: String) = graph.store.allowances.value[pkg]
    override fun interceptShowingFor(): String? = overlay.session?.packageName ?: InterceptGate.showingFor
    override fun hideOverlay() = overlay.hide()

    override fun schedule(delayMs: Long, action: () -> Unit) {
        cancelScheduled()
        scheduled = Runnable { scheduled = null; action() }.also { handler.postDelayed(it, delayMs) }
    }

    override fun cancelScheduled() {
        scheduled?.let { handler.removeCallbacks(it) }
        scheduled = null
    }

    override fun intercept(pkg: String, decision: Decision.Intercept) {
        val app = graph.store.config.value.app(pkg) ?: return
        Log.d(TAG, "intercepting $pkg (${decision.effectiveMode})")
        if (graph.store.config.value.overlayMode) {
            if (!showOverlay(app, decision)) performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }
        val session = graph.intercepts.create(app, decision, this)
        InterceptGate.launching(session.token, pkg)
        val intent = Intent(this, InterceptActivity::class.java)
            .putExtra(InterceptActivity.EXTRA_TOKEN, session.token)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (runCatching { startActivity(intent) }.isFailure) {
            Log.w(TAG, "startActivity threw; overlay instead")
            InterceptGate.clear()
            graph.intercepts.remove(session.token)
            if (!showOverlay(app, decision)) performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }
        guardRetries = 0
        handler.removeCallbacks(launchGuard)
        launchGuardToken = session.token
        handler.postDelayed(launchGuard, LAUNCH_GUARD_MS)
    }

    private fun showOverlay(app: com.mdportnov.monk.shared.model.BlockedApp, decision: Decision.Intercept): Boolean {
        val session = graph.intercepts.create(app, decision, this)
        overlay.show(session)
        if (!overlay.isShowing) graph.intercepts.remove(session.token)
        return overlay.isShowing
    }

    /** Fail-closed guard: the Activity never resumed → overlay; overlay impossible → Home. */
    private var guardRetries = 0
    private var launchGuardToken = -1L
    private val launchGuard: Runnable = object : Runnable {
        override fun run() {
            val token = launchGuardToken
            if (InterceptGate.isResumed(token)) return
            if (InterceptGate.isCreated(token) && guardRetries < LAUNCH_GUARD_RETRIES) {
                // Created but not yet resumed: a cold start on a slow device, give it more time.
                guardRetries++
                handler.postDelayed(this, LAUNCH_GUARD_MS)
                return
            }
            val session = graph.intercepts[token]
            Log.w(TAG, "intercept screen never resumed; falling back to overlay")
            InterceptGate.clear()
            if (session == null) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
            overlay.show(session)
            if (!overlay.isShowing) performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    // --- InterceptSession.Host ---

    override fun goHome() { performGlobalAction(GLOBAL_ACTION_HOME) }
    override fun foregroundPackage(): String? = gate.lastForeground
    override fun launchApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { runCatching { startActivity(it) } }
    }

    companion object {
        private const val TAG = "MonkA11y"
        private const val LAUNCH_GUARD_MS = 2500L
        private const val LAUNCH_GUARD_RETRIES = 3

        @Volatile private var instance: WeakReference<MonkAccessibilityService>? = null

        /** Home via the accessibility API: no task/affinity surprises, no chooser, works on every OEM. */
        fun goHomeStatic(): Boolean = instance?.get()?.performGlobalAction(GLOBAL_ACTION_HOME) == true

        /** A tile started a focus session: the app in front must be judged again right away. */
        fun reevaluateForeground() { instance?.get()?.let { it.gate.reevaluateForeground() } }
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

    fun essential(context: Context, launchers: Set<String> = launchers(context)): Set<String> {
        val out = HashSet(fixed)
        out += context.packageName
        out += launchers
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
