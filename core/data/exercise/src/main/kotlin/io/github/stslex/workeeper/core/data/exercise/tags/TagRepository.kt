package io.github.stslex.workeeper.core.data.exercise.tags

import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
import kotlinx.coroutines.flow.Flow

interface TagRepository {

    fun observeAll(): Flow<List<TagDataModel>>

    suspend fun searchByPrefix(prefix: String): List<TagDataModel>

    suspend fun findByName(name: String): TagDataModel?

    suspend fun add(name: String): TagDataModel

    suspend fun delete(uuid: String)
}
