package io.github.stslex.workeeper.core.exercise.exercise.model

import io.github.stslex.workeeper.core.core.utils.CommonExt.parseOrRandom
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import kotlin.uuid.Uuid

data class ExerciseChangeDataModel(
    val uuid: String? = null,
    val name: String,
    val type: ExerciseTypeDataModel = ExerciseTypeDataModel.WEIGHTED,
    val description: String? = null,
    val imagePath: String? = null,
    val archived: Boolean = false,
    val timestamp: Long,
    val labels: List<String> = emptyList(),
)

internal fun ExerciseChangeDataModel.toEntity(): ExerciseEntity = ExerciseEntity(
    uuid = Uuid.parseOrRandom(uuid),
    name = name,
    type = type.toEntity(),
    description = description,
    imagePath = imagePath,
    archived = archived,
    createdAt = timestamp,
    archivedAt = null,
)
