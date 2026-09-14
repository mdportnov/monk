package com.mdportnov.monk.shared.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * The one motion vocabulary of the app. Three durations, three curves (Material 3 emphasized
 * family), and the handful of transitions built from them, so that a page, its title, a tab, a
 * sheet and a card that grows all move with the same hand.
 */
object Motion {
    const val Short = 200
    const val Medium = 300
    const val Long = 450

    /** Symmetric: for things that move from one resting place to another. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Entering: fast start, long settle. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Leaving: gentle start, quick departure. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> enter(duration: Int = Medium): TweenSpec<T> = tween(duration, easing = EmphasizedDecelerate)
    fun <T> exit(duration: Int = Short): TweenSpec<T> = tween(duration, easing = EmphasizedAccelerate)
    fun <T> standard(duration: Int = Medium): TweenSpec<T> = tween(duration, easing = Standard)

    val contentSize: FiniteAnimationSpec<IntSize> = standard()
    val color: FiniteAnimationSpec<Color> = standard()
    val float: FiniteAnimationSpec<Float> = standard()

    /**
     * Shared-axis X: both pages travel the same distance on the same curve, the old one fading
     * out early and the new one fading in late. `travel` is the fraction of the width covered.
     */
    fun sharedAxisX(forward: Boolean, travel: Float = 0.3f): ContentTransform {
        val dir = if (forward) 1 else -1
        val enter = slideInHorizontally(standard(Medium)) { (dir * it * travel).roundToInt() } +
            fadeIn(tween(Medium - 60, delayMillis = 60, easing = LinearOutSlowInEasing))
        val exit = slideOutHorizontally(standard(Medium)) { (-dir * it * travel).roundToInt() } +
            fadeOut(tween(120, easing = FastOutLinearInEasing))
        return enter togetherWith exit
    }

    /** Fade through: content replaced in place (loading → list, one state of a glyph → another). */
    fun fadeThrough(): ContentTransform =
        (fadeIn(tween(Medium - 60, delayMillis = 60, easing = LinearOutSlowInEasing)) + scaleIn(enter(Medium), initialScale = 0.96f)) togetherWith
            fadeOut(tween(120, easing = FastOutLinearInEasing))

    /** A floating element (pill, badge) that pops into place and shrinks away; [delay] staggers a row of them. */
    fun appear(delay: Int = 0): EnterTransition =
        fadeIn(tween(Short, delayMillis = delay, easing = EmphasizedDecelerate)) +
            scaleIn(tween(Medium, delayMillis = delay, easing = EmphasizedDecelerate), initialScale = 0.85f)
    fun disappear(): ExitTransition = fadeOut(exit(Short)) + scaleOut(exit(Short), targetScale = 0.85f)

    /** In-flow content that unfolds vertically (a notice, a save button, a chip row). */
    fun reveal(): EnterTransition = fadeIn(enter(Medium)) + expandVertically(enter(Medium))
    fun conceal(): ExitTransition = fadeOut(exit(Short)) + shrinkVertically(exit(Medium))

    /** Lazy-list rows: fade with the same curves, slide into their new slot on the standard one. */
    fun LazyItemScope.itemMotion(): Modifier = Modifier.animateItem(
        fadeInSpec = enter(Medium),
        placementSpec = standard<IntOffset>(Medium),
        fadeOutSpec = exit(Short),
    )
}

/**
 * A phase in [0, 1) read straight off the frame clock: (frame time mod period) / period. It is
 * a function of time alone, so whatever is recreated, recomposed or re-coloured picks up
 * exactly where the clock is — a drift or a breath never restarts.
 */
@Composable
fun rememberFrameClock(periodMillis: Long): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { t -> phase.floatValue = ((t / 1_000_000L) % periodMillis).toFloat() / periodMillis }
        }
    }
    return phase
}
