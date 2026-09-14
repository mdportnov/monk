package com.mdportnov.monk.shared.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Things only the hosting Activity / view controller can do, provided at the root. */
class HostActions(
    val requestNotificationPermission: () -> Unit = {},
)

val LocalHostActions = staticCompositionLocalOf { HostActions() }

/** Opens a pushed page from anywhere below the root, so a tab screen need not thread a callback for every route. */
val LocalOpenRoute = staticCompositionLocalOf<(Route) -> Unit> { {} }
