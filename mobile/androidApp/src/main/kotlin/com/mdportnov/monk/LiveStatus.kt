package com.mdportnov.monk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mdportnov.monk.shared.data.localMoment
import com.mdportnov.monk.shared.i18n.stringsFor
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.ProtectionState

/**
 * An ongoing notification with the focus / break countdown, opt-in. On Android 16 it asks to be
 * promoted (a Live Update: pinned in the shade and on the lock screen); elsewhere it is a plain
 * ongoing notification with a chronometer. Times itself out when the window ends.
 */
object LiveStatus {
    private const val CHANNEL = "monk_live"
    private const val ID = 2

    fun sync(context: Context, config: MonkConfig) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val now = System.currentTimeMillis()
        val m = localMoment()
        // The same state the home card shows: a break the schedule has already overtaken is not a break.
        val (until, isFocus) = when (config.state(now, m.dayIso, m.minuteOfDay)) {
            ProtectionState.FOCUS -> config.focusUntil to true
            ProtectionState.BREAK -> config.pausedUntil to false
            else -> null to false
        }
        if (!config.liveStatus || until == null || !MonkNotifications.granted(context)) {
            nm.cancel(ID)
            return
        }
        val s = stringsFor(config.language)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, s.liveStatus, NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_monk_small)
            .setContentTitle(if (isFocus) s.liveFocusTitle("") else s.liveBreakTitle)
            .setContentText(if (isFocus) s.liveFocusBody else s.liveBreakBody)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(until)
            .setShowWhen(true)
            .setTimeoutAfter((until - now).coerceAtLeast(1_000))
            .setCategory(if (isFocus) Notification.CATEGORY_PROGRESS else Notification.CATEGORY_STATUS)
        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 Live Update. The setter is missing from this SDK stub revision, so it is
            // looked up by name; a ROM without it just shows the ongoing notification.
            runCatching {
                Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType).invoke(builder, true)
            }
            builder.setStyle(
                Notification.ProgressStyle()
                    .setProgress(0)
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100)))
                    .setStyledByProgress(false),
            )
        }
        nm.notify(ID, builder.build())
    }
}
