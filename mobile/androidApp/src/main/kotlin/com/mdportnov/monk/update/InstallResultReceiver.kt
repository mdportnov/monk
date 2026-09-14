package com.mdportnov.monk.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.mdportnov.monk.MonkApplication

/**
 * Status callbacks of a committed [PackageInstaller] session. STATUS_PENDING_USER_ACTION hands us
 * the system confirmation dialog to launch; success needs nothing (the new APK takes over).
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { confirm ->
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> MonkApplication.updater(context)?.onInstallResult(true, null)
            else -> MonkApplication.updater(context)?.onInstallResult(false, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }

    companion object {
        const val ACTION = "com.mdportnov.monk.update.INSTALL_STATUS"
    }
}
