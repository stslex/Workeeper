// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import androidx.compose.runtime.Stable

@Stable
internal data class ChartFooterStatsUiModel(
    val minTitle: String,
    val minValue: String,
    val maxTitle: String,
    val maxValue: String,
    val lastTitle: String,
    val lastValue: String,
)
