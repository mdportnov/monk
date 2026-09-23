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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            // No session for this token: the process was killed and the system brought the
            // screen back on its own, or the service already moved on. Never show a pause screen
            // that cannot decide anything; leave, and let the service judge what is underneath.
            finishAndRemoveTask()
            MonkAccessibilityService.reevaluateForeground()
            return
        }
        setContent {
            val s = session ?: return@setContent
            val ui by s.ui.collectAsStateWithLifecycle()
            BackHandler { dismiss(s) }
            InterceptScreen(
                state = ui,
                onOpen = { reason -> open(s, reason?.name) },
                onDismiss = { dismiss(s) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!bind(intent)) {
            finishAndRemoveTask()
            MonkAccessibilityService.reevaluateForeground()
        }
    }

    private fun bind(intent: Intent): Boolean {
        val token = intent.getLongExtra(EXTRA_TOKEN, -1L)
        val s = monkGraph.intercepts[token] ?: return false
        // Arrived after the launch guard gave up and handed this session to the overlay: the
        // session is the overlay's now, and this Activity must not abandon it on the way out.
        if (!InterceptGate.owns(token)) return false
        // A new intercept over a live one: the old session is abandoned, not leaked.
        session?.let { old -> if (old.token != token) { old.abandon(); monkGraph.intercepts.remove(old.token) } }
        session = s
        InterceptGate.created(token)
        return true
    }

    override fun onResume() {
        super.onResume()
        val s = session ?: return
        // The service gave up on this launch and moved on: do not pop up over whatever is there
        // now, and leave the session alone — it may be on the overlay already.
        if (!InterceptGate.resumed(s.token)) {
            session = null
            finishAndRemoveTask()
        }
    }

    override fun onPause() {
        session?.let { InterceptGate.paused(it.token) }
        super.onPause()
    }

    private fun open(s: InterceptSession, reason: String?) {
        if (!s.open(reason)) return
        // The app's task is right underneath; finishing reveals it. Relaunch only if something
        // else has taken its place meanwhile — a relaunch would otherwise reset a deep link.
        finishAndRemoveTask()
        s.relaunchIfHidden()
    }

    private fun dismiss(s: InterceptSession) {
        s.dismiss()
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        session?.let { s ->
            if (isFinishing) s.abandon()
            InterceptGate.closed(s.token)
            monkGraph.intercepts.remove(s.token)
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TOKEN = "token"
    }
}

/**
 * Which intercept is on screen, keyed by token so a dying instance cannot clear the gate a
 * fresh one just opened. All access on the main thread.
 */
object InterceptGate {
    private var token: Long = -1
    private var pkg: String? = null
    private var isCreated = false
    private var isResumed = false
    private var inFront = false
    /** Survives [closed]: a screen that resumed and was then left quickly still launched fine. */
    private var lastResumed: Long = -1

    val showingFor: String? get() = pkg
    val liveToken: Long get() = token

    fun launching(token: Long, pkg: String) {
        this.token = token
        this.pkg = pkg
        isCreated = false
        isResumed = false
        inFront = false
    }

    fun owns(token: Long) = token == this.token

    fun created(token: Long) { if (token == this.token) isCreated = true }
    fun isCreated(token: Long) = token == this.token && isCreated

    /** true if this token is still the live one; marks it resumed. */
    fun resumed(token: Long): Boolean {
        if (token != this.token) return false
        isResumed = true
        inFront = true
        lastResumed = token
        return true
    }

    fun paused(token: Long) { if (token == this.token) inFront = false }

    fun isResumed(token: Long) = token == this.token && isResumed

    fun wasResumed(token: Long) = token == lastResumed

    /** The live pause screen is the resumed window right now: whatever surfaces meanwhile is underneath it. */
    fun isInFront() = token != -1L && inFront

    fun closed(token: Long) {
        if (token != this.token) return
        this.token = -1
        pkg = null
        isCreated = false
        isResumed = false
        inFront = false
    }

    fun clear() { closed(token) }
}
