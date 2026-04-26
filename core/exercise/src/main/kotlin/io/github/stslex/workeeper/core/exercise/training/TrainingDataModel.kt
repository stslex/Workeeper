package io.github.stslex.workeeper.core.exercise.training

import io.github.stslex.workeeper.core.database.training.TrainingEntity
import kotlin.uuid.Uuid

data class TrainingDataModel(
    val uuid: String,
    val name: String,
    val description: String? = null,
    val isAdhoc: Boolean = false,
    val archived: Boolean = false,
    val archivedAt: Long? = null,
    val timestamp: Long,
    val labels: List<String> = emptyList(),
    val exerciseUuids: List<String> = emptyList(),
)

internal fun TrainingEntity.toData(
    labels: List<String> = emptyList(),
    exerciseUuids: List<String> = emptyList(),
): TrainingDataModel = TrainingDataModel(
    uuid = uuid.toString(),
    name = name,
    description = description,
    isAdhoc = isAdhoc,
    archived = archived,
    archivedAt = archivedAt,
    timestamp = createdAt,
    labels = labels,
    exerciseUuids = exerciseUuids,
)

internal fun TrainingDataModel.toEntity(): TrainingEntity = TrainingEntity(
    uuid = Uuid.parse(uuid),
    name = name,
    description = description,
    isAdhoc = isAdhoc,
    archived = archived,
    createdAt = timestamp,
    archivedAt = archivedAt,
)

fun TrainingDataModel.toChangeModel(): TrainingChangeDataModel = TrainingChangeDataModel(
    uuid = uuid,
    name = name,
    description = description,
    isAdhoc = isAdhoc,
    archived = archived,
    timestamp = timestamp,
    labels = labels,
    exerciseUuids = exerciseUuids,
)
