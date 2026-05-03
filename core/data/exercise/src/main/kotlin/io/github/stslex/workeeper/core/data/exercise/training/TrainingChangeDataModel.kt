package io.github.stslex.workeeper.core.data.exercise.training

import io.github.stslex.workeeper.core.core.utils.CommonExt.parseOrRandom
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import kotlin.uuid.Uuid

data class TrainingChangeDataModel(
    val uuid: String?,
    val name: String,
    val description: String? = null,
    val isAdhoc: Boolean = false,
    val archived: Boolean = false,
    val timestamp: Long,
    val labels: List<String> = emptyList(),
    val exerciseUuids: List<String> = emptyList(),
)

internal fun TrainingChangeDataModel.toEntity(): TrainingEntity = TrainingEntity(
    uuid = Uuid.parseOrRandom(uuid),
    name = name,
    description = description,
    isAdhoc = isAdhoc,
    archived = archived,
    createdAt = timestamp,
    archivedAt = null,
)
