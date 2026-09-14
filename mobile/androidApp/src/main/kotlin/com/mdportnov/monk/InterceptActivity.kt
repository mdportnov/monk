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
import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.ui.intercept.InterceptScreen

/**
 * The pause / block splash that the accessibility service throws on top of a watched app.
 * Lives in its own task, never in Recents, and dies as soon as it leaves the screen (noHistory),
 * so there is no way to "swipe it away" and land back in the app underneath.
 */
class InterceptActivity : ComponentActivity() {
    private var target by mutableStateOf("")
    private var decided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        target = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        InterceptGate.showing = target
        setContent {
            val config = MonkRuntime.store.config.value
            val app = config.app(target)
            if (app == null) {
                finish()
                return@setContent
            }
            BackHandler { dismiss() }
            InterceptScreen(
                packageName = app.packageName,
                label = app.label,
                mode = app.mode,
                delaySeconds = config.delayFor(app),
                allowMinutes = config.allowFor(app),
                onOpen = { open(app.packageName, config.allowFor(app)) },
                onDismiss = { dismiss() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        target = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        InterceptGate.showing = target
        decided = false
    }

    private fun open(packageName: String, minutes: Int) {
        if (decided) return
        decided = true
        MonkRuntime.store.grantAllowance(packageName, minutes)
        MonkRuntime.store.recordOpened()
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        finish()
        if (launch != null) runCatching { startActivity(launch) }
    }

    private fun dismiss() {
        if (decided) return
        decided = true
        MonkRuntime.store.recordTurnedAway()
        goHome()
        finish()
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
    }

    override fun onStop() {
        super.onStop()
        // Left via Home/Recents without choosing: the app underneath is still blocked on next entry.
        if (!decided) {
            decided = true
            MonkRuntime.store.recordTurnedAway()
        }
    }

    override fun onDestroy() {
        if (InterceptGate.showing == target) InterceptGate.showing = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
    }
}

/** Which package the intercept screen is currently covering, so the service does not fire twice. */
object InterceptGate {
    @Volatile var showing: String? = null
}
