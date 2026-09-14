package com.mdportnov.monk

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import com.mdportnov.monk.shared.model.InstalledApp
import com.mdportnov.monk.shared.platform.MonkPlatform
import com.mdportnov.monk.shared.platform.PermissionStatus
import com.mdportnov.monk.shared.platform.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPlatform(private val app: Context, override val updater: Updater) : MonkPlatform {
    override val supportsBlocking = true

    override suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = app.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val imm = app.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val excluded = SystemPackages.essential(app) + imm.inputMethodList.map { it.packageName }
        pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .asSequence()
            .map { it.activityInfo.packageName }
            .filter { it !in excluded }
            .distinct()
            .mapNotNull { pkg ->
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                    .getOrNull()
                    ?.let { InstalledApp(pkg, it) }
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    override fun permissions() = PermissionStatus(
        accessibilityEnabled = isAccessibilityServiceEnabled(app),
        mayNeedRestrictedSettingsUnlock = isRestrictedSideload(),
    )

    override fun openAccessibilitySettings() = launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    override fun openAppInfo() =
        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))

    private fun launch(intent: Intent) {
        runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Android 13+ blocks the accessibility toggle for APKs installed from a downloaded file
     * (browser, file manager, F-Droid-style installers). `adb install` and store installs are not
     * affected. The user must tap the greyed toggle once, then App info → ⋮ → Allow restricted settings.
     */
    private fun isRestrictedSideload(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val source = runCatching { app.packageManager.getInstallSourceInfo(app.packageName) }.getOrNull() ?: return false
        return source.packageSource == android.content.pm.PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE ||
            source.packageSource == android.content.pm.PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
    }

    companion object {
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val ours = "${context.packageName}/${MonkAccessibilityService::class.java.name}"
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.id == ours || it.resolveInfo.serviceInfo.let { si -> "${si.packageName}/${si.name}" == ours } }
        }
    }
}
