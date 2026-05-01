// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Single source of truth for projecting a [ChartPointUiModel] into canvas-local pixels.
 * Both the chart draw loop and the tooltip overlay positioning resolve point coordinates
 * through this helper, so they cannot drift apart as one path changes and the other does
 * not.
 */
@Immutable
internal data class PointPixelMap(
    val canvasSize: Size,
    val axisPaddingPx: Float,
    val windowStartDay: LocalDate,
    val totalDays: Float,
    val minY: Double,
    val yRange: Double,
) {

    fun toPx(point: ChartPointUiModel): Offset {
        val width = canvasSize.width - axisPaddingPx * 2f
        val height = canvasSize.height - axisPaddingPx * 2f
        val dayIndex = ChronoUnit.DAYS.between(windowStartDay, point.day).toFloat()
        val xRatio = (dayIndex / totalDays).coerceIn(0f, 1f)
        val yRatio = ((point.value - minY) / yRange).toFloat().coerceIn(0f, 1f)
        return Offset(
            x = axisPaddingPx + xRatio * width,
            y = axisPaddingPx + height - yRatio * height,
        )
    }
}
