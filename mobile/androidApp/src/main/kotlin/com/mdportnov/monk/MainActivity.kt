package com.mdportnov.monk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mdportnov.monk.shared.ui.MonkApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MonkApp(onBackHandler = { enabled, onBack -> BackHandler(enabled, onBack) })
        }
    }

    override fun onResume() {
        super.onResume()
        // Throttled inside (6 h); foreground is the one moment a check reliably runs.
        MonkApplication.updater(this)?.check(force = false)
    }
}
