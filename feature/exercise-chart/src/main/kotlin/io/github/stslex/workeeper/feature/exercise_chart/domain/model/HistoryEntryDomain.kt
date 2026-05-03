// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.model

internal data class HistoryEntryDomain(
    val sessionUuid: String,
    val finishedAt: Long,
    val sets: List<HistorySetDomain>,
)

internal data class HistorySetDomain(
    val weight: Double?,
    val reps: Int,
)
