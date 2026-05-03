// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.mapper

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetSummary
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.feature.exercise.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseChangeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.HistoryEntryDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.SetSummaryDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.TagDomain

internal fun ExerciseDataModel.toDomain(): ExerciseDomain = ExerciseDomain(
    uuid = uuid,
    name = name,
    type = type.toDomain(),
    description = description,
    imagePath = imagePath,
    archived = archived,
    archivedAt = archivedAt,
    timestamp = timestamp,
    lastAdhocSets = lastAdhocSets?.map { it.toDomain() },
)

internal fun ExerciseChangeDomain.toData(): ExerciseChangeDataModel = ExerciseChangeDataModel(
    uuid = uuid,
    name = name,
    type = type.toData(),
    description = description,
    imagePath = imagePath,
    archived = archived,
    timestamp = timestamp,
    labels = labels,
    lastAdHocSets = lastAdhocSets?.map { it.toData() },
)

internal fun ExerciseTypeDataModel.toDomain(): ExerciseTypeDomain = when (this) {
    ExerciseTypeDataModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
    ExerciseTypeDataModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
}

internal fun ExerciseTypeDomain.toData(): ExerciseTypeDataModel = when (this) {
    ExerciseTypeDomain.WEIGHTED -> ExerciseTypeDataModel.WEIGHTED
    ExerciseTypeDomain.WEIGHTLESS -> ExerciseTypeDataModel.WEIGHTLESS
}

internal fun HistoryEntry.toDomain(): HistoryEntryDomain = HistoryEntryDomain(
    sessionUuid = sessionUuid,
    finishedAt = finishedAt,
    trainingName = trainingName,
    isAdhoc = isAdhoc,
    sets = sets.map { it.toDomain() },
)

internal fun SetSummary.toDomain(): SetSummaryDomain = SetSummaryDomain(
    weight = weight,
    reps = reps,
    type = type.toDomain(),
)

internal fun SetsDataType.toDomain(): SetTypeDomain = when (this) {
    SetsDataType.WARM -> SetTypeDomain.WARMUP
    SetsDataType.WORK -> SetTypeDomain.WORK
    SetsDataType.FAIL -> SetTypeDomain.FAILURE
    SetsDataType.DROP -> SetTypeDomain.DROP
}

internal fun PersonalRecordDataModel.toDomain(): PersonalRecordDomain = PersonalRecordDomain(
    sessionUuid = sessionUuid,
    performedExerciseUuid = performedExerciseUuid,
    setUuid = setUuid,
    weight = weight,
    reps = reps,
    type = type.toDomain(),
    finishedAt = finishedAt,
)

internal fun TagDataModel.toDomain(): TagDomain = TagDomain(
    uuid = uuid,
    name = name,
)

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

internal fun ActiveSessionInfo.toDomain(): ActiveSessionDomain = ActiveSessionDomain(
    sessionUuid = sessionUuid,
    trainingUuid = trainingUuid,
    startedAt = startedAt,
)
