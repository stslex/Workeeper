package io.github.stslex.workeeper.feature.single_training.domain.model

import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel

internal data class ExerciseDomainModel(
    val uuid: String,
    val name: String,
    val labels: List<String>,
    val sets: Int,
    val timestamp: Long,
)

internal fun ExerciseDataModel.toDomain(): ExerciseDomainModel = ExerciseDomainModel(
    uuid = uuid,
    labels = labels,
    sets = sets.size,
    name = name,
    timestamp = timestamp
)
