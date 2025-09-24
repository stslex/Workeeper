package io.github.stslex.workeeper.feature.single_training.domain.model

import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel

internal data class TrainingDomainModel(
    val uuid: String,
    val name: String,
    val exercises: List<ExerciseDomainModel>,
    val labels: List<String>,
    val timestamp: Long,
)

internal fun TrainingDataModel.toDomain(
    exercises: List<ExerciseDomainModel>,
): TrainingDomainModel = TrainingDomainModel(
    uuid = uuid,
    name = name,
    labels = labels,
    exercises = exercises,
    timestamp = timestamp,
)
