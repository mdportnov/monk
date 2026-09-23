package com.mdportnov.monk

import com.mdportnov.monk.shared.model.BlockMode
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForegroundGateTest {
    private class Fake : ForegroundGate.Effects {
        val launchers = setOf("com.launcher")
        val transient = setOf("com.android.systemui", "com.ime")
        val watched = mutableMapOf("com.insta" to BlockedApp("com.insta", "Insta", BlockMode.DELAY))
        var allowances = mutableMapOf<String, Long>()
        var keyguard = false
        var inCall = false
        var showing: String? = null
        val intercepts = mutableListOf<String>()
        var hidden = 0
        var scheduledDelay: Long? = null
        var scheduledAction: (() -> Unit)? = null
        var now = 1_000_000L

        override fun isLauncher(pkg: String) = pkg in launchers
        override fun isTransient(pkg: String) = pkg in transient
        override fun isActivityWindow(pkg: String, className: String?) = className?.endsWith("Activity") == true
        override fun isKeyguardLocked() = keyguard
        override fun isInCall() = inCall
        override fun decide(pkg: String): Decision {
            val app = watched[pkg] ?: return Decision.Allow
            if ((allowances[pkg] ?: 0) > now) return Decision.Allow
            return Decision.Intercept(app, limitReached = false)
        }
        override fun isWatched(pkg: String) = pkg in watched
        override fun allowanceUntil(pkg: String) = allowances[pkg]
        var nextChange: Long? = null
        override fun nextChangeMillis(pkg: String) = nextChange
        override fun interceptShowingFor() = showing
        var inFront = false
        override fun interceptInFront() = inFront
        override fun intercept(pkg: String, decision: Decision.Intercept) { intercepts += pkg; showing = pkg }
        override fun hideOverlay() { hidden++; showing = null }
        val redrawn = mutableListOf<String>()
        override fun redrawIntercept(pkg: String, decision: Decision.Intercept) { redrawn += pkg }
        override fun schedule(delayMs: Long, action: () -> Unit) { scheduledDelay = delayMs; scheduledAction = action }
        override fun cancelScheduled() { scheduledDelay = null; scheduledAction = null }
        val remembered = mutableListOf<String?>()
        override fun rememberForeground(pkg: String?) { remembered += pkg }
    }

    private fun gate(fx: Fake) = ForegroundGate("com.monk", "com.monk.MainActivity", fx) { fx.now }

    @Test
    fun interceptsOnEnteringWatchedActivity() {
        val fx = Fake(); val g = gate(fx)
        g.onWindow("com.insta", "com.insta.MainActivity")
        assertEquals(listOf("com.insta"), fx.intercepts)
        assertEquals("com.insta", g.lastForeground)
    }

    @Test
    fun ignoresDialogsBubblesAndTransientWindows() {
        val fx = Fake(); val g = gate(fx)
        g.onWindow("com.insta", "android.widget.PopupWindow")
        g.onWindow("com.android.systemui", "com.android.systemui.Shade")
        g.onWindow("com.ime", "com.ime.InputView")
        assertEquals(emptyList<String>(), fx.intercepts)
        assertNull(g.lastForeground)
    }

    @Test
    fun sameAppEventsDoNotRetriggerButLauncherResets() {
        val fx = Fake(); val g = gate(fx)
        g.onWindow("com.insta", "com.insta.MainActivity")
        fx.showing = null
        g.onWindow("com.insta", "com.insta.FeedActivity")
        assertEquals(1, fx.intercepts.size)
        g.onWindow("com.launcher", "com.launcher.Home")
        g.onWindow("com.insta", "com.insta.MainActivity")
        assertEquals(2, fx.intercepts.size)
    }

    @Test
    fun monkMainScreenResetsForegroundButInterceptScreenDoesNot() {
        val fx = Fake(); val g = gate(fx)
        g.onWindow("com.insta", "com.insta.MainActivity")
        g.onWindow("com.monk", "com.monk.InterceptActivity")
        assertEquals("com.insta", g.lastForeground)
        g.onWindow("com.monk", "com.monk.MainActivity")
        assertEquals("com.monk", g.lastForeground)
    }

    @Test
    fun keyguardDefersUntilUnlock() {
        val fx = Fake(); val g = gate(fx)
        fx.keyguard = true
        g.onWindow("com.insta", "com.insta.MainActivity")
        assertEquals(emptyList<String>(), fx.intercepts)
        fx.keyguard = false
        g.onUserPresent()
        assertEquals(listOf("com.insta"), fx.intercepts)
    }

    @Test
    fun keyguardDeferralRecoversWithoutUserPresent() {
        val fx = Fake(); val g = gate(fx)
        fx.keyguard = true
        g.onWindow("com.insta", "com.insta.MainActivity")
        fx.keyguard = false
        g.onWindow("com.insta", "com.insta.FeedActivity")
        assertEquals(listOf("com.insta"), fx.intercepts)
    }

    @Test
    fun allowanceSchedulesReevaluationAtExpiry() {
        val fx = Fake(); val g = gate(fx)
        fx.allowances["com.insta"] = fx.now + 60_000
        g.onWindow("com.insta", "com.insta.MainActivity")
        assertEquals(emptyList<String>(), fx.intercepts)
        assertEquals(60_250L, fx.scheduledDelay)
        fx.now += 61_000
        fx.scheduledAction!!.invoke()
        assertEquals(listOf("com.insta"), fx.intercepts)
    }

    @Test
    fun ruleBoundaryIsScheduledEvenWithoutAllowance() {
        val fx = Fake(); val g = gate(fx)
        fx.watched["com.free"] = BlockedApp("com.free", "Free", BlockMode.DELAY)
        fx.nextChange = 5 * 60_000L
        // Pretend a FREE window: decide() returns Allow for an unwatched-looking case by removing allowances path
        fx.allowances["com.free"] = fx.now + 60 * 60_000L
        g.onWindow("com.free", "com.free.MainActivity")
        assertEquals(5 * 60_000L + 250, fx.scheduledDelay)
    }

    @Test
    fun clockChangeDropsTheTimerAndRejudgesTheForegroundApp() {
        val fx = Fake(); val g = gate(fx)
        fx.allowances["com.insta"] = fx.now + 60 * 60_000L
        g.onWindow("com.insta", "com.insta.MainActivity")
        assertEquals(emptyList<String>(), fx.intercepts)
        fx.allowances.clear()
        g.onClockChanged()
        assertEquals(listOf("com.insta"), fx.intercepts)
        assertNull(fx.scheduledAction)
    }

    @Test
    fun leavingForAnotherAppCancelsTheExpiryTimer() {
        val fx = Fake(); val g = gate(fx)
        fx.allowances["com.insta"] = fx.now + 60_000
        g.onWindow("com.insta", "com.insta.MainActivity")
        g.onWindow("com.launcher", "com.launcher.Home")
        assertNull(fx.scheduledAction)
    }

    @Test
    fun coldStartWindowsUnderALiveInterceptAreNotCountedAgain() {
        val fx = Fake(); val g = gate(fx)
        g.onWindow("com.insta", "com.insta.MainActivity")
        fx.inFront = true
        fx.now += 2_000
        g.onWindow("com.insta", "com.insta.FeedActivity")
        assertEquals(1, fx.intercepts.size)
        // The pause screen left the front (a deep link on top of it): the app is covered again.
        fx.inFront = false
        fx.now += 2_000
        g.onWindow("com.insta", "com.insta.DeepLinkActivity")
        assertEquals(2, fx.intercepts.size)
    }

    @Test
    fun resurfacingAboveLiveInterceptRecoversExceptForEcho() {
        val fx = Fake(); val g = gate(fx)
        g.onWindow("com.insta", "com.insta.MainActivity")
        // echo right after the launch: ignored
        g.onWindow("com.insta", "com.insta.MainActivity")
        assertEquals(1, fx.intercepts.size)
        fx.now += 2_000
        g.onWindow("com.insta", "com.insta.DeepLinkActivity")
        assertEquals(2, fx.intercepts.size)
    }

    @Test
    fun overlayHidesWhenAnotherActivityOrLauncherSurfaces() {
        val fx = Fake(); val g = gate(fx)
        g.onWindow("com.insta", "com.insta.MainActivity")
        g.onWindow("com.other", "com.other.MainActivity")
        assertEquals(1, fx.hidden)
        fx.showing = "com.insta"
        g.onWindow("com.launcher", "com.launcher.Home")
        assertEquals(2, fx.hidden)
    }

    @Test
    fun unlockRejudgesAnExpiredAllowanceEvenWithoutPendingApp() {
        val fx = Fake(); val g = gate(fx)
        fx.allowances["com.insta"] = fx.now + 60_000
        g.onWindow("com.insta", "com.insta.MainActivity")
        assertEquals(emptyList<String>(), fx.intercepts)
        // Phone slept through the deadline: the uptime timer never fired.
        fx.now += 10 * 60_000
        g.onUserPresent()
        assertEquals(listOf("com.insta"), fx.intercepts)
    }

    @Test
    fun unlockWithLiveAllowanceReschedulesFromTheWallClock() {
        val fx = Fake(); val g = gate(fx)
        fx.allowances["com.insta"] = fx.now + 60_000
        g.onWindow("com.insta", "com.insta.MainActivity")
        fx.now += 20_000
        g.onUserPresent()
        assertEquals(emptyList<String>(), fx.intercepts)
        assertEquals(40_250L, fx.scheduledDelay)
    }

    @Test
    fun unlockOnLauncherDoesNothing() {
        val fx = Fake(); val g = gate(fx)
        g.onWindow("com.launcher", "com.launcher.Home")
        g.onUserPresent()
        assertEquals(emptyList<String>(), fx.intercepts)
    }

    @Test
    fun foregroundChangesAreHandedToTheHost() {
        val fx = Fake(); val g = gate(fx)
        g.onWindow("com.insta", "com.insta.MainActivity")
        g.onWindow("com.insta", "com.insta.FeedActivity")
        g.onWindow("com.launcher", "com.launcher.Home")
        assertEquals(listOf<String?>("com.insta", "com.launcher"), fx.remembered)
    }

    @Test
    fun restoredForegroundIsJudgedOnReevaluate() {
        val fx = Fake(); val g = gate(fx)
        g.restore("com.insta")
        assertEquals("com.insta", g.lastForeground)
        g.reevaluateForeground()
        assertEquals(listOf("com.insta"), fx.intercepts)
    }

    @Test
    fun restoreNeverOverridesALiveObservation() {
        val fx = Fake(); val g = gate(fx)
        g.onWindow("com.launcher", "com.launcher.Home")
        g.restore("com.insta")
        assertEquals("com.launcher", g.lastForeground)
        g.reevaluateForeground()
        assertEquals(emptyList<String>(), fx.intercepts)
    }

    @Test
    fun reevaluateUnderKeyguardWaitsForUnlock() {
        val fx = Fake(); val g = gate(fx)
        g.restore("com.insta")
        fx.keyguard = true
        g.reevaluateForeground()
        assertEquals(emptyList<String>(), fx.intercepts)
        fx.keyguard = false
        g.onUserPresent()
        assertEquals(listOf("com.insta"), fx.intercepts)
    }

    @Test
    fun unlockOverALivePauseScreenNeitherCountsAgainNorRestartsIt() {
        val fx = Fake(); val g = gate(fx)
        g.onWindow("com.insta", "com.insta.MainActivity")
        g.onUserPresent()
        g.reevaluateForeground()
        assertEquals(listOf("com.insta"), fx.intercepts)
        assertEquals(listOf("com.insta", "com.insta"), fx.redrawn)
    }

    @Test
    fun anAppOpenedDuringACallIsJudgedOnItsNextWindowAfterIt() {
        val fx = Fake(); val g = gate(fx)
        fx.inCall = true
        g.onWindow("com.insta", "com.insta.MainActivity")
        assertEquals(emptyList<String>(), fx.intercepts)
        fx.inCall = false
        g.onWindow("com.insta", "com.insta.FeedActivity")
        assertEquals(listOf("com.insta"), fx.intercepts)
    }

    @Test
    fun anAllowanceRunningOutUnderTheKeyguardWaitsForTheUnlock() {
        val fx = Fake(); val g = gate(fx)
        fx.allowances["com.insta"] = fx.now + 60_000
        g.onWindow("com.insta", "com.insta.MainActivity")
        val expire = fx.scheduledAction!!
        fx.now += 61_000
        fx.keyguard = true
        expire()
        assertEquals(emptyList<String>(), fx.intercepts)
        fx.keyguard = false
        g.onUserPresent()
        assertEquals(listOf("com.insta"), fx.intercepts)
    }
}
