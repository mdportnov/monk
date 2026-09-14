package com.mdportnov.monk

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.mdportnov.monk.shared.MonkRuntime
import com.mdportnov.monk.shared.i18n.stringsForSystem

object MonkNotifications {
    private const val CHANNEL = "monk_status"
    private const val ID_SERVICE_OFF = 1

    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** "Protection stopped" — only when the user opted in, apps are watched, and the service is really off. */
    fun serviceOff(context: Context) {
        if (!MonkRuntime.isInitialized) return
        val config = MonkRuntime.store.config.value
        if (!config.notifyWhenOff || config.apps.isEmpty()) return
        if (AndroidPlatform.isAccessibilityServiceEnabled(context)) return
        if (!granted(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val s = stringsForSystem()
        nm.createNotificationChannel(NotificationChannel(CHANNEL, s.protection, NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_monk_small)
            .setContentTitle(s.serviceOffTitle)
            .setContentText(s.serviceOffBody)
            .setStyle(Notification.BigTextStyle().bigText(s.serviceOffBody))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(ID_SERVICE_OFF, n)
    }

    fun clearServiceOff(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_SERVICE_OFF)
    }
}
