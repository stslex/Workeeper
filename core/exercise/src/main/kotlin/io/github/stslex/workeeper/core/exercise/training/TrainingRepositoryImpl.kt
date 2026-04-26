package io.github.stslex.workeeper.core.exercise.training

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.database.tag.TagDao
import io.github.stslex.workeeper.core.database.tag.TagEntity
import io.github.stslex.workeeper.core.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.database.tag.TrainingTagEntity
import io.github.stslex.workeeper.core.database.training.TrainingDao
import io.github.stslex.workeeper.core.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.database.training.TrainingExerciseEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

@Singleton
class TrainingRepositoryImpl @Inject constructor(
    private val dao: TrainingDao,
    private val trainingExerciseDao: TrainingExerciseDao,
    private val tagDao: TagDao,
    private val trainingTagDao: TrainingTagDao,
    private val sessionDao: SessionDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : TrainingRepository {

    // v3 has no name-LIKE paged search; ignores `query` and returns all template trainings.
    override fun getTrainings(query: String): Flow<PagingData<TrainingDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::pagedTemplates,
    ).flow
        .map { pagingData -> pagingData.map { it.toData() } }
        .flowOn(ioDispatcher)

    override fun getTrainingsUnique(query: String): Flow<PagingData<TrainingDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::pagedTemplates,
    ).flow
        .map { pagingData -> pagingData.map { it.toData() } }
        .flowOn(ioDispatcher)

    // v3 has no autocomplete query; return empty until feature redesign.
    override suspend fun searchTrainingsUnique(
        query: String,
        limit: Int,
    ): List<TrainingDataModel> = emptyList()

    override suspend fun updateTraining(training: TrainingChangeDataModel) {
        withContext(ioDispatcher) {
            val entity = training.toEntity()
            val existing = dao.getById(entity.uuid)
            if (existing == null) dao.insert(entity) else dao.update(entity)
            syncLabels(entity.uuid, training.labels)
            syncExercises(entity.uuid, training.exerciseUuids)
        }
    }

    override suspend fun removeTraining(uuid: String) {
        withContext(ioDispatcher) {
            dao.permanentDelete(Uuid.parse(uuid))
        }
    }

    override suspend fun getTraining(
        uuid: String,
    ): TrainingDataModel? = withContext(ioDispatcher) {
        val entityUuid = Uuid.parse(uuid)
        dao.getById(entityUuid)?.toData(
            labels = trainingTagDao.getTagNames(entityUuid),
            exerciseUuids = trainingExerciseDao.getByTraining(entityUuid).map { it.exerciseUuid.toString() },
        )
    }

    override fun subscribeForTraining(
        uuid: String,
    ): Flow<TrainingDataModel> = dao
        .observeById(Uuid.parse(uuid))
        .map { entity ->
            if (entity == null) {
                TrainingDataModel(uuid = uuid, name = "", timestamp = 0L)
            } else {
                entity.toData(
                    labels = trainingTagDao.getTagNames(entity.uuid),
                    exerciseUuids = trainingExerciseDao.getByTraining(entity.uuid).map { it.exerciseUuid.toString() },
                )
            }
        }
        .flowOn(ioDispatcher)

    override suspend fun removeAll(uuids: List<String>) = withContext(ioDispatcher) {
        uuids.forEach { dao.permanentDelete(Uuid.parse(it)) }
    }

    // v3 lacks date-range queries; charts will surface empty until v1 feature rewrite.
    override suspend fun getTrainings(
        query: String,
        startDate: Long,
        endDate: Long,
    ): List<TrainingDataModel> = emptyList()

    override suspend fun archive(uuid: String) {
        withContext(ioDispatcher) {
            dao.archive(Uuid.parse(uuid), System.currentTimeMillis())
        }
    }

    override suspend fun restore(uuid: String) {
        withContext(ioDispatcher) {
            dao.restore(Uuid.parse(uuid))
        }
    }

    override suspend fun permanentDelete(uuid: String) {
        withContext(ioDispatcher) {
            dao.permanentDelete(Uuid.parse(uuid))
        }
    }

    override fun pagedArchived(): Flow<PagingData<TrainingDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::pagedArchived,
    ).flow
        .map { pagingData ->
            pagingData.map { entity ->
                entity.toData(
                    labels = trainingTagDao.getTagNames(entity.uuid),
                    exerciseUuids = trainingExerciseDao.getByTraining(entity.uuid).map { it.exerciseUuid.toString() },
                )
            }
        }
        .flowOn(ioDispatcher)

    override fun observeArchivedCount(): Flow<Int> = dao.observeArchivedCount()
        .flowOn(ioDispatcher)

    override suspend fun countSessionsUsing(
        trainingUuid: String,
    ): Int = withContext(ioDispatcher) {
        sessionDao.countFinishedByTraining(Uuid.parse(trainingUuid))
    }

    private suspend fun syncLabels(trainingUuid: Uuid, labels: List<String>) {
        trainingTagDao.deleteByTraining(trainingUuid)
        if (labels.isEmpty()) return
        val tagUuids = labels
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .map { name ->
                tagDao.findByName(name)?.uuid ?: TagEntity(name = name).also { tagDao.insert(it) }.uuid
            }
        trainingTagDao.insert(
            tagUuids.map { tagUuid ->
                TrainingTagEntity(trainingUuid = trainingUuid, tagUuid = tagUuid)
            },
        )
    }

    private suspend fun syncExercises(trainingUuid: Uuid, exerciseUuids: List<String>) {
        trainingExerciseDao.deleteByTraining(trainingUuid)
        if (exerciseUuids.isEmpty()) return
        trainingExerciseDao.insert(
            exerciseUuids.mapIndexed { index, raw ->
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = Uuid.parse(raw),
                    position = index,
                )
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
