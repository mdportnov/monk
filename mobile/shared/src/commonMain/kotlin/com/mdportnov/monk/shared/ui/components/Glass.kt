package com.mdportnov.monk.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mdportnov.monk.shared.ui.theme.MonkColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Liquid glass: blur of whatever scrolls underneath, tinted with the surface colour. In the
 * dark theme the tint leans violet-black — the same ink as the pause screen — so the chrome
 * reads as part of the brand rather than a grey slab. Taps on empty chrome are swallowed so
 * they never reach the row that happens to sit beneath.
 */
@Composable
fun Modifier.monkGlass(hazeState: HazeState): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = if (dark) Color(0xFF14112A) else MaterialTheme.colorScheme.surfaceContainer
    return this
        .hazeEffect(hazeState, if (dark) HazeMaterials.ultraThin(tint) else HazeMaterials.thin(tint))
        .pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        }
}

/**
 * The top bar that condenses out of the page as it scrolls: hidden at rest (the page's own
 * headline is the title then), sliding in over the status bar once the headline has left.
 */
@Composable
fun GlassTopBar(title: String, visible: Boolean, hazeState: HazeState, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { -it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { -it / 2 },
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .monkGlass(hazeState)
                // The "magic": a violet wash that fades into the glass, plus a hairline of light.
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MonkColors.Violet.copy(alpha = if (dark) 0.22f else 0.10f),
                            MonkColors.Blue.copy(alpha = if (dark) 0.06f else 0.03f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        ) {
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, MonkColors.Violet.copy(alpha = if (dark) 0.5f else 0.35f), MonkColors.Blue.copy(alpha = if (dark) 0.5f else 0.35f), Color.Transparent),
                        ),
                    ),
            )
        }
    }
}

data class DockTab(val icon: ImageVector, val label: String)

/** Floating glass dock: icon + label per tab, the active one on a tinted pill. */
@Composable
fun GlassDock(
    tabs: List<DockTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(30.dp)
    val borderColor = if (dark) MonkColors.Violet.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.8f)
    Row(
        modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp)
            .height(64.dp)
            .shadow(14.dp, shape, spotColor = MonkColors.Violet.copy(alpha = if (dark) 0.35f else 0.18f), ambientColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .monkGlass(hazeState)
            .background(
                Brush.linearGradient(
                    listOf(MonkColors.Violet.copy(alpha = if (dark) 0.14f else 0.06f), MonkColors.Blue.copy(alpha = if (dark) 0.10f else 0.04f)),
                ),
            )
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, tab ->
            val active = i == selected
            val pill by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.28f else 0.16f) else Color.Transparent,
                tween(250), label = "pill",
            )
            val fg by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(250), label = "fg",
            )
            Column(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(pill)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icon, tab.label, tint = fg, modifier = Modifier.size(22.dp))
                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = fg, modifier = Modifier.padding(top = 2.dp))
            }
            if (i < tabs.lastIndex) Box(Modifier.width(2.dp))
        }
    }
}
