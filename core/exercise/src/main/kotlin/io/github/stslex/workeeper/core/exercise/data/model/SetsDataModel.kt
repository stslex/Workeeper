package io.github.stslex.workeeper.core.exercise.data.model

import io.github.stslex.workeeper.core.database.sets.SetsEntity
import io.github.stslex.workeeper.core.database.sets.SetsType
import kotlin.uuid.Uuid

data class SetsDataModel(
    val uuid: String,
    val exerciseUuid: String,
    val reps: Int,
    val weight: Double,
    val type: SetsType
)

fun SetsDataModel.toEntity(): SetsEntity = SetsEntity(
    uuid = Uuid.parse(uuid),
    exerciseUuid = Uuid.parse(exerciseUuid),
    reps = reps,
    weight = weight,
    type = type
)

fun SetsEntity.toData(): SetsDataModel = SetsDataModel(
    uuid = uuid.toString(),
    exerciseUuid = exerciseUuid.toString(),
    reps = reps,
    weight = weight,
    type = type
)