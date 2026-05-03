// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.mapper

import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.data.exercise.exercise.model.RecentExerciseDataModel
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.HistoryEntryDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.HistorySetDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.RecentExerciseDomain

internal fun ExerciseTypeDataModel.toDomain(): ExerciseTypeDomain = when (this) {
    ExerciseTypeDataModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
    ExerciseTypeDataModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
}

internal fun RecentExerciseDataModel.toDomain(): RecentExerciseDomain = RecentExerciseDomain(
    uuid = uuid,
    name = name,
    type = type.toDomain(),
    lastFinishedAt = lastFinishedAt,
)

internal fun HistoryEntry.toDomain(): HistoryEntryDomain = HistoryEntryDomain(
    sessionUuid = sessionUuid,
    finishedAt = finishedAt,
    sets = sets.map { HistorySetDomain(weight = it.weight, reps = it.reps) },
)
