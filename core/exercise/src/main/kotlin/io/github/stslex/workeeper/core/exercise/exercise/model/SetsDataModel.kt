package io.github.stslex.workeeper.core.exercise.exercise.model

import io.github.stslex.workeeper.core.database.session.model.SetEntity
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType.Companion.toData
import kotlin.uuid.Uuid

data class SetsDataModel(
    val uuid: String,
    val reps: Int,
    val weight: Double,
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
    weight = weight ?: 0.0,
    type = type.toData(),
)
