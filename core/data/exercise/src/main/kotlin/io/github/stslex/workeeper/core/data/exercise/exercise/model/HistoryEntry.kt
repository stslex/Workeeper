// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.exercise.model

data class HistoryEntry(
    val sessionUuid: String,
    val finishedAt: Long,
    val trainingName: String,
    val isAdhoc: Boolean,
    val sets: List<SetSummary>,
)

data class SetSummary(
    val weight: Double?,
    val reps: Int,
    val type: SetsDataType,
)
