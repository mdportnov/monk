package com.mdportnov.monk

import android.Manifest
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import com.mdportnov.monk.shared.model.ThemeMode
import com.mdportnov.monk.shared.ui.HostActions
import com.mdportnov.monk.shared.ui.LocalHostActions
import com.mdportnov.monk.shared.ui.MonkApp

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { monkGraph.platform.refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Paint before the first frame from the persisted choice, so a dark user never sees a
        // white flash on a light system (and vice versa).
        applyTheme(initialDark())
        super.onCreate(savedInstanceState)
        val host = HostActions(
            requestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
        setContent {
            CompositionLocalProvider(LocalHostActions provides host) {
                MonkApp(
                    graph = monkGraph.shared,
                    onBackHandler = { enabled, onBack -> BackHandler(enabled, onBack) },
                    onThemeResolved = ::applyTheme,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        monkGraph.platform.refreshPermissions()
        // Throttled inside (6 h); foreground is the one moment a check reliably runs.
        monkGraph.updater.check(force = false)
    }

    private fun initialDark(): Boolean = when (monkGraph.store.config.value.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    /** Window background + status/navigation bar icon contrast for the resolved theme. */
    private fun applyTheme(dark: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val bars = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF0B0D12.toInt() else 0xFFF1F3F9.toInt()))
    }
}
