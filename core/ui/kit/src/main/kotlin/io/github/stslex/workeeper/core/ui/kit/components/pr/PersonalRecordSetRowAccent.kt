// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.pr

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val ACCENT_WIDTH = 3.dp

/**
 * Paints a 3dp left-stripe in [color] behind the row's content; a matching start padding
 * keeps content from sliding under it. Always applied so the modifier graph stays stable
 * across PR-flag flips — drive visibility by passing [Color.Transparent] for non-PR rows.
 */
@Composable
fun Modifier.personalRecordAccent(color: Color): Modifier {
    val widthPx = with(LocalDensity.current) { ACCENT_WIDTH.toPx() }
    return this
        .drawBehind {
            if (color.alpha > 0f) {
                drawRect(
                    color = color,
                    topLeft = Offset.Zero,
                    size = Size(widthPx, size.height),
                )
            }
        }
        .padding(start = ACCENT_WIDTH)
}
