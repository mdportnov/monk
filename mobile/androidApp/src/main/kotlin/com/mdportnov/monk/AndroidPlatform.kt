package com.mdportnov.monk

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.mdportnov.monk.shared.model.InstalledApp
import com.mdportnov.monk.shared.platform.MonkPlatform
import com.mdportnov.monk.shared.platform.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPlatform(private val app: Context) : MonkPlatform {
    override val supportsBlocking = true

    override suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = app.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .asSequence()
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { (pkg, _) -> pkg != app.packageName }
            .distinctBy { it.first }
            .map { (pkg, label) -> InstalledApp(pkg, label) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    override fun permissions() = PermissionStatus(
        accessibilityEnabled = isAccessibilityServiceEnabled(app),
        overlayGranted = Settings.canDrawOverlays(app),
        mayNeedRestrictedSettingsUnlock = Build.VERSION.SDK_INT >= 33 && !installedFromStore(),
    )

    override fun openAccessibilitySettings() = launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    override fun openOverlaySettings() =
        launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${app.packageName}")))

    override fun openAppInfo() =
        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))

    private fun launch(intent: Intent) {
        runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun installedFromStore(): Boolean {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= 30) app.packageManager.getInstallSourceInfo(app.packageName).installingPackageName
            else @Suppress("DEPRECATION") app.packageManager.getInstallerPackageName(app.packageName)
        }.getOrNull()
        return installer == "com.android.vending"
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
