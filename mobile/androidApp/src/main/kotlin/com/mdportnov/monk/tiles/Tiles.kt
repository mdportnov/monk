package com.mdportnov.monk.tiles

import android.app.AlertDialog
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mdportnov.monk.shared.MonkRuntime
import com.mdportnov.monk.shared.data.formatClock
import com.mdportnov.monk.shared.i18n.stringsForSystem

/** "Monk: pause" — toggles a 15-minute protection pause. Reversible, so no confirmation. */
class PauseTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        if (!MonkRuntime.isInitialized) return
        val store = MonkRuntime.store
        val now = System.currentTimeMillis()
        val c = store.config.value
        if (c.isStrict(now) || c.isFocus(now)) return
        if (c.isPaused(now)) store.resumeProtection() else store.pauseProtection(now + 15 * 60_000L)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val s = stringsForSystem()
        val now = System.currentTimeMillis()
        val c = MonkRuntime.store.config.value
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
class FocusTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        if (!MonkRuntime.isInitialized) return
        val now = System.currentTimeMillis()
        if (MonkRuntime.store.config.value.isFocus(now)) return
        val s = stringsForSystem()
        val until = now + 25 * 60_000L
        val dialog = AlertDialog.Builder(this)
            .setTitle(s.focusConfirmTitle)
            .setMessage(s.focusConfirmBody(formatClock(until)))
            .setPositiveButton(s.start) { _, _ -> MonkRuntime.store.startFocus(until); refresh() }
            .setNegativeButton(s.cancel, null)
            .create()
        showDialog(dialog)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val s = stringsForSystem()
        val c = MonkRuntime.store.config.value
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
