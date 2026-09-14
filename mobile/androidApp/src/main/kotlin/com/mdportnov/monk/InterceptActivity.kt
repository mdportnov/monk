package com.mdportnov.monk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdportnov.monk.shared.MonkRuntime
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.ui.intercept.InterceptScreen

/**
 * The pause / block splash that the accessibility service throws on top of a watched app.
 * Lives in its own task, never in Recents, and dies as soon as it leaves the screen (noHistory),
 * so there is no way to "swipe it away" and land back in the app underneath.
 *
 * Stats: "intercepted" is counted here (the screen actually showed), "turned away" only on an
 * explicit dismiss. Leaving via Home / screen-off / an incoming call counts as nothing.
 */
class InterceptActivity : ComponentActivity() {
    private var target by mutableStateOf<Target?>(null)
    private var decided = false

    private data class Target(val app: BlockedApp, val config: MonkConfig, val limitReached: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!bind(intent)) {
            finishAndRemoveTask()
            return
        }
        setContent {
            val t = target ?: return@setContent
            BackHandler { dismiss() }
            InterceptScreen(
                packageName = t.app.packageName,
                label = t.app.label,
                mode = t.app.mode,
                delaySeconds = t.config.delayFor(t.app),
                allowMinutes = t.config.allowFor(t.app),
                limitReached = t.limitReached,
                dailyLimit = t.app.dailyLimit,
                askIntention = t.config.askIntention,
                onOpen = { reason -> open(t.app.packageName, t.config.allowFor(t.app), reason?.name) },
                onDismiss = { dismiss() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!bind(intent)) finishAndRemoveTask()
    }

    /** Resolves the watched app for this intent; false if it is no longer watched. */
    private fun bind(intent: Intent): Boolean {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val config = MonkRuntime.store.config.value
        val app = config.app(pkg) ?: return false
        val limitReached = app.dailyLimit?.let { MonkRuntime.store.opensToday(pkg) >= it } ?: false
        target = Target(app, config, limitReached)
        decided = false
        InterceptGate.showing = pkg
        InterceptGate.created = true
        MonkRuntime.store.recordIntercepted(pkg)
        return true
    }

    override fun onResume() {
        super.onResume()
        // The service gave up on us and already sent the user Home: do not pop up over the launcher.
        if (InterceptGate.showing != target?.app?.packageName) {
            finishAndRemoveTask()
            return
        }
        InterceptGate.resumed = true
    }

    private fun open(packageName: String, minutes: Int, reason: String?) {
        if (decided) return
        decided = true
        MonkRuntime.store.grantAllowance(packageName, minutes)
        MonkRuntime.store.recordOpened(packageName, reason)
        // The app's task is right underneath; finishing reveals it. Relaunch only if something
        // else has taken its place meanwhile — a relaunch would otherwise reset a deep link.
        val underneath = MonkAccessibilityService.currentForeground() == packageName
        finishAndRemoveTask()
        if (!underneath) {
            packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { runCatching { startActivity(it) } }
        }
    }

    private fun dismiss() {
        if (decided) return
        decided = true
        target?.let { MonkRuntime.store.recordTurnedAway(it.app.packageName) }
        goHome()
        finishAndRemoveTask()
    }

    private fun goHome() {
        if (MonkAccessibilityService.goHome()) return
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
    }

    override fun onDestroy() {
        if (InterceptGate.showing == target?.app?.packageName) InterceptGate.showing = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
    }
}

/** Which package the intercept screen is covering, so the service can tell "shown" from "dropped". */
object InterceptGate {
    @Volatile var showing: String? = null
    @Volatile var created: Boolean = false
    @Volatile var resumed: Boolean = false
}
