package io.github.stslex.workeeper.core.data.exercise.exercise.model

import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import kotlin.uuid.Uuid

data class ExerciseChangeDataModel(
    val uuid: Uuid,
    val name: String,
    val type: ExerciseTypeDataModel = ExerciseTypeDataModel.WEIGHTED,
    val description: String? = null,
    val imagePath: String? = null,
    val archived: Boolean = false,
    val timestamp: Long,
    val labels: List<String> = emptyList(),
    val lastAdHocSets: List<PlanSetDataModel>?,
)

internal fun ExerciseChangeDataModel.toEntity(): ExerciseEntity = ExerciseEntity(
    uuid = uuid,
    name = name,
    type = type.toEntity(),
    description = description,
    imagePath = imagePath,
    archived = archived,
    createdAt = timestamp,
    archivedAt = null,
    lastAdhocSets = PlanSetsConverter.toJson(lastAdHocSets),
)
