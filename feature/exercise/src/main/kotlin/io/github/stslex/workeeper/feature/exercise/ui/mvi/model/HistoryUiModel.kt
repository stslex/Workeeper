// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.runtime.Stable

/**
 * One history `.row` (extraction §3.5): the day-month date is the row name, the compact
 * set summary (`7×12 · 7×12 · …`, first five, then an ellipsis) is the meta sub-line.
 * The training name and the ad-hoc marker are NOT drawn on these rows any more — the
 * session screen the row opens carries both (delta table notes the drop). Whether the row
 * is the record row is decided at render time by matching [sessionUuid] against the
 * record's, so the trailing tag stays live when the PR flow re-emits.
 */
@Stable
data class HistoryUiModel(
    val sessionUuid: String,
    val dateLabel: String,
    val setsSummaryLabel: String,
)
