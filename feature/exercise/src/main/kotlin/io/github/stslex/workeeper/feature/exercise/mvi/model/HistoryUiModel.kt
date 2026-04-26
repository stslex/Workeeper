// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.exercise.exercise.model.SetSummary
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Stable
data class HistoryUiModel(
    val sessionUuid: String,
    val finishedAt: Long,
    val trainingName: String,
    val isAdhoc: Boolean,
    val sets: ImmutableList<SetSummaryUi>,
)

@Stable
data class SetSummaryUi(
    val weight: Double?,
    val reps: Int,
    val type: SetsDataType,
)

internal fun HistoryEntry.toUi(): HistoryUiModel = HistoryUiModel(
    sessionUuid = sessionUuid,
    finishedAt = finishedAt,
    trainingName = trainingName,
    isAdhoc = isAdhoc,
    sets = sets.map { it.toUi() }.toImmutableList(),
)

internal fun SetSummary.toUi(): SetSummaryUi = SetSummaryUi(
    weight = weight,
    reps = reps,
    type = type,
)
