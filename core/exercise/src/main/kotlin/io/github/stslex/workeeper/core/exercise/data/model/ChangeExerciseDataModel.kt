package io.github.stslex.workeeper.core.exercise.data.model

import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import kotlin.uuid.Uuid

data class ChangeExerciseDataModel(
    val uuid: String? = null,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val timestamp: Long,
)

fun ChangeExerciseDataModel.toEntity(): ExerciseEntity = ExerciseEntity(
    uuid = uuid
        ?.let { Uuid.parse(uuid) }
        ?: Uuid.random(),
    name = name,
    sets = sets,
    reps = reps,
    weight = weight,
    timestamp = timestamp
)
