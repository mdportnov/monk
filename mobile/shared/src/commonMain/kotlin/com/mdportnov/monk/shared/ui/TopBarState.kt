package com.mdportnov.monk.shared.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Where a page's large header rests and how far the page has scrolled. The bar morphs its
 * heading between the resting spot and the compact slot by reading this in placement and draw,
 * never in composition: a scroll moves one graphics layer, not the tree.
 *
 * [scrollPx] reads the page's own scroll state; [range] is how much scroll turns the large
 * header fully into the bar. Everything is in pixels of the root.
 */
class HeaderAnchor(val scrollPx: () -> Float) {
    /** Resting position of the header content (top-start corner, root coordinates). */
    var startX by mutableFloatStateOf(0f)
    var startY by mutableFloatStateOf(0f)
    /** Outer height of the header slot in the page, reported by the slot when it is laid out. */
    var slotHeight by mutableFloatStateOf(0f)
    /** Extra scroll after the slot has passed, so the morph lands as the content meets the bar. */
    var tail by mutableFloatStateOf(0f)
    val range: Float get() = slotHeight + tail

    /**
     * Card mode (Home): the page draws its own title, which only fades, and the bar's heading is
     * a condensed status row that lifts out of the status card's header as the card slides under
     * the bar. [cardTop] reads the root y of the card's top edge each frame: null when the page
     * has no card, −∞ once it has scrolled past, +∞ while it is still below the viewport.
     */
    var cardTop: (() -> Float?)? = null
    var cardX = 0f
    var cardWidth = 0f
    /** Card padding, glyph half-size, header row height, where the bar ends, and the lead-in before contact. */
    var cardPad = 0f
    var glyphHalf = 0f
    var rowHeight = 0f
    var barBottom = 0f
    var lead = 0f
    /** The mood of the card, as the bar's veil should wear it when condensed. */
    var tint by mutableStateOf(Color.Unspecified)

    /** Root y of the top of the card's header row, or null off card mode. */
    fun rowTop(): Float? = cardTop?.invoke()?.let { it + cardPad }

    /** 0 = large header at rest, 1 = compact bar. */
    fun progress(): Float {
        val row = rowTop()
        if (row != null) return ((barBottom + lead - row) / (rowHeight + lead)).coerceIn(0f, 1f)
        val r = range
        val at = scrollPx()
        // Slot not laid out yet (a tab restored under a pushed page): a scrolled page is condensed.
        if (r <= 0f) return if (at > 0f) 1f else 0f
        return (at / r).coerceIn(0f, 1f)
    }

    /** How far to scroll so the header settles at whichever end is nearer; null when it is settled. */
    fun settleDelta(): Float? {
        val p = progress()
        if (p >= 1f) return null
        val row = rowTop()
        if (row != null) {
            // Card mode: below the midpoint the whole way home, so the page title (which fades
            // over the first few dp of scroll) is back too; −∞ asks for "scroll to the top".
            if (p >= 0.5f) return (1f - p) * (rowHeight + lead)
            val at = scrollPx()
            if (at <= 0f) return null
            return if (at.isFinite()) -at else Float.NEGATIVE_INFINITY
        }
        if (p <= 0f) return null
        return if (p >= 0.5f) (1f - p) * range else -p * range
    }
}

/** The anchor of the page being composed; the page's header slot reports into it. */
val LocalHeaderAnchor = compositionLocalOf<HeaderAnchor?> { null }

/**
 * One top bar for the whole app, owned by the root. Pages describe what it should show; the bar
 * itself never re-mounts, so its title moves from page to page with the page's own motion
 * instead of sliding away with the old page and back in with the new one.
 *
 * Every page gets its own instance; the root shows the one that belongs to the page being
 * navigated to, so a page on its way out can keep describing itself without touching the bar.
 * [level] orders pages left to right (tabs by position, pushed pages deeper), which is what
 * decides the direction the title travels in.
 *
 * A page with a large header hands over an [anchor]: the bar then draws the heading itself, at
 * full size where the page's header slot rests and condensed in the bar once scrolled, with
 * every state in between driven by the scroll. A page without one gets the bar at rest
 * ([visible]) or none at all.
 */
class TopBarState {
    var title by mutableStateOf("")
    var visible by mutableStateOf(false)
    var level by mutableStateOf(0)
    var navigationIcon by mutableStateOf<(@Composable () -> Unit)?>(null)
    var actions by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
    /** The heading as drawn large; null means a plain title text. */
    var heading by mutableStateOf<(@Composable () -> Unit)?>(null)
    var anchor by mutableStateOf<HeaderAnchor?>(null)
    /** False until the page has described itself once; the root keeps the previous bar until then. */
    var bound by mutableStateOf(false)
        private set

    fun set(
        title: String,
        visible: Boolean,
        navigationIcon: (@Composable () -> Unit)? = null,
        actions: (@Composable RowScope.() -> Unit)? = null,
        level: Int = 0,
        heading: (@Composable () -> Unit)? = null,
        anchor: HeaderAnchor? = null,
    ) {
        this.title = title
        this.visible = visible
        this.level = level
        this.navigationIcon = navigationIcon
        this.actions = actions
        this.heading = heading
        this.anchor = anchor
        bound = true
    }
}
