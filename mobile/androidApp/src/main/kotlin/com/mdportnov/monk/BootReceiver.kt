package com.mdportnov.monk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a reboot or an update the service needs a few seconds to rebind; checking immediately
 * would flash a false "protection stopped". The check is deferred through an alarm instead.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val check = PendingIntent.getBroadcast(
                    context, 0,
                    Intent(context, ServiceCheckReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val am = context.getSystemService(AlarmManager::class.java)
                am.set(AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + 20_000, check)
            }
        }
    }
}

class ServiceCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = MonkNotifications.serviceOff(context)
}
