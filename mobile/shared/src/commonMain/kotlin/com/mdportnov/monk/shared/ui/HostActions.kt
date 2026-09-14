package com.mdportnov.monk.shared.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Things only the hosting Activity / view controller can do, provided at the root. */
class HostActions(
    val requestNotificationPermission: () -> Unit = {},
)

val LocalHostActions = staticCompositionLocalOf { HostActions() }
