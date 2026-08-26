// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.border

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A dashed border — Compose's `Modifier.border` cannot draw one. Marks the
 * excluded-from-the-record surfaces; the stroke is centred in the bounds like a CSS border.
 */
fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = DEFAULT_STROKE,
): Modifier = drawBehind {
    val stroke = strokeWidth.toPx()
    val inset = stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius.toPx() - inset),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(DASH_ON.toPx(), DASH_OFF.toPx()),
            ),
        ),
    )
}

private val DEFAULT_STROKE: Dp = 1.dp
private val DASH_ON: Dp = 3.dp
private val DASH_OFF: Dp = 3.dp
