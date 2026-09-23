package com.mdportnov.monk.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import com.mdportnov.monk.shared.ui.components.GlassTopBar
import com.mdportnov.monk.shared.ui.components.LocalHapticsEnabled
import com.mdportnov.monk.shared.ui.components.LocalOverlayHost
import com.mdportnov.monk.shared.ui.components.OverlayHost
import dev.chrisbanes.haze.rememberHazeState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.MonkGraph
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.mdportnov.monk.shared.model.ThemeMode
import com.mdportnov.monk.shared.ui.apps.AddAppsScreen
import com.mdportnov.monk.shared.ui.apps.AppDetailScreen
import com.mdportnov.monk.shared.ui.routines.RoutineDetailScreen
import com.mdportnov.monk.shared.ui.routines.RoutinesScreen
import com.mdportnov.monk.shared.ui.stats.ScreenTimeAppScreen
import com.mdportnov.monk.shared.ui.stats.ScreenTimeScreen
import com.mdportnov.monk.shared.ui.home.MainScreen
import com.mdportnov.monk.shared.ui.theme.MonkTheme

sealed interface Route {
    data object Main : Route
    /** The picker. With a [routineId] it adds what it picks to that routine as well as the list. */
    data class AddApps(val routineId: String = "") : Route
    data class AppDetail(val packageName: String) : Route
    data object ScreenTime : Route
    data class ScreenTimeApp(val packageName: String) : Route
    data object Routines : Route
    data class RoutineDetail(val routineId: String) : Route

    /** Saveable-state keys must be primitives; routes carry their identity as a string. */
    val stateKey: String
        get() = when (this) {
            is AddApps -> if (routineId.isEmpty()) "AddApps" else "AddApps:$routineId"
            is AppDetail -> "detail:$packageName"
            is ScreenTimeApp -> "screen:$packageName"
            is RoutineDetail -> "routine:$routineId"
            else -> this::class.simpleName.orEmpty()
        }

    companion object {
        /** The inverse of [stateKey]; null for a key this build does not know. */
        fun fromStateKey(key: String): Route? = when {
            key.startsWith("AddApps:") -> AddApps(key.removePrefix("AddApps:"))
            key.startsWith("detail:") -> AppDetail(key.removePrefix("detail:"))
            key.startsWith("screen:") -> ScreenTimeApp(key.removePrefix("screen:"))
            key.startsWith("routine:") -> RoutineDetail(key.removePrefix("routine:"))
            key == "Main" -> Main
            key == "AddApps" -> AddApps()
            key == "ScreenTime" -> ScreenTime
            key == "Routines" -> Routines
            else -> null
        }
    }
}

class Navigator(initial: List<Route> = listOf(Route.Main)) {
    var stack by mutableStateOf<List<Route>>(initial)
        private set
    var forward by mutableStateOf(true)
        private set
    val current get() = stack.last()
    val canGoBack get() = stack.size > 1
    fun push(route: Route) { forward = true; stack = stack + route }
    fun pop() { if (canGoBack) { forward = false; popped = stack.last(); stack = stack.dropLast(1) } }
    /**
     * A page closing itself. No-op once it is no longer on top: a page still drawn during its
     * exit animation (a deleted routine noticing it is gone) must not pop the page under it too.
     */
    fun pop(from: Route) { if (current.stateKey == from.stateKey) pop() }
    /** The route most recently popped; App drops its saved state once the exit animation is over. */
    var popped by mutableStateOf<Route?>(null)

    companion object {
        /** Survives process death: a pushed page comes back instead of a reset to Home. */
        val Saver = listSaver<Navigator, String>(
            save = { it.stack.map { r -> r.stateKey } },
            restore = { keys -> Navigator(keys.mapNotNull(Route::fromStateKey).ifEmpty { listOf(Route.Main) }) },
        )
    }
}

/** Resolves the user's theme choice against the system setting. */
@Composable
fun resolveDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private data class ThemeChoice(val mode: ThemeMode, val dynamic: Boolean, val language: String)

@Composable
fun MonkApp(
    graph: MonkGraph,
    onBackHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
    /** Lets the host paint window background and system bars to match; called on every change. */
    onThemeResolved: (dark: Boolean) -> Unit = {},
) {
    val store = graph.store
    val nav = rememberSaveable(saver = Navigator.Saver) { Navigator() }
    val overlay = remember { OverlayHost() }
    onBackHandler(overlay.onBack != null || nav.canGoBack) { overlay.onBack?.invoke() ?: nav.pop() }
    // Only the theme choice reaches the root: a slider commit elsewhere must not recompose it.
    val themeFlow = remember(store) { store.config.map { ThemeChoice(it.theme, it.dynamicColor, it.language) }.distinctUntilChanged() }
    val c0 = store.config.value
    val choice by themeFlow.collectAsStateWithLifecycle(ThemeChoice(c0.theme, c0.dynamicColor, c0.language))
    val dark = resolveDarkTheme(choice.mode)
    LaunchedEffect(dark) { onThemeResolved(dark) }
    LaunchedEffect(choice.language) { graph.platform.applyAppLanguage(choice.language) }
    val hapticsFlow = remember(store) { store.config.map { it.haptics }.distinctUntilChanged() }
    val haptics by hapticsFlow.collectAsStateWithLifecycle(store.config.value.haptics)
    val hazeState = rememberHazeState()
    // One bar description per page. The bar shows the target page's as soon as that page has
    // filled it in (its first SideEffect, one frame after the route changes); until then it keeps
    // the previous one, so the title never flashes empty and never goes to a page on its way out.
    val bars = remember { HashMap<Route, TopBarState>() }
    val target = bars.getOrPut(nav.current) { TopBarState() }
    val lastBound = remember { arrayOfNulls<TopBarState>(1) }
    val topBar = if (target.bound) target.also { lastBound[0] = it } else lastBound[0] ?: target
    LaunchedEffect(nav.stack) { bars.keys.retainAll(nav.stack.toSet()) }
    MonkTheme(darkTheme = dark, dynamicColor = choice.dynamic, language = choice.language) {
        CompositionLocalProvider(LocalHapticsEnabled provides haptics, LocalOverlayHost provides overlay, LocalOpenRoute provides remember(nav) { { r: Route -> nav.push(r) } }) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
            val stateHolder = rememberSaveableStateHolder()
            AnimatedContent(
                targetState = nav.current,
                transitionSpec = {
                    Motion.sharedAxisX(forward = nav.forward).apply { targetContentZIndex = nav.stack.size.toFloat() }
                },
                label = "route",
            ) { route ->
                val bar = bars.getOrPut(route) { TopBarState() }
                // Main leaves composition while a pushed page is up; without a holder its tab,
                // scroll and range state would reset and "back" would land on a blank Home.
                stateHolder.SaveableStateProvider(route.stateKey) {
                when (route) {
                    Route.Main -> MainScreen(
                        store = store,
                        platform = graph.platform,
                        onAddApps = { nav.push(Route.AddApps()) },
                        onOpenApp = { nav.push(Route.AppDetail(it)) },
                        topBar = bar,
                        hazeState = hazeState,
                    )
                    is Route.AddApps -> AddAppsScreen(
                        store = store,
                        platform = graph.platform,
                        routineId = route.routineId,
                        onClose = { nav.pop(route) },
                        topBar = bar,
                        hazeState = hazeState,
                    )
                    is Route.AppDetail -> AppDetailScreen(
                        store = store,
                        packageName = route.packageName,
                        onClose = { nav.pop(route) },
                        topBar = bar,
                        hazeState = hazeState,
                    )
                    Route.ScreenTime -> ScreenTimeScreen(
                        store = store,
                        platform = graph.platform,
                        onClose = { nav.pop(route) },
                        onOpenApp = { nav.push(Route.ScreenTimeApp(it)) },
                        topBar = bar,
                        hazeState = hazeState,
                    )
                    Route.Routines -> RoutinesScreen(
                        store = store,
                        onClose = { nav.pop(route) },
                        onOpenRoutine = { nav.push(Route.RoutineDetail(it)) },
                        topBar = bar,
                        hazeState = hazeState,
                    )
                    is Route.RoutineDetail -> RoutineDetailScreen(
                        store = store,
                        routineId = route.routineId,
                        onClose = { nav.pop(route) },
                        topBar = bar,
                        hazeState = hazeState,
                    )
                    is Route.ScreenTimeApp -> ScreenTimeAppScreen(
                        store = store,
                        platform = graph.platform,
                        packageName = route.packageName,
                        onClose = { nav.pop(route) },
                        topBar = bar,
                        hazeState = hazeState,
                    )
                }
                }
            }
            LaunchedEffect(nav.popped) {
                val gone = nav.popped ?: return@LaunchedEffect
                kotlinx.coroutines.delay(Motion.Long.toLong())
                if (gone !in nav.stack) { stateHolder.removeState(gone.stateKey); bars.remove(gone) }
            }
            GlassTopBar(bar = topBar, hazeState = hazeState, modifier = Modifier.align(Alignment.TopCenter))
            overlay.content?.invoke()
            }
        }
        }
    }
}
