package com.mdportnov.monk

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * The little the accessibility service needs to pick up where it left off after the system
 * killed and rebound it (low memory, an update, a battery manager): which app was in front,
 * and the wall/boot clock pair that lets a clock change made while Monk was dead be noticed.
 * Everything is tagged with the boot count, so a reboot invalidates it.
 */
class RuntimeState(context: Context) {
    private val prefs = context.getSharedPreferences("monk_runtime", Context.MODE_PRIVATE)
    private val bootCount = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    private val sameBoot: Boolean get() = bootCount >= 0 && prefs.getInt(KEY_BOOT, -2) == bootCount

    /** Foreground app persisted by the previous incarnation, if it was this same boot. */
    val foreground: String? get() = if (sameBoot) prefs.getString(KEY_FOREGROUND, null) else null

    /**
     * Some ROMs drop the pause-screen Activity launch from a service; every miss costs the
     * guard delay with the app visible. After two misses in a row the overlay goes first.
     */
    val preferOverlay: Boolean get() = prefs.getInt(KEY_LAUNCH_MISSES, 0) >= 2
    fun noteLaunchMiss() = prefs.edit().putInt(KEY_LAUNCH_MISSES, prefs.getInt(KEY_LAUNCH_MISSES, 0) + 1).apply()
    fun noteLaunchOk() { if (prefs.getInt(KEY_LAUNCH_MISSES, 0) != 0) prefs.edit().putInt(KEY_LAUNCH_MISSES, 0).apply() }

    fun rememberForeground(pkg: String?) {
        prefs.edit().putInt(KEY_BOOT, bootCount).putString(KEY_FOREGROUND, pkg).apply()
    }

    /**
     * How far the wall clock moved beyond what the boot clock says has elapsed since the last
     * anchor — the drift that a TIME_CHANGED broadcast would have reported had we been alive.
     * Null after a reboot or when no anchor exists.
     */
    fun driftSinceAnchor(): Long? {
        if (!sameBoot || !prefs.contains(KEY_WALL)) return null
        val wall = prefs.getLong(KEY_WALL, 0)
        val elapsed = prefs.getLong(KEY_ELAPSED, 0)
        return (System.currentTimeMillis() - wall) - (SystemClock.elapsedRealtime() - elapsed)
    }

    fun anchorClock() {
        prefs.edit()
            .putInt(KEY_BOOT, bootCount)
            .putLong(KEY_WALL, System.currentTimeMillis())
            .putLong(KEY_ELAPSED, SystemClock.elapsedRealtime())
            .apply()
    }

    private companion object {
        const val KEY_BOOT = "boot"
        const val KEY_FOREGROUND = "foreground"
        const val KEY_WALL = "wall"
        const val KEY_ELAPSED = "elapsed"
        const val KEY_LAUNCH_MISSES = "launch_misses"
    }
}
