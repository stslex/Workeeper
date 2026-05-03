package io.github.stslex.workeeper.core.data.exercise.tags.model

import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import kotlin.uuid.Uuid

data class TagDataModel(
    val uuid: String,
    val name: String,
)

internal fun TagEntity.toData(): TagDataModel = TagDataModel(
    uuid = uuid.toString(),
    name = name,
)

internal fun TagDataModel.toEntity(): TagEntity = TagEntity(
    uuid = Uuid.parse(uuid),
    name = name,
)
