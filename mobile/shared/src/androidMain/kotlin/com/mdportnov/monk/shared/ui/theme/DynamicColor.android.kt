package com.mdportnov.monk.shared.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < 31) return null
    val context = LocalContext.current
    return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

actual fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= 31
