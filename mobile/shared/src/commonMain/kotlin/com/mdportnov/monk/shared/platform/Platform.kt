package com.mdportnov.monk.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.mdportnov.monk.shared.model.InstalledApp

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
    fun permissions(): PermissionStatus
    fun openAccessibilitySettings()
    fun openAppInfo()
    fun requestNotificationPermission()
    /** Offers to add the Quick Settings tiles (Android 13+); no-op elsewhere. */
    fun requestAddTiles()
    val updater: Updater?
}

@Composable
expect fun AppIcon(packageName: String, size: Dp, modifier: Modifier = Modifier)

expect fun systemLanguage(): String
