package com.mdportnov.monk.shared.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.platform.MonkPlatform
import com.mdportnov.monk.shared.ui.components.DockTab
import com.mdportnov.monk.shared.ui.components.GlassDock
import com.mdportnov.monk.shared.ui.components.GlassTopBar
import com.mdportnov.monk.shared.ui.settings.SettingsScreen
import com.mdportnov.monk.shared.ui.stats.StatsScreen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private enum class Tab { Home, Stats, Settings }

/** Scroll position of the current tab, given to every tab screen by the shell. */
class TabScroll(val list: LazyListState, val column: ScrollState)

@Composable
fun MainScreen(
    store: MonkStore,
    platform: MonkPlatform,
    onAddApps: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val s = strings
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val hazeState = rememberHazeState()
    val scrolls = remember { Tab.entries.associateWith { TabScroll(LazyListState(), ScrollState(0)) } }
    val scroll = scrolls.getValue(tab)
    // The compact bar appears once the page's own headline (first ~72 dp) has scrolled away.
    val condensed by remember(tab) {
        derivedStateOf {
            if (tab == Tab.Home) scroll.list.firstVisibleItemIndex > 0 || scroll.list.firstVisibleItemScrollOffset > 140
            else scroll.column.value > 140
        }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Content runs under both glass pieces; these insets keep the first and last rows reachable.
    val contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 12.dp + 64.dp + 24.dp)

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            when (tab) {
                Tab.Home -> HomeScreen(store, platform, onAddApps, onOpenApp, scroll.list, contentPadding)
                Tab.Stats -> StatsScreen(store, scroll.column, contentPadding)
                Tab.Settings -> SettingsScreen(store, platform, scroll.column, contentPadding)
            }
        }
        GlassTopBar(
            title = when (tab) { Tab.Home -> "monk_"; Tab.Stats -> s.tabStats; Tab.Settings -> s.tabSettings },
            visible = condensed,
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        GlassDock(
            tabs = listOf(
                DockTab(Icons.Outlined.Apps, s.tabHome),
                DockTab(Icons.Outlined.Insights, s.tabStats),
                DockTab(Icons.Outlined.Tune, s.tabSettings),
            ),
            selected = tab.ordinal,
            onSelect = { tab = Tab.entries[it] },
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
