// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.exercise.model

import io.github.stslex.workeeper.core.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
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
    val lastAdhocSets: List<PlanSetDataModel>?,
)

internal fun ExerciseEntity.toData(): ExerciseDataModel = ExerciseDataModel(
    uuid = uuid.toString(),
    name = name,
    type = type.toData(),
    description = description,
    imagePath = imagePath,
    archived = archived,
    archivedAt = archivedAt,
    timestamp = createdAt,
    lastAdhocSets = lastAdhocSets?.let { PlanSetsConverter.fromJson(it) },
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
    lastAdhocSets = lastAdhocSets?.let { PlanSetsConverter.toJson(it) },
)
