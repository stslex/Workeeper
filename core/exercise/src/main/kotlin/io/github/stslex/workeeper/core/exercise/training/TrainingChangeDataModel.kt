package io.github.stslex.workeeper.core.exercise.training

import io.github.stslex.workeeper.core.core.utils.CommonExt.parseOrRandom
import io.github.stslex.workeeper.core.database.training.TrainingEntity
import kotlin.uuid.Uuid

data class TrainingChangeDataModel(
    val uuid: String?,
    val name: String,
    val labels: List<String>,
    val exerciseUuids: List<String>,
    val timestamp: Long,
)

internal fun TrainingChangeDataModel.toEntity(): TrainingEntity = TrainingEntity(
    uuid = Uuid.parseOrRandom(uuid),
    name = name,
    labels = labels,
    exercises = exerciseUuids.map { Uuid.parse(it) },
    timestamp = timestamp,
)
