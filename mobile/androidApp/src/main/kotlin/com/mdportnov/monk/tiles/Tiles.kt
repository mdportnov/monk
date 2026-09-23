package com.mdportnov.monk.tiles

import android.app.AlertDialog
import android.os.Build
import android.os.CountDownTimer
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mdportnov.monk.MonkAccessibilityService
import com.mdportnov.monk.monkGraph
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.data.localMoment
import com.mdportnov.monk.shared.data.LocalMoment
import com.mdportnov.monk.shared.i18n.Strings
import com.mdportnov.monk.shared.model.BuiltInRoutines
import com.mdportnov.monk.shared.model.MonkConfig
import com.mdportnov.monk.shared.model.Routine
import com.mdportnov.monk.shared.model.ProtectionState
import com.mdportnov.monk.shared.i18n.stringsForSystem
import com.mdportnov.monk.shared.ui.components.COUNTDOWN_CONFIRM_SECONDS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Base: re-render while the shade is open, from the store rather than from a snapshot. */
abstract class MonkTileService(private val id: String) : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    override fun onTileAdded() = TileRegistry.set(this, id, true)
    override fun onTileRemoved() = TileRegistry.set(this, id, false)

    override fun onStartListening() {
        // Listening means the tile is in the shade, whatever the add/remove callbacks said before.
        TileRegistry.set(this, id, true)
        startWatching()
    }

    private fun startWatching() {
        watching?.cancel()
        watching = scope.launch { monkGraph.store.config.collect { refresh() } }
    }

    override fun onStopListening() {
        watching?.cancel()
        watching = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    protected abstract fun refresh()

    protected fun Tile.subtitleCompat(text: CharSequence) {
        if (Build.VERSION.SDK_INT >= 29) subtitle = text
    }
}

/**
 * "Monk: break" — a 15-minute break behind the same ten-second breath as on the home card;
 * ending it early is one tap, since that only strengthens protection.
 */
class PauseTileService : MonkTileService(TileRegistry.PAUSE) {
    override fun onClick() {
        val store = monkGraph.store
        val now = System.currentTimeMillis()
        val c = store.config.value
        val m = localMoment()
        if (c.state(now, m.dayIso, m.minuteOfDay) == ProtectionState.BREAK) { store.resumeProtection(); return }
        if (!store.canStartBreak(now)) return
        val s = stringsForSystem()
        // Fixed when the breath is over, not at the tap: the dialog may stay up a while.
        val until = { System.currentTimeMillis() + 15 * 60_000L }
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(s.breakConfirmTitle)
            .setMessage(s.breakConfirmBody(formatClock(until() + COUNTDOWN_CONFIRM_SECONDS * 1000L)))
            .setPositiveButton(s.startBreak) { _, _ -> store.pauseProtection(until()) }
            .setNegativeButton(s.cancel, null)
            .create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.isEnabled = false
            val timer = object : CountDownTimer(COUNTDOWN_CONFIRM_SECONDS * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) { button.text = s.countdownWait(((millisUntilFinished + 999) / 1000).toInt()) }
                override fun onFinish() { button.text = s.startBreak; button.isEnabled = true }
            }.start()
            dialog.setOnDismissListener { timer.cancel() }
        }
        showDialog(dialog)
    }

    /**
     * The tile offers only breaks its own onClick would accept, and when it refuses it says the
     * actual reason. It used to print a cooldown time while a break was already running, and to
     * offer a break outside the base hours that the tap then silently dropped.
     */
    private fun offerBreak(tile: Tile, c: MonkConfig, m: LocalMoment, now: Long, s: Strings) {
        when {
            c.isPaused(now) -> { tile.state = Tile.STATE_ACTIVE; tile.subtitleCompat(s.pausedUntil(formatClock(c.pausedUntil))) }
            !c.schedule.isActive(m.dayIso, m.minuteOfDay) -> { tile.state = Tile.STATE_UNAVAILABLE; tile.subtitleCompat(s.scheduleOffShort) }
            c.canStartBreak(now, m.dayIso, m.minuteOfDay) -> { tile.state = Tile.STATE_INACTIVE; tile.subtitleCompat(s.pause15) }
            else -> { tile.state = Tile.STATE_UNAVAILABLE; tile.subtitleCompat(s.breakCooldown(formatClock(c.nextBreakAt(now)))) }
        }
    }

    override fun refresh() {
        val tile = qsTile ?: return
        val s = stringsForSystem()
        val now = System.currentTimeMillis()
        val c = monkGraph.store.config.value
        val m = localMoment()
        tile.label = s.tilePause
        // Same state as the home card; the tile adds only the break-specific reasons for "no".
        when (c.state(now, m.dayIso, m.minuteOfDay)) {
            ProtectionState.ROUTINE -> {
                val run = c.activeRun(now)
                if (run != null) {
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.subtitleCompat(s.routineRunsUntil(formatClock(run.until)))
                } else {
                    // A routine open on its own hours does not forbid a break; only a session does.
                    offerBreak(tile, c, m, now, s)
                }
            }
            ProtectionState.STRICT -> { tile.state = Tile.STATE_UNAVAILABLE; tile.subtitleCompat(s.strictLocked) }
            ProtectionState.BREAK -> { tile.state = Tile.STATE_ACTIVE; tile.subtitleCompat(s.pausedUntil(formatClock(c.pausedUntil))) }
            ProtectionState.OFF -> { tile.state = Tile.STATE_UNAVAILABLE; tile.subtitleCompat(s.protectionOff) }
            ProtectionState.SCHEDULED_OFF -> offerBreak(tile, c, m, now, s)
            ProtectionState.ON -> offerBreak(tile, c, m, now, s)
        }
        tile.updateTile()
    }
}

/** Why this tile cannot start anything, in the words of the actual cause. */
private fun tileBlocker(c: MonkConfig, routine: Routine?, s: Strings): String = when {
    routine == null -> s.tileRoutineNone
    c.apps.isEmpty() -> s.tileNoApps
    !routine.enabled -> s.tileRoutineOff(s.routineName(routine))
    else -> s.routineCoversNothingNote(s.routineName(routine))
}

/**
 * "Monk: focus" — from the shade, the built-in focus routine for its own default length, behind a
 * confirmation, since it cannot be undone. The tile deliberately starts one fixed routine rather
 * than offering a menu: there is no room in a tile for a choice, and Focus is the one people reach
 * for without thinking. Everything else is a tap away on the routines screen.
 */
class FocusTileService : MonkTileService(TileRegistry.FOCUS) {
    override fun onClick() {
        val store = monkGraph.store
        val now = System.currentTimeMillis()
        val c = store.config.value
        val s = stringsForSystem()
        val live = c.activeRun(now)
        // Focus already running: nothing to do. Another routine running: say which one, rather
        // than swallowing the tap — a tile that does nothing and explains nothing is the worst
        // answer available, and this is the one surface with no room to show it passively.
        if (live != null) {
            if (live.routineId != BuiltInRoutines.FOCUS) {
                val other = c.runningRoutine(now)?.let { s.routineName(it) }.orEmpty()
                showDialog(
                    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(s.routines)
                        .setMessage(s.routineAlreadyRunning(other))
                        .setPositiveButton(s.gotIt, null)
                        .create(),
                )
            }
            return
        }
        val focus = c.routine(BuiltInRoutines.FOCUS)
        val routine = focus?.takeIf { it.enabled && !it.coversNothing && c.apps.isNotEmpty() }
        if (routine == null) {
            showDialog(
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(s.tileFocus)
                    .setMessage(tileBlocker(c, focus, s))
                    .setPositiveButton(s.gotIt, null)
                    .create(),
            )
            return
        }
        val name = s.routineName(routine)
        val minutes = routine.manualMinutes.coerceAtLeast(5)
        val until = now + minutes * 60_000L
        // Starting one ends a running break and turns protection on: say so where it applies.
        val notes = listOfNotNull(s.endsBreakNote.takeIf { c.isPaused(now) }, s.turnsOnNote.takeIf { !c.enabled })
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(s.routineStartTitle(name))
            .setMessage((listOf(s.routineStartBody(name, formatClock(until))) + notes).joinToString("\n\n"))
            .setPositiveButton(s.start) { _, _ ->
                store.startRoutine(routine.id, System.currentTimeMillis() + minutes * 60_000L)
                // The app under a live pause screen must be judged again right away.
                MonkAccessibilityService.reevaluateForeground()
            }
            .setNegativeButton(s.cancel, null)
            .create()
        showDialog(dialog)
    }

    override fun refresh() {
        val tile = qsTile ?: return
        val s = stringsForSystem()
        val c = monkGraph.store.config.value
        val now = System.currentTimeMillis()
        tile.label = s.tileFocus
        val run = c.activeRun(now)
        val routine = c.routine(BuiltInRoutines.FOCUS)
        when {
            // Only Focus lights this tile up. Another routine's session is somebody else's
            // commitment, and showing it under the word "focus" would name the wrong promise.
            run?.routineId == BuiltInRoutines.FOCUS -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitleCompat(s.until(formatClock(run.until)))
            }
            run != null -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitleCompat(c.runningRoutine(now)?.let { s.routineName(it) } ?: s.routineEyebrow)
            }
            routine == null || !routine.enabled || routine.coversNothing || c.apps.isEmpty() -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitleCompat(tileBlocker(c, routine, s))
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitleCompat(s.duration(routine.manualMinutes * 60_000L))
            }
        }
        tile.updateTile()
    }
}
