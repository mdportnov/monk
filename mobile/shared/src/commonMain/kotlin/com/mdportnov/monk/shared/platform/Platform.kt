package com.mdportnov.monk.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.mdportnov.monk.shared.model.InstalledApp
import kotlinx.coroutines.flow.StateFlow

data class PermissionStatus(
    /** The accessibility service that watches foreground apps. Required. */
    val accessibilityEnabled: Boolean,
    /** APKs installed from a downloaded file on Android 13+ need "Allow restricted settings" first. */
    val mayNeedRestrictedSettingsUnlock: Boolean,
    /** POST_NOTIFICATIONS on Android 13+; true where notifications need no runtime grant. */
    val notificationsGranted: Boolean = true,
)

interface MonkPlatform {
    /** false on iOS: the UI shows an "unsupported" card instead of the blocker controls. */
    val supportsBlocking: Boolean
    suspend fun installedApps(): List<InstalledApp>
    /** Latest known permission state; the platform refreshes it on resume and on service changes. */
    val permissions: StateFlow<PermissionStatus>
    fun refreshPermissions()
    fun openAccessibilitySettings()
    fun openAppInfo()
    /** Offers to add the Quick Settings tiles (Android 13+); no-op elsewhere. */
    fun requestAddTiles()
    /** Tell the OS the app's language ("system" = follow the device), so system-facing strings match. */
    fun applyAppLanguage(language: String)
    val updater: Updater?
}

@Composable
expect fun AppIcon(packageName: String, size: Dp, modifier: Modifier = Modifier)

expect fun systemLanguage(): String
