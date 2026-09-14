package com.mdportnov.monk.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.mdportnov.monk.shared.model.InstalledApp

data class PermissionStatus(
    /** The accessibility service that watches foreground apps. Required. */
    val accessibilityEnabled: Boolean,
    /** "Display over other apps". Optional, makes the interception more reliable on some OEMs. */
    val overlayGranted: Boolean,
    /** Sideloaded apps on Android 13+ need "Allow restricted settings" before the service can be enabled. */
    val mayNeedRestrictedSettingsUnlock: Boolean,
)

interface MonkPlatform {
    /** false on iOS: the UI shows an "unsupported" card instead of the blocker controls. */
    val supportsBlocking: Boolean
    suspend fun installedApps(): List<InstalledApp>
    fun permissions(): PermissionStatus
    fun openAccessibilitySettings()
    fun openOverlaySettings()
    fun openAppInfo()
}

@Composable
expect fun AppIcon(packageName: String, size: Dp, modifier: Modifier = Modifier)

expect fun systemLanguage(): String
