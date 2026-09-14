package com.mdportnov.monk.tiles

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which Quick Settings tiles are in the shade. The OS never exposes this directly: the only
 * signals are TileService.onTileAdded / onTileRemoved (API 24+) and, on 33+, the
 * TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED answer to an add request. Both feed this file.
 */
object TileRegistry {
    private const val PREFS = "monk_tiles"
    private val _added = MutableStateFlow<Set<String>>(emptySet())
    val added: StateFlow<Set<String>> = _added

    fun load(context: Context) {
        _added.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("added", emptySet()).orEmpty().toSet()
    }

    fun set(context: Context, tile: String, present: Boolean) {
        val next = if (present) _added.value + tile else _added.value - tile
        if (next == _added.value) return
        _added.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("added", next).apply()
    }

    const val PAUSE = "pause"
    const val FOCUS = "focus"
}
