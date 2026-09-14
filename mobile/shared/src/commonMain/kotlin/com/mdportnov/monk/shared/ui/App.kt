package com.mdportnov.monk.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.MonkGraph
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.mdportnov.monk.shared.model.ThemeMode
import com.mdportnov.monk.shared.ui.apps.AddAppsScreen
import com.mdportnov.monk.shared.ui.apps.AppDetailScreen
import com.mdportnov.monk.shared.ui.home.MainScreen
import com.mdportnov.monk.shared.ui.theme.MonkTheme

sealed interface Route {
    data object Main : Route
    data object AddApps : Route
    data class AppDetail(val packageName: String) : Route
}

class Navigator {
    var stack by mutableStateOf<List<Route>>(listOf(Route.Main))
        private set
    var forward by mutableStateOf(true)
        private set
    val current get() = stack.last()
    val canGoBack get() = stack.size > 1
    fun push(route: Route) { forward = true; stack = stack + route }
    fun pop() { if (canGoBack) { forward = false; stack = stack.dropLast(1) } }
}

/** Resolves the user's theme choice against the system setting. */
@Composable
fun resolveDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private data class ThemeChoice(val mode: ThemeMode, val dynamic: Boolean)

@Composable
fun MonkApp(
    graph: MonkGraph,
    onBackHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
    /** Lets the host paint window background and system bars to match; called on every change. */
    onThemeResolved: (dark: Boolean) -> Unit = {},
) {
    val store = graph.store
    val nav = remember { Navigator() }
    onBackHandler(nav.canGoBack) { nav.pop() }
    // Only the theme choice reaches the root: a slider commit elsewhere must not recompose it.
    val themeFlow = remember(store) { store.config.map { ThemeChoice(it.theme, it.dynamicColor) }.distinctUntilChanged() }
    val choice by themeFlow.collectAsStateWithLifecycle(ThemeChoice(store.config.value.theme, store.config.value.dynamicColor))
    val dark = resolveDarkTheme(choice.mode)
    LaunchedEffect(dark) { onThemeResolved(dark) }
    MonkTheme(darkTheme = dark, dynamicColor = choice.dynamic) {
        Surface(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = nav.current,
                transitionSpec = {
                    val dir = if (nav.forward) 1 else -1
                    (slideInHorizontally { dir * it / 6 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -dir * it / 6 } + fadeOut())
                },
                label = "route",
            ) { route ->
                when (route) {
                    Route.Main -> MainScreen(
                        store = store,
                        platform = graph.platform,
                        onAddApps = { nav.push(Route.AddApps) },
                        onOpenApp = { nav.push(Route.AppDetail(it)) },
                    )
                    Route.AddApps -> AddAppsScreen(
                        store = store,
                        platform = graph.platform,
                        onClose = { nav.pop() },
                    )
                    is Route.AppDetail -> AppDetailScreen(
                        store = store,
                        packageName = route.packageName,
                        onClose = { nav.pop() },
                    )
                }
            }
        }
    }
}
