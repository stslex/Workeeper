package io.github.stslex.workeeper.core.exercise.exercise.model

import io.github.stslex.workeeper.core.core.utils.CommonExt.parseOrRandom
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import kotlin.uuid.Uuid

data class ExerciseChangeDataModel(
    val uuid: String? = null,
    val name: String,
    val trainingUuid: String?,
    val sets: List<SetsDataModel>,
    val labels: List<String>,
    val timestamp: Long,
)

fun ExerciseChangeDataModel.toEntity(): ExerciseEntity = ExerciseEntity(
    uuid = Uuid.parseOrRandom(uuid),
    name = name,
    trainingUuid = trainingUuid?.let { Uuid.parse(it) },
    sets = sets.map { it.toEntity() },
    labels = labels,
    timestamp = timestamp
)
