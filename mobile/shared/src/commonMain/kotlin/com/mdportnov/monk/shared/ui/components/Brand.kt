package com.mdportnov.monk.shared.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Morrow's brand orange, as its own app and site use it. */
private val MorrowAccent = Color(0xFFFF4C00)

/**
 * Morrow's "daybreak" mark — a sun rising over the horizon — in a rounded tile the size of
 * [size], matching how the Monk mark is presented next to it. Drawn to the same geometry as
 * Morrow's own logo (a 100 × 56 box: the horizon a round-capped rule, the sun a half-disc
 * standing on it), so the two apps show the same sign.
 */
@Composable
fun MorrowMark(size: Dp) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(size * 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(size),
    ) {
        Box(Modifier.padding(size * 0.2f)) {
            val ink = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.fillMaxSize()) {
                // The logo's own box is 100 × 56; scale it to whatever room the tile leaves.
                val unit = minOf(this.size.width / 100f, this.size.height / 56f)
                val w = 100f * unit
                val h = 56f * unit
                val left = (this.size.width - w) / 2f
                val top = (this.size.height - h) / 2f
                fun x(v: Float) = left + v * unit
                fun y(v: Float) = top + v * unit
                val sun = Path().apply {
                    // A half-disc of radius 26 centred on the horizon, flat side down.
                    arcTo(
                        rect = Rect(Offset(x(24f), y(20f)), Size(52f * unit, 52f * unit)),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = true,
                    )
                    close()
                }
                drawPath(sun, MorrowAccent)
                drawLine(
                    color = ink,
                    start = Offset(x(6f), y(46f)),
                    end = Offset(x(94f), y(46f)),
                    strokeWidth = 8f * unit,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
