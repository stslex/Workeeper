package io.github.stslex.workeeper.feature.single_training.domain.model

import io.github.stslex.workeeper.core.exercise.training.TrainingChangeDataModel

internal data class TrainingDomainChangeModel(
    val uuid: String?,
    val name: String,
    val exercisesUuids: List<String>,
    val labels: List<String>,
    val timestamp: Long,
) {

    fun toData(): TrainingChangeDataModel = TrainingChangeDataModel(
        uuid = uuid,
        name = name,
        labels = labels,
        exerciseUuids = exercisesUuids,
        timestamp = timestamp,
    )
}
