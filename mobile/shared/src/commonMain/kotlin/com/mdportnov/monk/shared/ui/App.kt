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
import com.mdportnov.monk.shared.MonkRuntime
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

@Composable
fun MonkApp(
    onBackHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
    /** Lets the host paint window background and system bars to match; called on every change. */
    onThemeResolved: (dark: Boolean) -> Unit = {},
) {
    val nav = remember { Navigator() }
    onBackHandler(nav.canGoBack) { nav.pop() }
    val config by MonkRuntime.store.config.collectAsStateWithLifecycle()
    val dark = resolveDarkTheme(config.theme)
    LaunchedEffect(dark) { onThemeResolved(dark) }
    MonkTheme(darkTheme = dark, dynamicColor = config.dynamicColor) {
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
                        store = MonkRuntime.store,
                        platform = MonkRuntime.platform,
                        onAddApps = { nav.push(Route.AddApps) },
                        onOpenApp = { nav.push(Route.AppDetail(it)) },
                    )
                    Route.AddApps -> AddAppsScreen(
                        store = MonkRuntime.store,
                        platform = MonkRuntime.platform,
                        onClose = { nav.pop() },
                    )
                    is Route.AppDetail -> AppDetailScreen(
                        store = MonkRuntime.store,
                        packageName = route.packageName,
                        onClose = { nav.pop() },
                    )
                }
            }
        }
    }
}
