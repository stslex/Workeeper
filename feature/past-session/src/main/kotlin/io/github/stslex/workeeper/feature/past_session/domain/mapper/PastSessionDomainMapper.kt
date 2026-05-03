// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain.mapper

import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.session.model.PerformedExerciseDetailDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDetailDataModel
import io.github.stslex.workeeper.feature.past_session.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.PerformedExerciseDetailDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SessionDetailDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SetDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SetTypeDomain

internal fun SessionDetailDataModel.toDomain(): SessionDetailDomain = SessionDetailDomain(
    sessionUuid = sessionUuid,
    trainingUuid = trainingUuid,
    trainingName = trainingName,
    isAdhoc = isAdhoc,
    startedAt = startedAt,
    finishedAt = finishedAt,
    exercises = exercises.map { it.toDomain() },
)

internal fun PerformedExerciseDetailDataModel.toDomain(): PerformedExerciseDetailDomain =
    PerformedExerciseDetailDomain(
        performedExerciseUuid = performedExerciseUuid,
        exerciseUuid = exerciseUuid,
        exerciseName = exerciseName,
        exerciseType = exerciseType.toDomain(),
        position = position,
        skipped = skipped,
        sets = sets.map { it.toDomain() },
    )

internal fun ExerciseTypeDataModel.toDomain(): ExerciseTypeDomain = when (this) {
    ExerciseTypeDataModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
    ExerciseTypeDataModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
}

internal fun SetsDataModel.toDomain(): SetDomain = SetDomain(
    uuid = uuid,
    reps = reps,
    weight = weight,
    type = type.toDomain(),
)

internal fun SetsDataType.toDomain(): SetTypeDomain = when (this) {
    SetsDataType.WARM -> SetTypeDomain.WARMUP
    SetsDataType.WORK -> SetTypeDomain.WORK
    SetsDataType.FAIL -> SetTypeDomain.FAILURE
    SetsDataType.DROP -> SetTypeDomain.DROP
}

internal fun SetDomain.toData(): SetsDataModel = SetsDataModel(
    uuid = uuid,
    reps = reps,
    weight = weight,
    type = type.toData(),
)

internal fun SetTypeDomain.toData(): SetsDataType = when (this) {
    SetTypeDomain.WARMUP -> SetsDataType.WARM
    SetTypeDomain.WORK -> SetsDataType.WORK
    SetTypeDomain.FAILURE -> SetsDataType.FAIL
    SetTypeDomain.DROP -> SetsDataType.DROP
}
