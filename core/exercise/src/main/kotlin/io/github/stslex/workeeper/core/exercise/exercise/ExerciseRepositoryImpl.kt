package io.github.stslex.workeeper.core.exercise.exercise

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.database.tag.ExerciseTagEntity
import io.github.stslex.workeeper.core.database.tag.TagDao
import io.github.stslex.workeeper.core.database.tag.TagEntity
import io.github.stslex.workeeper.core.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.toData
import io.github.stslex.workeeper.core.exercise.exercise.model.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

@Singleton
internal class ExerciseRepositoryImpl @Inject constructor(
    private val dao: ExerciseDao,
    private val tagDao: TagDao,
    private val exerciseTagDao: ExerciseTagDao,
    private val trainingExerciseDao: TrainingExerciseDao,
    private val sessionDao: SessionDao,
    @IODispatcher private val bgDispatcher: CoroutineDispatcher,
) : ExerciseRepository {

    override val exercises: Flow<PagingData<ExerciseDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::pagedActive,
    ).flow
        .map { pagingData -> pagingData.map { it.toData() } }
        .flowOn(bgDispatcher)

    // v3 has no name-LIKE paged search; ignores `query` and returns all active exercises.
    override fun getExercises(query: String): Flow<PagingData<ExerciseDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::pagedActive,
    ).flow
        .map { pagingData -> pagingData.map { it.toData() } }
        .flowOn(bgDispatcher)

    override fun getUniqueExercises(query: String): Flow<PagingData<ExerciseDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::pagedActive,
    ).flow
        .map { pagingData -> pagingData.map { it.toData() } }
        .flowOn(bgDispatcher)

    override suspend fun getExercisesByUuid(
        uuids: List<String>,
    ): List<ExerciseDataModel> = withContext(bgDispatcher) {
        dao.getByUuids(uuids.map(Uuid::parse)).map { entity ->
            entity.toData(labels = loadLabels(entity.uuid))
        }
    }

    override suspend fun getExercise(
        uuid: String,
    ): ExerciseDataModel? = withContext(bgDispatcher) {
        val entityUuid = Uuid.parse(uuid)
        dao.getById(entityUuid)?.toData(labels = loadLabels(entityUuid))
    }

    // v3 lacks date-range queries; charts will surface empty until v1 feature rewrite.
    override suspend fun getExercises(
        name: String,
        startDate: Long,
        endDate: Long,
    ): List<ExerciseDataModel> = emptyList()

    override suspend fun saveItem(item: ExerciseChangeDataModel) = withContext(bgDispatcher) {
        val entity = item.toEntity()
        if (item.uuid.isNullOrBlank()) {
            dao.insert(entity)
        } else {
            val existing = dao.getById(entity.uuid)
            if (existing == null) dao.insert(entity) else dao.update(entity)
        }
        syncLabels(entity.uuid, item.labels)
    }

    override suspend fun deleteItem(uuid: String) {
        withContext(bgDispatcher) {
            dao.permanentDelete(Uuid.parse(uuid))
        }
    }

    // v3 has no autocomplete query; return empty until feature redesign.
    override suspend fun searchItemsWithExclude(query: String): List<ExerciseDataModel> = emptyList()

    override suspend fun deleteAllItems(uuids: List<Uuid>) {
        withContext(bgDispatcher) {
            uuids.forEach { dao.permanentDelete(it) }
        }
    }

    override suspend fun archive(uuid: String) {
        withContext(bgDispatcher) {
            dao.archive(Uuid.parse(uuid), System.currentTimeMillis())
        }
    }

    override suspend fun restore(uuid: String) {
        withContext(bgDispatcher) {
            dao.restore(Uuid.parse(uuid))
        }
    }

    override suspend fun permanentDelete(uuid: String) {
        withContext(bgDispatcher) {
            dao.permanentDelete(Uuid.parse(uuid))
        }
    }

    override suspend fun canArchive(uuid: String): Boolean = withContext(bgDispatcher) {
        trainingExerciseDao.countActiveTemplatesUsing(Uuid.parse(uuid)) == 0
    }

    override fun pagedArchived(): Flow<PagingData<ExerciseDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::pagedArchived,
    ).flow
        .map { pagingData ->
            pagingData.map { entity -> entity.toData(labels = loadLabels(entity.uuid)) }
        }
        .flowOn(bgDispatcher)

    override fun observeArchivedCount(): Flow<Int> = dao.observeArchivedCount()
        .flowOn(bgDispatcher)

    override suspend fun countSessionsUsing(
        exerciseUuid: String,
    ): Int = withContext(bgDispatcher) {
        sessionDao.countFinishedContainingExercise(Uuid.parse(exerciseUuid))
    }

    private suspend fun loadLabels(exerciseUuid: Uuid): List<String> =
        exerciseTagDao.getTagNames(exerciseUuid)

    private suspend fun syncLabels(exerciseUuid: Uuid, labels: List<String>) {
        exerciseTagDao.deleteByExercise(exerciseUuid)
        if (labels.isEmpty()) return
        val tagUuids = labels
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .map { name ->
                tagDao.findByName(name)?.uuid ?: TagEntity(name = name).also { tagDao.insert(it) }.uuid
            }
        exerciseTagDao.insert(
            tagUuids.map { tagUuid ->
                ExerciseTagEntity(exerciseUuid = exerciseUuid, tagUuid = tagUuid)
            },
        )
    }

    companion object {

        private val pagingConfig = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false,
        )
    }
}
