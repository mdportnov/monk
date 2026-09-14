package com.mdportnov.monk.shared.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * One line that shrinks instead of wrapping: segmented buttons, dock labels, chips. Russian
 * labels run 30–50% longer than English and a wrapped segment reads as broken.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    minSize: Float = 10f,
) {
    val color = LocalContentColor.current
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = TextAlign.Center),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}
