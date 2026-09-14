package com.mdportnov.monk.shared.ui.home

import androidx.compose.animation.AnimatedContent
import com.mdportnov.monk.shared.ui.Motion
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import com.mdportnov.monk.shared.ui.components.GlassRail
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.platform.MonkPlatform
import com.mdportnov.monk.shared.ui.components.DockTab
import com.mdportnov.monk.shared.ui.components.GlassDock
import com.mdportnov.monk.shared.ui.TopBarState
import androidx.compose.runtime.SideEffect
import dev.chrisbanes.haze.HazeState
import com.mdportnov.monk.shared.ui.settings.SettingsScreen
import com.mdportnov.monk.shared.ui.stats.StatsScreen
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import com.mdportnov.monk.shared.ui.HeaderAnchor
import com.mdportnov.monk.shared.ui.LocalHeaderAnchor
import com.mdportnov.monk.shared.ui.components.PageHeaderPad
import com.mdportnov.monk.shared.ui.components.PageTitle

private enum class Tab { Home, Stats, Settings }

/** Scroll position of the current tab, given to every tab screen by the shell. */
class TabScroll(val list: LazyListState, val column: ScrollState)

@Composable
fun MainScreen(
    store: MonkStore,
    platform: MonkPlatform,
    onAddApps: () -> Unit,
    onOpenApp: (String) -> Unit,
    topBar: TopBarState,
    hazeState: HazeState,
) {
    val s = strings
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    // Saveable: Main leaves composition under a pushed page and must come back where it was.
    val scrolls = Tab.entries.associateWith { t -> key(t) { TabScroll(rememberLazyListState(), rememberScrollState()) } }
    val scroll = scrolls.getValue(tab)
    // One anchor per tab: how far that tab has scrolled, read by the bar in draw, never here.
    val anchors = remember {
        Tab.entries.associateWith { t ->
            val sc = scrolls.getValue(t)
            if (t == Tab.Home) HeaderAnchor {
                if (sc.list.firstVisibleItemIndex > 0) Float.MAX_VALUE else sc.list.firstVisibleItemScrollOffset.toFloat()
            } else HeaderAnchor { sc.column.value.toFloat() }
        }
    }
    val anchor = anchors.getValue(tab)
    val title = when (tab) { Tab.Home -> "monk_"; Tab.Stats -> s.tabStats; Tab.Settings -> s.tabSettings }
    // One heading lambda per tab, created once: a lambda written at this call site would be
    // updated in place by the compiler on every recomposition, and the bar's outgoing slot
    // (still showing the previous tab) would redraw with the new tab's title mid-transition.
    val headings = remember {
        Tab.entries.associateWith { t ->
            val composable: @Composable () -> Unit = {
                when (t) {
                    Tab.Home -> CompactStatus(store, anchors.getValue(t))
                    Tab.Stats -> PageTitle(strings.tabStats)
                    Tab.Settings -> PageTitle(strings.tabSettings)
                }
            }
            composable
        }
    }
    val heading = headings.getValue(tab)
    SideEffect { topBar.set(title = title, visible = false, level = tab.ordinal, heading = heading, anchor = anchor) }
    // A release inside the morph settles it: the header is either fully open or fully in the bar.
    val snap = remember(tab) {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val delta = anchor.settleDelta() ?: return Velocity.Zero
                when {
                    tab != Tab.Home -> scroll.column.animateScrollBy(delta, Motion.standard())
                    delta == Float.NEGATIVE_INFINITY -> scroll.list.animateScrollToItem(0)
                    else -> scroll.list.animateScrollBy(delta, Motion.standard())
                }
                return Velocity.Zero
            }
        }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabs = listOf(
        DockTab(Icons.Outlined.Apps, s.tabHome),
        DockTab(Icons.Outlined.Insights, s.tabStats),
        DockTab(Icons.Outlined.Tune, s.tabSettings),
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Tablets and unfolded foldables: a side rail instead of the dock, content capped so
        // cards stop stretching into banners.
        val wide = maxWidth >= 840.dp
        val contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = top + 12.dp,
            bottom = if (wide) bottom + 24.dp else bottom + 12.dp + 64.dp + 24.dp,
        )
        // Where every tab's header rests: the content column's start, under the status bar and the
        // list's top padding. The bar draws the heading there and needs the same numbers.
        val railWidth = if (wide) 96.dp else 0.dp
        val column = (maxWidth - railWidth).coerceAtMost(720.dp)
        val startX = railWidth + (maxWidth - railWidth - column) / 2 + 16.dp + 4.dp
        val startY = top + 12.dp + PageHeaderPad
        val nominalTitleHeight = with(LocalDensity.current) { (MaterialTheme.typography.headlineMedium.fontSize.value * 1.25f).sp.toDp() }
        with(LocalDensity.current) {
            val x = startX.toPx()
            val y = startY.toPx()
            val tail = 12.dp.toPx()
            // Home: the status card's top edge, read off the list each frame, drives the morph.
            val padTop = (top + 12.dp).toPx()
            val home = anchors.getValue(Tab.Home)
            val list = scrolls.getValue(Tab.Home).list
            val cardTop: () -> Float? = {
                val info = list.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.key == "status" }
                when {
                    item != null -> padTop + item.offset
                    (info.visibleItemsInfo.firstOrNull()?.index ?: 0) > 0 -> Float.NEGATIVE_INFINITY
                    else -> null
                }
            }
            val cardX = (startX - 4.dp).toPx()
            val cardWidth = (column - 32.dp).toPx()
            val cardPad = 20.dp.toPx()
            val glyphHalf = 32.dp.toPx()
            val barBottom = (top + 56.dp).toPx()
            val lead = 24.dp.toPx()
            // A title tab restored while scrolled never lays its slot out; give it the slot's
            // nominal height so its progress is measured, not guessed.
            val nominalSlot = (nominalTitleHeight + PageHeaderPad * 2).toPx()
            SideEffect {
                anchors.values.forEach { it.startX = x; it.startY = y; it.tail = tail }
                anchors.forEach { (t, a) -> if (t != Tab.Home && a.slotHeight == 0f) a.slotHeight = nominalSlot }
                home.cardTop = cardTop
                home.cardX = cardX; home.cardWidth = cardWidth; home.cardPad = cardPad; home.glyphHalf = glyphHalf
                home.rowHeight = glyphHalf * 2; home.barBottom = barBottom; home.lead = lead
            }
        }
        Box(Modifier.fillMaxSize().padding(start = railWidth).nestedScroll(snap).hazeSource(hazeState)) {
            Box(Modifier.widthIn(max = 720.dp).fillMaxSize().align(Alignment.TopCenter)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { Motion.sharedAxisX(forward = targetState.ordinal > initialState.ordinal, travel = 0.12f) },
                    label = "tab",
                ) { t ->
                    val sc = scrolls.getValue(t)
                    CompositionLocalProvider(LocalHeaderAnchor provides anchors.getValue(t)) {
                        when (t) {
                            Tab.Home -> HomeScreen(store, platform, onAddApps, onOpenApp, { tab = Tab.Stats }, sc.list, contentPadding, hazeState)
                            Tab.Stats -> StatsScreen(store, platform, sc.column, contentPadding)
                            Tab.Settings -> SettingsScreen(store, platform, sc.column, contentPadding)
                        }
                    }
                }
            }
        }
        if (wide) {
            GlassRail(tabs, tab.ordinal, { tab = Tab.entries[it] }, hazeState, Modifier.align(Alignment.CenterStart))
        } else {
            GlassDock(tabs, tab.ordinal, { tab = Tab.entries[it] }, hazeState, Modifier.align(Alignment.BottomCenter))
        }
    }
}
