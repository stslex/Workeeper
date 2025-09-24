package io.github.stslex.workeeper.core.exercise.exercise.model

import io.github.stslex.workeeper.core.database.exercise.model.SetsEntity
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType.Companion.toData
import kotlin.uuid.Uuid

data class SetsDataModel(
    val uuid: String,
    val reps: Int,
    val weight: Double,
    val type: SetsDataType,
)

internal fun SetsDataModel.toEntity(): SetsEntity = SetsEntity(
    uuid = Uuid.parse(uuid),
    reps = reps,
    weight = weight,
    type = type.toEntity(),
)

internal fun SetsEntity.toData(): SetsDataModel = SetsDataModel(
    uuid = uuid.toString(),
    reps = reps,
    weight = weight,
    type = type.toData(),
)
