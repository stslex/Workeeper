package io.github.stslex.workeeper.core.exercise.exercise.model

import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel.Companion.toData
import kotlin.uuid.Uuid

data class ExerciseDataModel(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeDataModel = ExerciseTypeDataModel.WEIGHTED,
    val description: String? = null,
    val imagePath: String? = null,
    val archived: Boolean = false,
    val archivedAt: Long? = null,
    val timestamp: Long,
    // legacy v2 fields, retained for feature compatibility during v3 transition.
    // `trainingUuid` is not modelled in v3 (exercises are library items, not training-bound).
    // `sets` are now per-session (PerformedExercise/Set tables), not per-template.
    // `labels` are populated from the exercise_tag join when available.
    val trainingUuid: String? = null,
    val sets: List<SetsDataModel> = emptyList(),
    val labels: List<String> = emptyList(),
)

internal fun ExerciseEntity.toData(
    labels: List<String> = emptyList(),
): ExerciseDataModel = ExerciseDataModel(
    uuid = uuid.toString(),
    name = name,
    type = type.toData(),
    description = description,
    imagePath = imagePath,
    archived = archived,
    archivedAt = archivedAt,
    timestamp = createdAt,
    labels = labels,
)

internal fun ExerciseDataModel.toEntity(): ExerciseEntity = ExerciseEntity(
    uuid = Uuid.parse(uuid),
    name = name,
    type = type.toEntity(),
    description = description,
    imagePath = imagePath,
    archived = archived,
    createdAt = timestamp,
    archivedAt = archivedAt,
)
