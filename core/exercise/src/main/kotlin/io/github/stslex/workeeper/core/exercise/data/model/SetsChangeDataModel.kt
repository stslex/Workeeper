package io.github.stslex.workeeper.core.exercise.data.model

import io.github.stslex.workeeper.core.database.sets.SetsEntity
import io.github.stslex.workeeper.core.database.sets.SetsType
import kotlin.uuid.Uuid

data class SetsChangeDataModel(
    val uuid: String?,
    val exerciseUuid: String,
    val reps: Int,
    val weight: Double,
    val type: SetsType
)

fun SetsChangeDataModel.toEntity() = SetsEntity(
    uuid = uuid?.let { Uuid.parse(uuid) } ?: Uuid.random(),
    exerciseUuid = Uuid.parse(exerciseUuid),
    reps = reps,
    weight = weight,
    type = type
)