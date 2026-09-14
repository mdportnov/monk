package com.mdportnov.monk.shared.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import com.mdportnov.monk.shared.ui.Motion
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import com.mdportnov.monk.shared.ui.theme.MonkColors
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.lerp as lerpColor
import com.mdportnov.monk.shared.ui.HeaderAnchor
import com.mdportnov.monk.shared.ui.LocalHeaderAnchor
import com.mdportnov.monk.shared.ui.TopBarState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Liquid glass: blur of whatever scrolls underneath, tinted with the surface colour. In the
 * dark theme the tint leans violet-black — the same ink as the pause screen — so the chrome
 * reads as part of the brand rather than a grey slab. Taps on empty chrome are swallowed so
 * they never reach the row that happens to sit beneath. [alpha], read inside the effect on
 * every change, lets the glass condense out of nothing without touching composition.
 */
@Composable
fun Modifier.monkGlass(hazeState: HazeState, consumeTaps: Boolean = true, alpha: (() -> Float)? = null): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Dark: the surface leaning towards the accent (violet-black on the brand palette, the
    // wallpaper's hue under dynamic colour); light: the tonal container.
    val tint = if (dark) lerpColor(MaterialTheme.colorScheme.surface, chromeAccent(), 0.14f) else MaterialTheme.colorScheme.surfaceContainer
    val style = if (dark) HazeMaterials.ultraThin(tint) else HazeMaterials.thin(tint)
    val glass = if (alpha == null) this.hazeEffect(hazeState, style) else this.hazeEffect(hazeState, style) { this.alpha = alpha() }
    if (!consumeTaps) return glass
    return glass.swallowTouches()
}

private fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
}

/**
 * The chrome's two accents, taken from the colour scheme so that they are the wallpaper's
 * hues when dynamic colour is on and the brand violet and blue when it is off. Nothing in the
 * chrome names a brand colour directly; the status card's moods are state colours and stay.
 */
@Composable
fun chromeAccent(): Color = MaterialTheme.colorScheme.secondary

@Composable
fun chromeAccentAlt(): Color = MaterialTheme.colorScheme.primary

/** Height of the compact bar below the status bar. */
private val BarHeight = 56.dp

/** Everything the bar shows for one page; the lambdas are excluded from the transition key. */
private class Heading(
    val title: String,
    val level: Int,
    val navigationIcon: (@Composable () -> Unit)?,
    val actions: (@Composable RowScope.() -> Unit)?,
    val content: (@Composable () -> Unit)?,
    val anchor: HeaderAnchor?,
    val visible: Boolean,
) {
    val key: Any = title to level
    /** 0 = large heading resting in the page, 1 = condensed into the bar. */
    fun progress(): Float = anchor?.progress() ?: if (visible) 1f else 0f

    // Equal by key: the bar recomposes during a switch (the touch shield, for one) and a fresh
    // instance each time would re-target AnimatedContent and restart its enter animation.
    override fun equals(other: Any?): Boolean = other is Heading && other.key == key
    override fun hashCode(): Int = key.hashCode()
}

/**
 * The glass surface's own progress. It follows the heading of the page in front, but when that
 * page changes it crosses from the old value to the new one on the motion clock rather than
 * jumping — a page switch while the header is half-condensed stays continuous.
 */
private class GlassProgress {
    private var key: Any? = null
    // Snapshot state: the draw lambdas and the touch shield re-subscribe when the page hands
    // over a new anchor under the same title (Main recreated after a pushed page, for one).
    private var target by mutableStateOf<() -> Float>({ 0f })
    private var from = 0f
    /** 0 = still showing the value the previous page left, 1 = live. Reset synchronously on a switch. */
    var blend by mutableFloatStateOf(1f)
    /** True only while a page-switch blend is running; otherwise the surface is the heading's live value. */
    var animating by mutableStateOf(false)
        private set
    /** Bumped on every switch: keys the blend effect (a key flipping back within one frame still restarts it) and lets a late-ending blend leave a newer one alone. */
    var generation by mutableIntStateOf(0)
        private set

    /** Exactly what the heading reads, from the same anchor — except during the switch blend. */
    fun current(): Float {
        val live = target().let { if (it.isNaN()) 0f else it }
        return if (!animating || blend >= 1f) live else lerp(from, live, blend)
    }
    /** The glass itself lags the heading a little: it forms as the content arrives, not before. */
    fun surface(): Float = smoothstep(0.15f, 1f, current())

    /** Called on every composition of the bar; only a new page starts a blend, before the next draw. */
    fun update(newKey: Any, newTarget: () -> Float) {
        if (key != newKey) {
            from = Snapshot.withoutReadObservation { if (key == null) newTarget() else current() }
            if (key != null) { blend = 0f; animating = true; generation++ }
        }
        key = newKey
        target = newTarget
    }

    /** Ends the blend started for [gen]; a blend cancelled by a newer switch leaves the newer one alone. */
    fun finish(gen: Int) { if (gen == generation) animating = false }
}

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The top bar that condenses out of the page as it scrolls. The page's large heading is drawn
 * here, at full size where the page's header slot rests; as the page scrolls it shrinks and
 * glides into the bar's title slot, the glass forming underneath it, every frame a function of
 * the scroll offset. When the page underneath changes, the heading travels sideways on the same
 * axis, curve and clock as the page itself; [TopBarState.level] says which way.
 */
@Composable
fun GlassTopBar(
    bar: TopBarState,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val heading = Heading(bar.title, bar.level, bar.navigationIcon, bar.actions, bar.heading, bar.anchor, bar.visible)
    val glass = remember { GlassProgress() }
    glass.update(heading.key, heading::progress)
    LaunchedEffect(glass.generation) {
        if (!glass.animating) return@LaunchedEffect
        val gen = glass.generation
        try {
            animate(0f, 1f, animationSpec = Motion.standard()) { v, _ -> glass.blend = v }
        } finally {
            glass.finish(gen)
        }
    }
    // Only a formed bar shields the rows beneath; at rest the header area must still start a scroll.
    val shields by remember(glass) { derivedStateOf { glass.current() >= 0.5f } }
    val compactScale = MaterialTheme.typography.titleMedium.fontSize.value / MaterialTheme.typography.headlineMedium.fontSize.value
    // The veil wears the mood of the page in front (the status card's colour on Home), crossing
    // on the colour clock when it changes so the glass never flips.
    val accent = chromeAccent()
    val accentAlt = chromeAccentAlt()
    val mood = heading.anchor?.tint?.takeIf { it.isSpecified } ?: accent
    // Animated outside composition: the bar must not recompose per frame while the tint crosses.
    val tint = remember { Animatable(mood, (Color.VectorConverter)(mood.colorSpace)) }
    LaunchedEffect(mood) { tint.animateTo(mood, Motion.color) }
    Box(
        modifier
            .fillMaxWidth()
            .height(statusTop + BarHeight)
            .monkGlass(hazeState, consumeTaps = false, alpha = glass::surface)
            .drawWithCache {
                val hair = 1.dp.toPx()
                val sheenHalf = size.width * 0.22f
                val t = tint.value
                val veil = Brush.verticalGradient(
                    listOf(
                        t.copy(alpha = if (dark) 0.24f else 0.12f),
                        accentAlt.copy(alpha = if (dark) 0.06f else 0.03f),
                        Color.Transparent,
                    ),
                )
                val hairline = Brush.horizontalGradient(
                    listOf(Color.Transparent, t.copy(alpha = if (dark) 0.5f else 0.35f), accentAlt.copy(alpha = if (dark) 0.5f else 0.35f), Color.Transparent),
                )
                val sheen = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = if (dark) 0.55f else 0.9f),
                    1f to Color.Transparent,
                    startX = -sheenHalf,
                    endX = sheenHalf,
                )
                onDrawBehind {
                    val p = glass.current()
                    if (p <= 0f) return@onDrawBehind
                    // The wash that fades into the glass.
                    drawRect(veil, alpha = glass.surface())
                    // Past the midpoint a hairline of light forms along the edge and a gleam sweeps into place.
                    val e = smoothstep(0.6f, 1f, p)
                    if (e <= 0f) return@onDrawBehind
                    drawRect(hairline, topLeft = Offset(0f, size.height - hair), size = Size(size.width, hair), alpha = e)
                    val cx = lerp(-0.3f, 0.68f, e) * size.width
                    translate(left = cx) {
                        drawRect(sheen, topLeft = Offset(-sheenHalf, size.height - hair * 1.5f), size = Size(sheenHalf * 2, hair * 1.5f), alpha = e * 0.8f)
                    }
                }
            }
            .then(if (shields) Modifier.swallowTouches() else Modifier),
    ) {
        Box(Modifier.padding(top = statusTop).fillMaxWidth().height(BarHeight)) {
            // The transition is keyed by the heading's identity (title, level); what it draws is
            // always the latest description for that key, so lambdas and anchors never go stale.
            val latest = remember { HashMap<Any, Heading>() }
            latest[heading.key] = heading
            AnimatedContent(
                targetState = heading,
                contentKey = { it.key },
                transitionSpec = { Motion.sharedAxisX(forward = targetState.level >= initialState.level, travel = 0.12f) },
                label = "heading",
            ) { shown ->
                val h = latest[shown.key] ?: shown
                Box(Modifier.fillMaxWidth().height(BarHeight)) {
                    Box(
                        Modifier.headingMorph(
                            h,
                            statusTop = with(density) { statusTop.toPx() },
                            barHeight = with(density) { BarHeight.toPx() },
                            compactScale = compactScale,
                            sidePad = with(density) { 20.dp.toPx() },
                            compactPad = with(density) { 72.dp.toPx() },
                        ),
                    ) {
                        if (h.content != null) h.content.invoke() else PageTitle(h.title)
                    }
                    if (h.navigationIcon != null) {
                        Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp).graphicsLayer { alpha = h.progress() }) { h.navigationIcon.invoke() }
                    }
                    if (h.actions != null) {
                        Row(
                            Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).graphicsLayer { alpha = h.progress() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) { h.actions.invoke(this) }
                    }
                }
            }
        }
    }
}

/**
 * Places the heading once and moves it with a graphics layer: scale from full to the compact
 * title size, translation from the page's resting spot to the centre of the bar, both linear
 * in the anchor's progress. The transform origin is the top-start corner so the baseline
 * travels on one straight line and never jumps. The width is fixed to whatever fits in the
 * compact slot, so the text is never re-measured mid-scroll.
 */
private fun Modifier.headingMorph(
    h: Heading,
    statusTop: Float,
    barHeight: Float,
    compactScale: Float,
    sidePad: Float,
    compactPad: Float,
): Modifier = layout { measurable, constraints ->
    val barW = constraints.maxWidth
    val card = h.anchor?.cardTop != null && h.anchor.rowTop() != null
    val maxW = if (card) barW.toFloat() else minOf(barW - 2 * sidePad, (barW - 2 * compactPad) / compactScale)
    val placeable = measurable.measure(Constraints(maxWidth = maxW.roundToInt().coerceAtLeast(0)))
    val ltr = layoutDirection == LayoutDirection.Ltr
    val w = placeable.width.toFloat()
    val hgt = placeable.height.toFloat()
    layout(barW, placeable.height) {
        placeable.placeWithLayer(0, 0) {
            val p = h.progress()
            val a = h.anchor
            if (a != null && a.cardTop != null) {
                // Card mode: the status row places its own elements in root coordinates, so the
                // layer stays at identity; only the card-less fallback (the wordmark) condenses here.
                transformOrigin = TransformOrigin(0f, 0f)
                val row = a.rowTop()
                if (row == null) {
                    scaleX = compactScale; scaleY = compactScale
                    translationX = (barW - w * compactScale) / 2f
                    translationY = (barHeight - hgt * compactScale) / 2f
                    alpha = p
                } else {
                    scaleX = 1f; scaleY = 1f
                    translationX = 0f; translationY = 0f
                    alpha = 1f
                }
                return@placeWithLayer
            }
            val sc = lerp(1f, compactScale, p)
            val xL = when {
                a == null -> (barW - w) / 2f
                ltr -> a.startX
                else -> barW - a.startX - w
            }
            val yL = if (a == null) (barHeight - hgt) / 2f else a.startY - statusTop
            val xS = (barW - w * sc) / 2f
            val yS = (barHeight - hgt * sc) / 2f
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = sc
            scaleY = sc
            translationX = lerp(xL, xS, p)
            translationY = lerp(yL, yS, p)
        }
    }
}

/** A page's large title, as the bar draws it; pages use it inside [PageHeaderSlot] so both measure alike. */
@Composable
fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * Where a page's large header rests. The slot lays out the same content the bar draws, so the
 * list reserves exactly the space the heading needs, but draws nothing itself: the one visible
 * copy lives in the bar and follows the scroll from here. Without an anchor (a page shown on
 * its own) the content is simply drawn in place.
 */
@Composable
fun PageHeaderSlot(modifier: Modifier = Modifier, inPage: Boolean = false, content: @Composable () -> Unit) {
    val anchor = LocalHeaderAnchor.current
    if (anchor == null) {
        Box(modifier.padding(vertical = PageHeaderPad)) { content() }
        return
    }
    Box(
        modifier
            .padding(vertical = PageHeaderPad)
            // In-page: the title stays where it is and fades out before it would meet the status
            // bar, so the bar never shows two headings; the bar's own heading comes later.
            .then(
                if (inPage) Modifier.graphicsLayer { alpha = 1f - (anchor.scrollPx() / (anchor.slotHeight * 0.5f).coerceAtLeast(1f)).coerceIn(0f, 1f) }
                else Modifier.drawWithContent { },
            )
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val outer = placeable.height + with(this) { (PageHeaderPad * 2).roundToPx() }
                if (anchor.slotHeight != outer.toFloat()) anchor.slotHeight = outer.toFloat()
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
    ) { content() }
}

/** Vertical breathing room of the header slot; the shell adds it to the resting position. */
val PageHeaderPad = 4.dp

data class DockTab(val icon: ImageVector, val label: String)

/** Floating glass dock: icon + label per tab, one tinted pill that glides to the active tab. */
@Composable
fun GlassDock(
    tabs: List<DockTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(30.dp)
    val accent = chromeAccent()
    val accentAlt = chromeAccentAlt()
    val borderColor = if (dark) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.8f)
    val pillColor = MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.28f else 0.16f)
    val position by animateFloatAsState(selected.toFloat(), Motion.standard(), label = "dock")
    val gap = 2.dp
    BoxWithConstraints(
        modifier
            .widthIn(max = 480.dp)
            .padding(horizontal = 24.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp)
            .height(64.dp)
            .shadow(14.dp, shape, spotColor = accent.copy(alpha = if (dark) 0.35f else 0.18f), ambientColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .monkGlass(hazeState, consumeTaps = false)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = if (dark) 0.14f else 0.06f), accentAlt.copy(alpha = if (dark) 0.10f else 0.04f)),
                ),
            )
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val slot = (maxWidth - gap * (tabs.size - 1)) / tabs.size
        val stride = with(LocalDensity.current) { (slot + gap).toPx() }
        Box(
            Modifier
                .offset { IntOffset((stride * position).roundToInt(), 0) }
                .width(slot)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(pillColor),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                val fg by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    Motion.color, label = "fg",
                )
                // Above 1.3× font scale the label would not fit under the icon: the icon alone stays.
                val bigFont = LocalDensity.current.fontScale > 1.3f
                Column(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, tab.label, tint = fg, modifier = Modifier.size(22.dp))
                    if (!bigFont) {
                        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides fg) {
                            FitText(tab.label, style = MaterialTheme.typography.labelSmall, minSize = 8f, modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp))
                        }
                    }
                }
                if (i < tabs.lastIndex) Box(Modifier.width(gap))
            }
        }
    }
}

/** The dock, stood up on its side for wide screens (tablets, unfolded foldables). */
@Composable
fun GlassRail(
    tabs: List<DockTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = chromeAccent()
    val accentAlt = chromeAccentAlt()
    val pillColor = MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.28f else 0.16f)
    val position by animateFloatAsState(selected.toFloat(), Motion.standard(), label = "rail")
    val stride = with(LocalDensity.current) { 72.dp.toPx() }
    Box(
        modifier
            .fillMaxHeight()
            .width(96.dp)
            .monkGlass(hazeState)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = if (dark) 0.14f else 0.06f), accentAlt.copy(alpha = if (dark) 0.08f else 0.03f)),
                ),
            )
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp, bottom = 16.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .offset { IntOffset(0, (stride * position).roundToInt()) }
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(pillColor),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                val fg by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    Motion.color, label = "fg",
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, tab.label, tint = fg, modifier = Modifier.size(24.dp))
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides fg) {
                        FitText(tab.label, style = MaterialTheme.typography.labelSmall, minSize = 8f, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                    }
                }
            }
        }
    }
}

/**
 * The floating action pill: solid and confident — the theme's primary with its own ink, so it
 * is the wallpaper's hue under dynamic colour and the brand blue otherwise — carried on the
 * same soft accent shadow as the dock.
 */
@Composable
fun GlassActionPill(text: String, icon: ImageVector, onClick: () -> Unit, hazeState: HazeState, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(26.dp)
    val h = rememberHaptics()
    Row(
        modifier
            .height(AddAppsPillHeight)
            .shadow(14.dp, shape, spotColor = chromeAccent().copy(alpha = if (dark) 0.35f else 0.18f), ambientColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable { h.select(); onClick() }
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Height of the floating action pill; a list under one reserves this much room to scroll clear of it. */
val AddAppsPillHeight = 52.dp

/** Status-bar inset + top bar height: what content must leave free under an always-on GlassTopBar. */
@Composable
fun glassTopBarInset() = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp
