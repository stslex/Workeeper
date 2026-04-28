// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.model

import androidx.compose.runtime.Stable

@Stable
data class HistoryUiModel(
    val sessionUuid: String,
    val setsSummaryLabel: String,
    val metaLabel: String,
)
