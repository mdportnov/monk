package com.mdportnov.monk

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * host provides a minimal one. The countdown freezes on its own when the window loses focus
 * (the shade, a system dialog), the same way it does in the Activity.
 */
class InterceptOverlay(private val service: AccessibilityService, private val registry: InterceptRegistry) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var backCallback: OnBackInvokedCallback? = null
    private var owner: Owner? = null
    var session: InterceptSession? = null
        private set

    val isShowing get() = view != null

    fun show(session: InterceptSession) {
        hide()
        this.session = session
        val owner = Owner().also { this.owner = it }
        val compose = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            fitsSystemWindows = false
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
                val ui by session.ui.collectAsStateWithLifecycle()
                InterceptScreen(
                    state = ui,
                    onOpen = { reason -> open(session, reason?.name) },
                    onDismiss = { dismiss(session) },
                )
            }
        }
        // Full-screen but with real insets: no LAYOUT_NO_LIMITS, so safeDrawingPadding() inside
        // the screen sees the status bar and the gesture area like it does in an Activity.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
        }
        runCatching { wm.addView(compose, params) }
            .onSuccess {
                view = compose
                registerBack(compose, session)
                owner.resume()
                compose.requestFocus()
            }
            .onFailure { owner.destroy(); this.owner = null; this.session = null }
    }

    /**
     * Android 16 (targetSdk 36) stops dispatching KEYCODE_BACK: back reaches a window only through
     * its OnBackInvokedDispatcher. The key listener above stays for the legacy path (< 33).
     */
    private fun registerBack(compose: ComposeView, session: InterceptSession) {
        if (Build.VERSION.SDK_INT < 33) return
        val dispatcher = compose.findOnBackInvokedDispatcher() ?: return
        val callback = OnBackInvokedCallback { dismiss(session) }
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        backCallback = callback
    }

    private fun open(session: InterceptSession, reason: String?) {
        if (!session.open(reason)) return
        hide()
        session.relaunchIfHidden()
    }

    private fun dismiss(session: InterceptSession) {
        session.dismiss()
        hide()
    }

    /** Removes the window. An undecided session is abandoned like an Activity left via Home. */
    fun hide() {
        if (Build.VERSION.SDK_INT >= 33) view?.findOnBackInvokedDispatcher()?.let { d -> backCallback?.let(d::unregisterOnBackInvokedCallback) }
        backCallback = null
        view?.let { v -> runCatching { wm.removeViewImmediate(v) } }
        view = null
        owner?.destroy()
        owner = null
        session?.let { it.abandon(); registry.remove(it.token) }
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
        fun resume() { if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() {
            if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }
}
