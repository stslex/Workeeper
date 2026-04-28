// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.pr

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

private val ACCENT_WIDTH = 3.dp

/**
 * Adds a 3dp amber left-stripe to a set row to flag a PR. The stripe paints behind the
 * row's content; a matching start padding keeps content from sliding under it. Pulls the
 * stripe color from [AppUi] so it reacts to theme changes.
 */
@Composable
fun Modifier.personalRecordAccent(): Modifier {
    val color: Color = AppUi.colors.record.border
    val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { ACCENT_WIDTH.toPx() }
    return this
        .drawBehind {
            drawRect(
                color = color,
                topLeft = Offset.Zero,
                size = Size(widthPx, size.height),
            )
        }
        .padding(start = ACCENT_WIDTH)
}
