package com.mdportnov.monk

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mdportnov.monk.shared.ui.intercept.InterceptScreen

/**
 * The pause screen as a TYPE_ACCESSIBILITY_OVERLAY window owned by the service. Needs no
 * permission, is not subject to background-activity-start rules, and covers the whole display —
 * split-screen, desktop windows, OEM ROMs that drop activity launches. Compose needs a
 * lifecycle / saved-state / view-model owner on the view tree; a service has none, so this
 * host provides a minimal one.
 */
class InterceptOverlay(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: Owner? = null
    var session: InterceptSession? = null
        private set

    val isShowing get() = view != null

    fun show(session: InterceptSession) {
        hide(countAbandoned = true)
        this.session = session
        val owner = Owner().also { this.owner = it }
        val compose = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, code, event ->
                if (code == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss(session)
                    true
                } else {
                    false
                }
            }
            setContent {
                // A service window gets no system-bar insets: pad by hand so the buttons clear
                // the gesture area and the orb clears the status bar.
                Box(Modifier.fillMaxSize().padding(top = 32.dp, bottom = 40.dp)) {
                InterceptScreen(
                    packageName = session.packageName,
                    label = session.app.label,
                    mode = session.app.mode,
                    delaySeconds = session.config.delayFor(session.app),
                    allowMinutes = session.config.allowFor(session.app),
                    limitReached = session.limitReached,
                    dailyLimit = session.app.dailyLimit,
                    focusUntil = session.focusUntil,
                    timesToday = session.timesToday,
                    askIntention = session.config.askIntention,
                    message = session.config.pauseMessage,
                    onOpen = { reason -> open(session, reason?.name) },
                    onDismiss = { dismiss(session) },
                )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (android.os.Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        runCatching { wm.addView(compose, params) }
            .onSuccess {
                view = compose
                owner.resume()
                compose.requestFocus()
            }
            .onFailure { owner.destroy(); this.owner = null; this.session = null }
    }

    private fun open(session: InterceptSession, reason: String?) {
        if (session.isDecided) return
        session.open(reason)
        hide(countAbandoned = false)
        session.relaunchIfHidden()
    }

    private fun dismiss(session: InterceptSession) {
        if (session.isDecided) return
        session.dismiss()
        hide(countAbandoned = false)
    }

    /** Removes the window. An undecided session is simply abandoned (no stats), like leaving the Activity via Home. */
    fun hide(countAbandoned: Boolean) {
        view?.let { v -> runCatching { wm.removeViewImmediate(v) } }
        view = null
        owner?.destroy()
        owner = null
        session = null
    }

    private class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()
        init {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store
        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() {
            if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }
}
