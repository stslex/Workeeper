// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import androidx.compose.runtime.Stable

@Stable
internal data class ChartFooterStatsUiModel(
    val minLabel: String,
    val maxLabel: String,
    val lastLabel: String,
)
