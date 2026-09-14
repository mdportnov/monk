package com.mdportnov.monk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a reboot or an update, tell the user if the service did not come back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> MonkNotifications.serviceOff(context)
        }
    }
}
