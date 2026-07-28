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
 * A `1px dashed` CSS border, which Compose's `Modifier.border` cannot draw.
 *
 * The mockups use dashed strokes as the **excluded-from-the-record** signature: the skipped
 * rail group, the one-off ordinal chip and badge, the `.addex` button. All of them are
 * `border: 1px dashed <color>` in CSS; this modifier is that treatment, stroke centred inside
 * the bounds the way a CSS border is.
 *
 * Dash rhythm: CSS leaves dash geometry to the UA; measured off Chrome's rendering of the
 * mockup, a 1px dashed border is ~3px on / ~3px off, and that pair is kept as the default
 * rather than exposed as an axis nobody should tune per call site.
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
