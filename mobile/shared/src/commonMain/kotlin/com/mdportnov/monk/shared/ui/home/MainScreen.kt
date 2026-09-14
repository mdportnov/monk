package com.mdportnov.monk.shared.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.platform.MonkPlatform
import com.mdportnov.monk.shared.ui.settings.SettingsScreen
import com.mdportnov.monk.shared.ui.stats.StatsScreen

private enum class Tab { Home, Stats, Settings }

@Composable
fun MainScreen(
    store: MonkStore,
    platform: MonkPlatform,
    onAddApps: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val s = strings
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.Home,
                    onClick = { tab = Tab.Home },
                    icon = { Icon(Icons.Outlined.Apps, null) },
                    label = { Text(s.tabHome) },
                )
                NavigationBarItem(
                    selected = tab == Tab.Stats,
                    onClick = { tab = Tab.Stats },
                    icon = { Icon(Icons.Outlined.Insights, null) },
                    label = { Text(s.tabStats) },
                )
                NavigationBarItem(
                    selected = tab == Tab.Settings,
                    onClick = { tab = Tab.Settings },
                    icon = { Icon(Icons.Outlined.Tune, null) },
                    label = { Text(s.tabSettings) },
                )
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            Tab.Home -> HomeScreen(store, platform, onAddApps, onOpenApp, m)
            Tab.Stats -> StatsScreen(store, m)
            Tab.Settings -> SettingsScreen(store, platform, m)
        }
    }
}
