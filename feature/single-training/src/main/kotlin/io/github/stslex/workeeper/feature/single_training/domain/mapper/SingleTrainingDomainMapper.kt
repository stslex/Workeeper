// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain.mapper

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionStateDataModel
import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.feature.single_training.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.SessionStateDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.single_training.domain.model.TagDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingChangeDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomain

internal fun TrainingDataModel.toDomain(): TrainingDomain = TrainingDomain(
    uuid = uuid,
    name = name,
    description = description,
    isAdhoc = isAdhoc,
    archived = archived,
    archivedAt = archivedAt,
    timestamp = timestamp,
    labels = labels,
    exerciseUuids = exerciseUuids,
)

internal fun TrainingChangeDomain.toData(): TrainingChangeDataModel = TrainingChangeDataModel(
    uuid = uuid,
    name = name,
    description = description,
    isAdhoc = isAdhoc,
    archived = archived,
    timestamp = timestamp,
    labels = labels,
    exerciseUuids = exerciseUuids,
)

internal fun ExerciseDataModel.toDomain(): ExerciseDomain = ExerciseDomain(
    uuid = uuid,
    name = name,
    type = type.toDomain(),
    description = description,
    imagePath = imagePath,
)

internal fun ExerciseTypeDataModel.toDomain(): ExerciseTypeDomain = when (this) {
    ExerciseTypeDataModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
    ExerciseTypeDataModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
}

internal fun TagDataModel.toDomain(): TagDomain = TagDomain(uuid = uuid, name = name)

internal fun SessionDataModel.toDomain(): SessionDomain = SessionDomain(
    uuid = uuid,
    trainingUuid = trainingUuid,
    state = state.toDomain(),
    startedAt = startedAt,
    finishedAt = finishedAt,
)

internal fun SessionStateDataModel.toDomain(): SessionStateDomain = when (this) {
    SessionStateDataModel.IN_PROGRESS -> SessionStateDomain.IN_PROGRESS
    SessionStateDataModel.FINISHED -> SessionStateDomain.FINISHED
}

internal fun ActiveSessionInfo.toDomain(): ActiveSessionDomain = ActiveSessionDomain(
    sessionUuid = sessionUuid,
    trainingUuid = trainingUuid,
    startedAt = startedAt,
)

internal fun SetTypeDataModel.toDomain(): SetTypeDomain = when (this) {
    SetTypeDataModel.WARMUP -> SetTypeDomain.WARMUP
    SetTypeDataModel.WORK -> SetTypeDomain.WORK
    SetTypeDataModel.FAILURE -> SetTypeDomain.FAILURE
    SetTypeDataModel.DROP -> SetTypeDomain.DROP
}

internal fun SetTypeDomain.toData(): SetTypeDataModel = when (this) {
    SetTypeDomain.WARMUP -> SetTypeDataModel.WARMUP
    SetTypeDomain.WORK -> SetTypeDataModel.WORK
    SetTypeDomain.FAILURE -> SetTypeDataModel.FAILURE
    SetTypeDomain.DROP -> SetTypeDataModel.DROP
}

internal fun PlanSetDataModel.toDomain(): PlanSetDomain = PlanSetDomain(
    weight = weight,
    reps = reps,
    type = type.toDomain(),
)

internal fun PlanSetDomain.toData(): PlanSetDataModel = PlanSetDataModel(
    weight = weight,
    reps = reps,
    type = type.toData(),
)

internal fun SessionConflictResolver.Resolution.toDomain(): StartSessionConflict = when (this) {
    SessionConflictResolver.Resolution.ProceedFresh -> StartSessionConflict.ProceedFresh
    is SessionConflictResolver.Resolution.SilentResume -> StartSessionConflict.SilentResume(sessionUuid)
    is SessionConflictResolver.Resolution.NeedsUserChoice -> StartSessionConflict.NeedsUserChoice(active.toDomain())
}
