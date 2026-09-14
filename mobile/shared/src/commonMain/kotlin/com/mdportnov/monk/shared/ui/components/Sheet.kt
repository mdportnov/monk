package com.mdportnov.monk.shared.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.mdportnov.monk.shared.ui.Motion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A slot at the root of the window where sheets are drawn, above every page and the chrome.
 * The sheet lives in the app's own window, so it shares the frame clock with the page under
 * it and follows the finger the way the rest of the app does. `onBack` is what the host's
 * back button should do while something is up.
 */
class OverlayHost {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    var onBack by mutableStateOf<(() -> Unit)?>(null)
}

val LocalOverlayHost = compositionLocalOf<OverlayHost?> { null }

/**
 * Bottom sheet with a handle: slides up on appearance, tracks a drag on the handle or on the
 * content once it is scrolled to the top, and always leaves by animating out — whether
 * dismissed by drag, by the back button, by the scrim or by a button inside that calls `hide`.
 * `onDismiss` fires only after the sheet has left the screen.
 */
@Composable
fun MonkSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(hide: () -> Unit) -> Unit) {
    val host = LocalOverlayHost.current
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestContent by rememberUpdatedState(content)
    if (host == null) {
        SheetBody({ latestDismiss() }, { hide -> latestContent(hide) }, null)
        return
    }
    DisposableEffect(host) {
        val controls = SheetControls()
        host.content = { SheetBody({ latestDismiss() }, { hide -> latestContent(hide) }, controls) }
        host.onBack = { controls.hide?.invoke() }
        onDispose {
            host.content = null
            host.onBack = null
        }
    }
}

private class SheetControls { var hide: (() -> Unit)? = null }

@Composable
private fun SheetBody(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(hide: () -> Unit) -> Unit,
    controls: SheetControls?,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Vertical distance from the resting place; starts off-screen until the sheet is measured.
    val offset = remember { Animatable(100_000f) }
    var height by remember { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
    val velocityThreshold = with(density) { 125.dp.toPx() }
    val positionalThreshold = with(density) { 96.dp.toPx() }

    fun drag(delta: Float) {
        if (leaving || height == 0) return
        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, height.toFloat())) }
    }
    fun hide() {
        if (leaving) return
        leaving = true
        scope.launch {
            offset.animateTo(height.toFloat(), Motion.exit(Motion.Medium))
            onDismiss()
        }
    }
    fun settle(velocity: Float) {
        if (leaving || height == 0) return
        val away = velocity > velocityThreshold || (velocity > -velocityThreshold && offset.value > positionalThreshold)
        if (away) hide() else scope.launch { offset.animateTo(0f, Motion.enter(Motion.Medium)) }
    }
    controls?.hide = ::hide

    // Enter once the first measurement is in: start just below the screen, then rise.
    LaunchedEffect(Unit) {
        val h = snapshotFlow { height }.first { it > 0 }
        offset.snapTo(h.toFloat())
        offset.animateTo(0f, Motion.enter(Motion.Long))
    }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy < 0 && offset.value > 0 && source == NestedScrollSource.UserInput) {
                    val consumed = dy.coerceAtLeast(-offset.value)
                    drag(consumed)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    drag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y < 0 && offset.value > 0) { settle(available.y); return available }
                return Velocity.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offset.value > 0 || available.y > velocityThreshold) { settle(available.y); return available }
                return Velocity.Zero
            }
        }
    }
    val scrim = MaterialTheme.colorScheme.scrim
    val handle = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val progress = if (height == 0) 0f else (1f - offset.value / height).coerceIn(0f, 1f)
                    drawRect(scrim.copy(alpha = 0.4f * progress))
                }
                .pointerInput(Unit) { detectTapGestures { hide() } },
        )
        Surface(
            Modifier
                .align(Alignment.BottomCenter)
                .statusBarsPadding()
                .padding(top = 24.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .onSizeChanged { height = it.height }
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .nestedScroll(connection)
                .draggable(
                    state = rememberDraggableState(::drag),
                    orientation = Orientation.Vertical,
                    onDragStopped = { settle(it) },
                ),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .drawBehind { drawRect(handle) },
                    )
                }
                content(::hide)
            }
        }
    }
}
