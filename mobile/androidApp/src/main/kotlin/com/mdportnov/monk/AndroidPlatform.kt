package com.mdportnov.monk

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.StatusBarManager
import android.app.usage.UsageStatsManager
import android.os.PowerManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import com.mdportnov.monk.shared.i18n.stringsForSystem
import com.mdportnov.monk.shared.data.ScreenTimeArchive
import com.mdportnov.monk.shared.model.InstalledApp
import com.mdportnov.monk.shared.platform.SharedPrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.mdportnov.monk.shared.model.ScreenTimeReport
import com.mdportnov.monk.shared.platform.MonkPlatform
import com.mdportnov.monk.shared.platform.PermissionStatus
import com.mdportnov.monk.shared.platform.Updater
import com.mdportnov.monk.tiles.FocusTileService
import com.mdportnov.monk.tiles.PauseTileService
import com.mdportnov.monk.tiles.TileRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class AndroidPlatform(private val app: Context, override val updater: Updater) : MonkPlatform {
    override val supportsBlocking = true
    private val screenTime = ScreenTimeProvider(app, ScreenTimeArchive(SharedPrefsStore(app, "monk_screen")))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _permissions = MutableStateFlow(readPermissions())
    override val permissions: StateFlow<PermissionStatus> = _permissions

    /** Called on Activity resume and when the accessibility service connects / unbinds. */
    override fun refreshPermissions() {
        val next = readPermissions()
        if (next.usageAccessGranted != _permissions.value.usageAccessGranted) screenTime.invalidate()
        _permissions.value = next
    }

    private fun readPermissions() = PermissionStatus(
        accessibilityEnabled = isAccessibilityServiceEnabled(app),
        mayNeedRestrictedSettingsUnlock = isRestrictedSideload(),
        notificationsGranted = MonkNotifications.granted(app),
        tilesAdded = TileRegistry.added.value.size,
        tilesTotal = 2,
        canRequestTiles = Build.VERSION.SDK_INT >= 33,
        batteryUnrestricted = runCatching { app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName) }.getOrDefault(true),
        backgroundRestricted = Build.VERSION.SDK_INT >= 28 &&
            runCatching { app.getSystemService(ActivityManager::class.java).isBackgroundRestricted }.getOrDefault(false),
        sleeping = Build.VERSION.SDK_INT >= 30 &&
            runCatching { app.getSystemService(UsageStatsManager::class.java).appStandbyBucket >= UsageStatsManager.STANDBY_BUCKET_RESTRICTED }.getOrDefault(false),
        samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
        usageAccessGranted = screenTime.granted(),
    )

    override suspend fun screenTime(days: Int, packages: Set<String>): ScreenTimeReport = screenTime.report(days, packages)

    override fun clearScreenTime() = screenTime.clearArchive()

    /** Copies the days the OS still holds into Monk's archive; cheap (cached a minute), safe on every resume. */
    fun snapshotScreenTime(packages: Set<String>) {
        if (!screenTime.granted()) return
        scope.launch { runCatching { screenTime.report(ScreenTimeProvider.OS_WINDOW_DAYS, packages) } }
    }

    /** Straight to our own row where the OS supports the package URI (Android 10+ on most ROMs), the full list otherwise. */
    override fun openUsageAccessSettings() {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${app.packageName}"))
        if (!launchIfResolvable(direct)) launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

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

    override fun openAccessibilitySettings() = launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    override fun openAppInfo() =
        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))

    /**
     * The one-tap system dialog first (needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, declared);
     * the full list as a fallback on ROMs that hide the dialog.
     */
    override fun requestBatteryUnrestricted() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${app.packageName}"))
        if (!launchIfResolvable(direct)) launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** Samsung Device care → Battery, where "Never sleeping apps" lives; app info everywhere else. */
    override fun openBatterySettings() {
        val samsung = Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
        if (!launchIfResolvable(samsung)) openAppInfo()
    }

    private fun launchIfResolvable(intent: Intent): Boolean {
        if (app.packageManager.resolveActivity(intent, 0) == null) return false
        return runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /**
     * Asks the system to add the tiles that are not in the shade yet, one request at a time:
     * a second request while the first dialog is up is refused as "request in progress".
     * "Already added" answers are recorded so the row can say so instead of doing nothing.
     */
    override fun requestAddTiles() {
        if (Build.VERSION.SDK_INT < 33) return
        val sbm = app.getSystemService(StatusBarManager::class.java) ?: return
        val s = stringsForSystem()
        val queue = ArrayDeque(
            listOf(
                Triple(TileRegistry.FOCUS, FocusTileService::class.java, s.tileFocus to R.drawable.ic_tile_focus),
                Triple(TileRegistry.PAUSE, PauseTileService::class.java, s.tilePause to R.drawable.ic_tile_pause),
            ).filter { it.first !in TileRegistry.added.value },
        )
        fun next() {
            val (id, cls, look) = queue.removeFirstOrNull() ?: run { refreshPermissions(); return }
            runCatching {
                sbm.requestAddTileService(ComponentName(app, cls), look.first, Icon.createWithResource(app, look.second), app.mainExecutor) { result ->
                    when (result) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> TileRegistry.set(app, id, true)
                    }
                    next()
                }
            }.onFailure { next() }
        }
        next()
    }

    /**
     * Android 13+ per-app locale: the accessibility service label, tiles and notifications are
     * rendered by the system from resources, so they follow only if the OS knows the choice.
     * Also flips Locale.getDefault() in-process, which stringsForSystem() reads.
     */
    override fun applyAppLanguage(language: String) {
        if (Build.VERSION.SDK_INT < 33) return
        val lm = app.getSystemService(android.app.LocaleManager::class.java) ?: return
        val wanted = if (language == "system") android.os.LocaleList.getEmptyLocaleList() else android.os.LocaleList.forLanguageTags(language)
        if (lm.applicationLocales != wanted) runCatching { lm.applicationLocales = wanted }
    }

    override fun openUrl(url: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

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
        return source.packageSource == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE ||
            source.packageSource == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
    }

    companion object {
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            if (MonkAccessibilityService.isConnected) return true
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val ours = "${context.packageName}/${MonkAccessibilityService::class.java.name}"
            val listed = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.id == ours || it.resolveInfo.serviceInfo.let { si -> "${si.packageName}/${si.name}" == ours } }
            if (listed) return true
            // The manager's list is rebuilt lazily after resume; the settings string is the source of truth.
            val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return setting.split(':').any { it.equals(ours, ignoreCase = true) || it.equals(ComponentName.unflattenFromString(ours)?.flattenToShortString(), ignoreCase = true) }
        }
    }
}
