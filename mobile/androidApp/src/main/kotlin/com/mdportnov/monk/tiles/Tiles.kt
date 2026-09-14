package com.mdportnov.monk.tiles

import android.app.AlertDialog
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mdportnov.monk.MonkAccessibilityService
import com.mdportnov.monk.monkGraph
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.i18n.stringsForSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Base: re-render while the shade is open, from the store rather than from a snapshot. */
abstract class MonkTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    override fun onStartListening() {
        watching?.cancel()
        watching = scope.launch { monkGraph.store.config.collect { refresh() } }
    }

    override fun onStopListening() {
        watching?.cancel()
        watching = null
    }

    override fun onDestroy() {
        scope.launch { }.cancel()
        super.onDestroy()
    }

    protected abstract fun refresh()
}

/** "Monk: pause" — toggles a 15-minute protection pause. Reversible, so no confirmation. */
class PauseTileService : MonkTileService() {
    override fun onClick() {
        val store = monkGraph.store
        val now = System.currentTimeMillis()
        val c = store.config.value
        if (c.isStrict(now) || c.isFocus(now)) return
        if (c.isPaused(now)) store.resumeProtection() else store.pauseProtection(now + 15 * 60_000L)
    }

    override fun refresh() {
        val tile = qsTile ?: return
        val s = stringsForSystem()
        val now = System.currentTimeMillis()
        val c = monkGraph.store.config.value
        tile.label = s.tilePause
        when {
            c.isStrict(now) || c.isFocus(now) -> { tile.state = Tile.STATE_UNAVAILABLE; tile.subtitle = s.strictLocked }
            c.isPaused(now) -> { tile.state = Tile.STATE_ACTIVE; tile.subtitle = s.pausedUntil(formatClock(c.pausedUntil)) }
            else -> { tile.state = Tile.STATE_INACTIVE; tile.subtitle = s.pause15 }
        }
        tile.updateTile()
    }
}

/** "Monk: focus" — starts a 25-minute focus session after a confirmation, since it cannot be undone. */
class FocusTileService : MonkTileService() {
    override fun onClick() {
        val now = System.currentTimeMillis()
        if (monkGraph.store.config.value.isFocus(now)) return
        val s = stringsForSystem()
        val until = now + 25 * 60_000L
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(s.focusConfirmTitle)
            .setMessage(s.focusConfirmBody(formatClock(until)))
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
            tile.subtitle = s.focusUntil(formatClock(c.focusUntil))
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = s.focus25
        }
        tile.updateTile()
    }
}
