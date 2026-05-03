// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.exercise.model

import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType.Companion.toData
import kotlin.uuid.Uuid

data class SetsDataModel(
    val uuid: String,
    val reps: Int,
    val weight: Double?,
    val type: SetsDataType,
)

internal fun SetsDataModel.toEntity(
    performedExerciseUuid: Uuid,
    position: Int,
): SetEntity = SetEntity(
    uuid = Uuid.parse(uuid),
    performedExerciseUuid = performedExerciseUuid,
    position = position,
    reps = reps,
    weight = weight,
    type = type.toEntity(),
)

internal fun SetEntity.toData(): SetsDataModel = SetsDataModel(
    uuid = uuid.toString(),
    reps = reps,
    weight = weight,
    type = type.toData(),
)

internal fun SetEntity.toSummary(): SetSummary = SetSummary(
    weight = weight,
    reps = reps,
    type = type.toData(),
)
