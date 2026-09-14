package com.mdportnov.monk.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdportnov.monk.shared.i18n.LocalStrings
import com.mdportnov.monk.shared.i18n.stringsFor
import com.mdportnov.monk.shared.resources.Montserrat_Bold
import com.mdportnov.monk.shared.resources.Montserrat_Medium
import com.mdportnov.monk.shared.resources.Montserrat_Regular
import com.mdportnov.monk.shared.resources.Montserrat_SemiBold
import com.mdportnov.monk.shared.resources.Res
import org.jetbrains.compose.resources.Font

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
    surface = Color(0xFF0F131A),
    onSurface = MonkColors.Fog,
    surfaceVariant = Color(0xFF1B2130),
    onSurfaceVariant = Color(0xFFA9B1C3),
    surfaceContainer = Color(0xFF161B26),
    surfaceContainerHigh = Color(0xFF1C2231),
    surfaceContainerHighest = Color(0xFF242C3D),
    surfaceContainerLow = Color(0xFF11151D),
    outline = Color(0xFF3A4358),
    outlineVariant = Color(0xFF232A3A),
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
    background = Color(0xFFF1F3F9),
    onBackground = Color(0xFF12151C),
    surface = Color(0xFFF1F3F9),
    onSurface = Color(0xFF12151C),
    surfaceVariant = Color(0xFFE6E9F2),
    onSurfaceVariant = Color(0xFF5A6177),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF7F8FC),
    surfaceContainerHighest = Color(0xFFE9ECF4),
    surfaceContainerLow = Color(0xFFF7F8FC),
    outline = Color(0xFFB9C0D0),
    outlineVariant = Color(0xFFE3E6EE),
    error = Color(0xFFC62B4A),
    onError = Color.White,
)

private val MonkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
private fun montserrat(): FontFamily = FontFamily(
    Font(Res.font.Montserrat_Regular, FontWeight.Normal),
    Font(Res.font.Montserrat_Medium, FontWeight.Medium),
    Font(Res.font.Montserrat_SemiBold, FontWeight.SemiBold),
    Font(Res.font.Montserrat_Bold, FontWeight.Bold),
)

private fun monkTypography(family: FontFamily) = Typography(
    displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.25).sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.9.sp),
    labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.8.sp),
)

@Composable
fun MonkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    language: String = "system",
    content: @Composable () -> Unit,
) {
    val strings = remember(language) { stringsFor(language) }
    val family = montserrat()
    val typography = remember(family) { monkTypography(family) }
    val dynamic = if (dynamicColor) platformDynamicColorScheme(darkTheme) else null
    // Material You keeps its own surfaces but our accent semantics stay: tertiary must read as
    // "walked away" (green-ish) and error as "blocked", so those two are pinned.
    val scheme = when {
        dynamic == null -> if (darkTheme) DarkScheme else LightScheme
        darkTheme -> dynamic.copy(tertiary = MonkColors.Mint, error = MonkColors.Rose)
        else -> dynamic.copy(tertiary = LightScheme.tertiary, error = LightScheme.error)
    }
    CompositionLocalProvider(LocalStrings provides strings) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = MonkShapes,
            typography = typography,
            content = content,
        )
    }
}
