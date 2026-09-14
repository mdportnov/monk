package com.mdportnov.monk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mdportnov.monk.shared.ui.intercept.InterceptScreen

/**
 * The pause / block splash that the accessibility service throws on top of a watched app.
 * Lives in its own task, never in Recents, and dies as soon as it leaves the screen (noHistory),
 * so there is no way to "swipe it away" and land back in the app underneath.
 */
class InterceptActivity : ComponentActivity() {
    private var session by mutableStateOf<InterceptSession?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Always dark, whatever the system says: light status-bar icons over the ink background.
        val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        if (!bind(intent)) {
            finishAndRemoveTask()
            return
        }
        setContent {
            val s = session ?: return@setContent
            BackHandler { dismiss(s) }
            InterceptScreen(
                packageName = s.packageName,
                label = s.app.label,
                mode = s.app.mode,
                delaySeconds = s.config.delayFor(s.app),
                allowMinutes = s.config.allowFor(s.app),
                limitReached = s.limitReached,
                dailyLimit = s.app.dailyLimit,
                focusUntil = s.focusUntil,
                timesToday = s.timesToday,
                askIntention = s.config.askIntention,
                message = s.config.pauseMessage,
                onOpen = { reason -> open(s, reason?.name) },
                onDismiss = { dismiss(s) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!bind(intent)) finishAndRemoveTask()
    }

    private fun bind(intent: Intent): Boolean {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val s = InterceptSession.start(this, pkg) ?: return false
        session = s
        InterceptGate.showing = pkg
        InterceptGate.created = true
        return true
    }

    override fun onResume() {
        super.onResume()
        // The service gave up on us and already sent the user Home: do not pop up over the launcher.
        if (InterceptGate.showing != session?.packageName) {
            finishAndRemoveTask()
            return
        }
        InterceptGate.resumed = true
    }

    private fun open(s: InterceptSession, reason: String?) {
        if (s.isDecided) return
        s.open(reason)
        // The app's task is right underneath; finishing reveals it. Relaunch only if something
        // else has taken its place meanwhile — a relaunch would otherwise reset a deep link.
        finishAndRemoveTask()
        s.relaunchIfHidden()
    }

    private fun dismiss(s: InterceptSession) {
        if (s.isDecided) return
        s.dismiss()
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        if (InterceptGate.showing == session?.packageName) InterceptGate.showing = null
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
