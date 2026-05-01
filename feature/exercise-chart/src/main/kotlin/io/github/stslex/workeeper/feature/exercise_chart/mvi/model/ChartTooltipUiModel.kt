// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import androidx.compose.runtime.Stable

@Stable
internal data class ChartTooltipUiModel(
    val sessionUuid: String,
    val exerciseName: String,
    val dateLabel: String,
    val displayLabel: String,
    val setCountLabel: String?,
)
