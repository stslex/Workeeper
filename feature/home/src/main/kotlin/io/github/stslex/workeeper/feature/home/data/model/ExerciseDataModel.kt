package io.github.stslex.workeeper.feature.home.data.model

import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import kotlin.uuid.Uuid

data class ExerciseDataModel(
    val uuid: String,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Int,
    val timestamp: Long,
)

fun ExerciseEntity.toData(): ExerciseDataModel = ExerciseDataModel(
    uuid = uuid.toString(),
    name = name,
    sets = sets,
    reps = reps,
    weight = weight,
    timestamp = timestamp
)

fun ExerciseDataModel.toEntity(): ExerciseEntity = ExerciseEntity(
    uuid = Uuid.parse(uuid),
    name = name,
    sets = sets,
    reps = reps,
    weight = weight,
    timestamp = timestamp
)
