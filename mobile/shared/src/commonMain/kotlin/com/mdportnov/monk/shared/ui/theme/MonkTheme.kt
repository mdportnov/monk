package com.mdportnov.monk.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mdportnov.monk.shared.i18n.LocalStrings
import com.mdportnov.monk.shared.i18n.stringsForSystem

object MonkColors {
    val Ink = Color(0xFF0B0D12)
    val Blue = Color(0xFF7AA2F7)
    val Violet = Color(0xFFBB9AF7)
    val Fog = Color(0xFFE8ECF1)
    val Rose = Color(0xFFF7768E)
    val Mint = Color(0xFF9ECE6A)
}

private val DarkScheme = darkColorScheme(
    primary = MonkColors.Blue,
    onPrimary = MonkColors.Ink,
    primaryContainer = Color(0xFF243458),
    onPrimaryContainer = Color(0xFFD6E1FF),
    secondary = MonkColors.Violet,
    onSecondary = MonkColors.Ink,
    secondaryContainer = Color(0xFF3A2E5C),
    onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = MonkColors.Mint,
    background = MonkColors.Ink,
    onBackground = MonkColors.Fog,
    surface = Color(0xFF11151D),
    onSurface = MonkColors.Fog,
    surfaceVariant = Color(0xFF1B2130),
    onSurfaceVariant = Color(0xFFA9B1C3),
    surfaceContainer = Color(0xFF161B26),
    surfaceContainerHigh = Color(0xFF1B2130),
    surfaceContainerHighest = Color(0xFF222A3B),
    surfaceContainerLow = Color(0xFF0F131A),
    outline = Color(0xFF3A4358),
    outlineVariant = Color(0xFF262D3D),
    error = MonkColors.Rose,
    onError = MonkColors.Ink,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3D63C9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF0E2358),
    secondary = Color(0xFF7455B8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBDDFF),
    onSecondaryContainer = Color(0xFF2A1450),
    tertiary = Color(0xFF3E7A1F),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF12151C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12151C),
    surfaceVariant = Color(0xFFE6E9F2),
    onSurfaceVariant = Color(0xFF4C5468),
    surfaceContainer = Color(0xFFF0F2F8),
    surfaceContainerHigh = Color(0xFFE9ECF4),
    surfaceContainerHighest = Color(0xFFE2E6F0),
    surfaceContainerLow = Color(0xFFF9FAFD),
    outline = Color(0xFFB9C0D0),
    outlineVariant = Color(0xFFDDE1EA),
    error = Color(0xFFC62B4A),
    onError = Color.White,
)

private val MonkShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MonkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStrings provides stringsForSystem()) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            shapes = MonkShapes,
            content = content,
        )
    }
}
