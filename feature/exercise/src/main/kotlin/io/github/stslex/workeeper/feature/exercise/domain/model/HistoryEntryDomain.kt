// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.model

internal data class HistoryEntryDomain(
    val sessionUuid: String,
    val finishedAt: Long,
    val trainingName: String,
    val isAdhoc: Boolean,
    val sets: List<SetSummaryDomain>,
)

internal data class SetSummaryDomain(
    val weight: Double?,
    val reps: Int,
    val type: SetTypeDomain,
)
