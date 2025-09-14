package io.github.stslex.workeeper.core.exercise.training

import io.github.stslex.workeeper.core.database.training.TrainingEntity
import kotlin.uuid.Uuid

data class TrainingDataModel(
    val uuid: String,
    val name: String,
    val labels: List<String>,
    val exerciseUuids: List<String>,
    val timestamp: Long,
)

internal fun TrainingEntity.toData(): TrainingDataModel = TrainingDataModel(
    uuid = uuid.toString(),
    name = name,
    labels = labels,
    exerciseUuids = exercises.map { it.toString() },
    timestamp = timestamp
)

internal fun TrainingDataModel.toEntity(): TrainingEntity = TrainingEntity(
    uuid = Uuid.parse(uuid),
    name = name,
    labels = labels,
    exercises = exerciseUuids.map { Uuid.parse(it) },
    timestamp = timestamp
)
