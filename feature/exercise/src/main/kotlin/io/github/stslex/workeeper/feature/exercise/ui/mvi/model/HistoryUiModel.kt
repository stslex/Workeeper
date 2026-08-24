// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.runtime.Stable

/**
 * One history `.row` (extraction §3.5): [dateLabel] is the row name, [setsSummaryLabel] the first
 * five sets. Record-row status is matched at render time on [sessionUuid], not stored here.
 */
@Stable
data class HistoryUiModel(
    val sessionUuid: String,
    val dateLabel: String,
    val setsSummaryLabel: String,
)
