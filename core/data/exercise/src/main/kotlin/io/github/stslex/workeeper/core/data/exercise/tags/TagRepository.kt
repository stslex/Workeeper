package io.github.stslex.workeeper.core.data.exercise.tags

import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
import kotlinx.coroutines.flow.Flow

interface TagRepository {

    fun observeAll(): Flow<List<TagDataModel>>

    suspend fun searchByPrefix(prefix: String): List<TagDataModel>

    suspend fun findByName(name: String): TagDataModel?

    suspend fun add(name: String): TagDataModel

    /**
     * Hot stream of the [limit] longest-idle tags — «Отставшие группы»: per tag, the finish
     * time of the last session in which a non-skipped exercise carrying it was performed,
     * oldest first. Tags with no finished history are absent, not infinitely idle — see
     * `TagDao.observeTagIdleStats`.
     */
    fun observeTagIdleStats(limit: Int): Flow<List<TagIdleStat>>

    /** One «Отставшие группы» row: the tag and when its group last trained. */
    data class TagIdleStat(
        val name: String,
        val lastTrainedAt: Long,
    )
}
