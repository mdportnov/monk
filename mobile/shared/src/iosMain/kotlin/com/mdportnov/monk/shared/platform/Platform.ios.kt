package com.mdportnov.monk.shared.platform

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.mdportnov.monk.shared.data.KeyValueStore
import com.mdportnov.monk.shared.model.InstalledApp
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

class UserDefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun getString(key: String): String? = defaults.stringForKey(key)
    override fun putString(key: String, value: String) = defaults.setObject(value, key)
}

/**
 * Deliberately inert. iOS has no accessibility-service equivalent; app interception would need the
 * Screen Time / FamilyControls entitlement and a DeviceActivity extension. Until that lands, the
 * shared UI shows an "unsupported" card and none of these calls do anything.
 */
object IosPlatform : MonkPlatform {
    override val supportsBlocking = false
    override suspend fun installedApps(): List<InstalledApp> = emptyList()
    override fun permissions() = PermissionStatus(accessibilityEnabled = false, overlayGranted = false, mayNeedRestrictedSettingsUnlock = false)
    override fun openAccessibilitySettings() = Unit
    override fun openOverlaySettings() = Unit
    override fun openAppInfo() = Unit
}

actual fun systemLanguage(): String = NSLocale.currentLocale.languageCode

@Composable
actual fun AppIcon(packageName: String, size: Dp, modifier: Modifier) {
    Icon(Icons.Outlined.PhoneIphone, null, modifier = modifier.size(size), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
