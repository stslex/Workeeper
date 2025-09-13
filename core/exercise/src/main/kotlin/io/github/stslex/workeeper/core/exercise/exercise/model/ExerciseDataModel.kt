package io.github.stslex.workeeper.core.exercise.exercise.model

import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import kotlin.uuid.Uuid

data class ExerciseDataModel(
    val uuid: String,
    val name: String,
    val trainingUuid: String?,
    val sets: List<SetsDataModel>,
    val labels: List<String>,
    val timestamp: Long,
)

fun ExerciseEntity.toData(): ExerciseDataModel = ExerciseDataModel(
    uuid = uuid.toString(),
    name = name,
    trainingUuid = trainingUuid?.toString(),
    labels = labels,
    sets = sets.map { it.toData() },
    timestamp = timestamp,
)

fun ExerciseDataModel.toEntity(): ExerciseEntity = ExerciseEntity(
    uuid = Uuid.parse(uuid),
    name = name,
    trainingUuid = trainingUuid?.let { Uuid.parse(it) },
    sets = sets.map { it.toEntity() },
    labels = labels,
    timestamp = timestamp
)
