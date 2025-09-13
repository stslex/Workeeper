package io.github.stslex.workeeper.core.exercise.exercise.model

import io.github.stslex.workeeper.core.database.exercise.model.SetsEntity
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType.Companion.toData

data class SetsDataModel(
    val reps: Int,
    val weight: Double,
    val type: SetsDataType
)

internal fun SetsDataModel.toEntity(): SetsEntity = SetsEntity(
    reps = reps,
    weight = weight,
    type = type.toEntity()
)

internal fun SetsEntity.toData(): SetsDataModel = SetsDataModel(
    reps = reps,
    weight = weight,
    type = type.toData()
)