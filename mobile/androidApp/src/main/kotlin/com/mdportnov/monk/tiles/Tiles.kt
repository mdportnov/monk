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
        val until = now + 15 * 60_000L
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(s.breakConfirmTitle)
            .setMessage(s.breakConfirmBody(formatClock(until)))
            .setPositiveButton(s.startBreak) { _, _ -> store.pauseProtection(until) }
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

    override fun refresh() {
        val tile = qsTile ?: return
        val s = stringsForSystem()
        val now = System.currentTimeMillis()
        val c = monkGraph.store.config.value
        val m = localMoment()
        tile.label = s.tilePause
        // Same state as the home card; the tile adds only the break-specific reasons for "no".
        when (c.state(now, m.dayIso, m.minuteOfDay)) {
            ProtectionState.FOCUS -> { tile.state = Tile.STATE_UNAVAILABLE; tile.subtitleCompat(s.focusUntil(formatClock(c.focusUntil))) }
            ProtectionState.STRICT -> { tile.state = Tile.STATE_UNAVAILABLE; tile.subtitleCompat(s.strictLocked) }
            ProtectionState.BREAK -> { tile.state = Tile.STATE_ACTIVE; tile.subtitleCompat(s.pausedUntil(formatClock(c.pausedUntil))) }
            ProtectionState.OFF -> { tile.state = Tile.STATE_UNAVAILABLE; tile.subtitleCompat(s.protectionOff) }
            ProtectionState.SCHEDULED_OFF -> { tile.state = Tile.STATE_UNAVAILABLE; tile.subtitleCompat(s.scheduleOffShort) }
            ProtectionState.ON -> if (c.canStartBreak(now)) {
                tile.state = Tile.STATE_INACTIVE; tile.subtitleCompat(s.pause15)
            } else {
                tile.state = Tile.STATE_UNAVAILABLE; tile.subtitleCompat(s.breakCooldown(formatClock(c.nextBreakAt(now))))
            }
        }
        tile.updateTile()
    }
}

/** "Monk: focus" — starts a 25-minute focus session after a confirmation, since it cannot be undone. */
class FocusTileService : MonkTileService(TileRegistry.FOCUS) {
    override fun onClick() {
        val now = System.currentTimeMillis()
        val c = monkGraph.store.config.value
        if (c.isFocus(now)) return
        val s = stringsForSystem()
        val until = now + 30 * 60_000L
        // Starting focus ends a running break and turns protection on: say so where it applies.
        val notes = listOfNotNull(s.endsBreakNote.takeIf { c.isPaused(now) }, s.turnsOnNote.takeIf { !c.enabled })
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(s.focusConfirmTitle)
            .setMessage((listOf(s.focusConfirmBody(formatClock(until))) + notes).joinToString("\n\n"))
            .setPositiveButton(s.start) { _, _ ->
                monkGraph.store.startFocus(until)
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
        if (c.isFocus(now)) {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitleCompat(s.focusUntil(formatClock(c.focusUntil)))
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitleCompat(s.focus30)
        }
        tile.updateTile()
    }
}
