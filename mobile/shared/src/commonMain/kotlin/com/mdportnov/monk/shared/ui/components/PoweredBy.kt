package com.mdportnov.monk.shared.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Powered by Punto Cero & Mike Portnov w/ ♡" — the same signature line the web products carry:
 * light connectors, weighted names, an outlined heart with the Punto Cero dot.
 */
@Composable
fun PoweredBy(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val strong = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val dot = Color(0xFFFF6A00)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) { append("Powered by ") }
                withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append("Punto Cero") }
                withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) { append(" & ") }
                withStyle(SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append("Mike Portnov") }
                withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic)) { append("  w/ ") }
            },
            style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 1.4.sp, fontSize = 12.5.sp),
            maxLines = 1,
        )
        Box(Modifier.padding(start = 2.dp).size(16.dp)) {
            Icon(Icons.Outlined.FavoriteBorder, null, tint = muted, modifier = Modifier.size(16.dp))
            Canvas(Modifier.size(16.dp)) { drawCircle(dot, radius = 2.dp.toPx(), center = Offset(size.width - 1.5.dp.toPx(), 1.5.dp.toPx())) }
        }
    }
}
