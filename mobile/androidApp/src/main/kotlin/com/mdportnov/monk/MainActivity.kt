package com.mdportnov.monk

import android.Manifest
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.activity.SystemBarStyle
import com.mdportnov.monk.shared.model.ThemeMode
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.mdportnov.monk.shared.MonkRuntime
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mdportnov.monk.shared.ui.MonkApp

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Paint before the first frame from the persisted choice, so a dark user never sees a
        // white flash on a light system (and vice versa).
        applyTheme(initialDark())
        super.onCreate(savedInstanceState)
        (MonkRuntime.platform as? AndroidPlatform)?.notificationPermissionRequester = {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MonkApp(
                onBackHandler = { enabled, onBack -> BackHandler(enabled, onBack) },
                onThemeResolved = ::applyTheme,
            )
        }
    }

    private fun initialDark(): Boolean = when (MonkRuntime.store.config.value.theme) {
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

    override fun onDestroy() {
        (MonkRuntime.platform as? AndroidPlatform)?.notificationPermissionRequester = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Throttled inside (6 h); foreground is the one moment a check reliably runs.
        MonkApplication.updater(this)?.check(force = false)
    }
}
