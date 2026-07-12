package io.github.stslex.workeeper.core.data.exercise.tags

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.tag.TagDao
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.core.data.exercise.tags.model.toData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class TagRepositoryImpl @Inject internal constructor(
    private val dao: TagDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val transitionRunner: DbTransitionRunner,
) : TagRepository {

    override fun observeAll(): Flow<List<TagDataModel>> = dao
        .observeAll()
        .map { tags -> tags.map { it.toData() } }
        .flowOn(ioDispatcher)

    override suspend fun searchByPrefix(prefix: String): List<TagDataModel> =
        withContext(ioDispatcher) {
            dao.searchByPrefix(prefix).map { it.toData() }
        }

    override suspend fun findByName(name: String): TagDataModel? = withContext(ioDispatcher) {
        dao.findByName(name)?.toData()
    }

    override suspend fun add(name: String): TagDataModel = transitionRunner {
        val existing = dao.findByName(name)
        if (existing != null) {
            existing.toData()
        } else {
            val entity = TagEntity(name = name)
            dao.insert(entity)
            entity.toData()
        }
    }

    override suspend fun delete(uuid: String) {
        withContext(ioDispatcher) {
            dao.delete(Uuid.parse(uuid))
        }
    }
}
