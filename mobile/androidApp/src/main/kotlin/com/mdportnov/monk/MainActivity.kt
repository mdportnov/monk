package com.mdportnov.monk

import android.Manifest
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        (MonkRuntime.platform as? AndroidPlatform)?.notificationPermissionRequester = {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MonkApp(onBackHandler = { enabled, onBack -> BackHandler(enabled, onBack) })
        }
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
