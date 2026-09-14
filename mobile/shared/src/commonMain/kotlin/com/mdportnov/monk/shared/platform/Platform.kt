package com.mdportnov.monk.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.mdportnov.monk.shared.model.InstalledApp
import com.mdportnov.monk.shared.model.ScreenTimeReport
import kotlinx.coroutines.flow.StateFlow

data class PermissionStatus(
    /** The accessibility service that watches foreground apps. Required. */
    val accessibilityEnabled: Boolean,
    /** APKs installed from a downloaded file on Android 13+ need "Allow restricted settings" first. */
    val mayNeedRestrictedSettingsUnlock: Boolean,
    /** POST_NOTIFICATIONS on Android 13+; true where notifications need no runtime grant. */
    val notificationsGranted: Boolean = true,
    /** Quick Settings tiles: how many of the app's tiles are in the shade, and whether the OS can add them for us (Android 13+). */
    val tilesAdded: Int = 0,
    val tilesTotal: Int = 0,
    val canRequestTiles: Boolean = false,
    /** Exempt from battery optimization (Doze / OEM sleep lists take their cue from it). */
    val batteryUnrestricted: Boolean = true,
    /** The OS forbids background work outright ("Restricted" / Samsung "Deep sleeping apps"). */
    val backgroundRestricted: Boolean = false,
    /** Standby bucket RESTRICTED: the OS (or Samsung "Sleeping apps") has parked the app. */
    val sleeping: Boolean = false,
    /** Samsung One UI: has its own sleep lists on top of the stock switches. */
    val samsung: Boolean = false,
    /** "Usage access" (PACKAGE_USAGE_STATS): lets the app read the OS's own screen-time records. Optional. */
    val usageAccessGranted: Boolean = false,
) {
    /** Whether anything on the "keep it alive" checklist still needs the user. */
    val backgroundNeedsAttention: Boolean
        get() = !batteryUnrestricted || backgroundRestricted || sleeping
}

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
    /** System dialog (or settings page) to exempt the app from battery optimization. */
    fun requestBatteryUnrestricted()
    /** The page with "Pause app activity if unused" (Android 11+), app info elsewhere. */
    /** The OEM battery page that holds the sleep lists (Samsung Device care), app info elsewhere. */
    fun openBatterySettings()
    /** Tell the OS the app's language ("system" = follow the device), so system-facing strings match. */
    fun applyAppLanguage(language: String)
    fun openUrl(url: String)
    /** The system page where "Usage access" is granted; no-op where the OS has no such thing. */
    fun openUsageAccessSettings()
    /** Drops Monk's own screen-time archive (the OS record is untouched). */
    fun clearScreenTime()
    /**
     * Foreground time per local day for [packages] and for the phone as a whole over the last
     * [days] days (ending today). Cached briefly by the platform; safe to call on every resume.
     * [ScreenTimeReport.Unsupported] / [ScreenTimeReport.NotGranted] when nothing can be read.
     */
    suspend fun screenTime(days: Int, packages: Set<String>): ScreenTimeReport
    val updater: Updater?
}

@Composable
expect fun AppIcon(packageName: String, size: Dp, modifier: Modifier = Modifier)

expect fun systemLanguage(): String
