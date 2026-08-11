// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import androidx.compose.runtime.Stable
import java.time.LocalDate

/**
 * One point on the chart — one calendar day. [value] is the Y value after the active metric
 * fold; [setCount] feeds the readout caption's «N подходов». The tooltip-era fields
 * (`sessionUuid`, `weight`, `reps`) left with their only reader — the winner identity and
 * the aggregate-point null/0 contract live on `ChartPointDomain`, where the parity tests
 * pin them.
 */
@Stable
data class ChartPointUiModel(
    val day: LocalDate,
    val dayMillis: Long,
    val value: Double,
    val setCount: Int,
)
