package com.mdportnov.monk.shared.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Material You: the wallpaper-derived scheme, or null where the platform has none. */
@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

expect fun supportsDynamicColor(): Boolean
