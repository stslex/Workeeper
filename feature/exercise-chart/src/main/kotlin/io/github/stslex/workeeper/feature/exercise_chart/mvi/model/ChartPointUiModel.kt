// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import androidx.compose.runtime.Stable
import java.time.LocalDate

/**
 * One completed-session point on the chart. [value] is the Y value after the active metric
 * fold; [reps] preserves the representative set's PR tiebreak for Weight record marking,
 * while [setCount] feeds the readout caption's «N подходов». [sessionUuid] is the stable
 * identity that keeps two points on the same calendar date independently drawable and
 * scrubbable. Aggregate Session points carry zero [reps].
 */
@Stable
data class ChartPointUiModel(
    val day: LocalDate,
    val dayMillis: Long,
    val sessionUuid: String,
    val value: Double,
    val setCount: Int,
    val reps: Int = 0,
)
