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
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository.BulkArchiveOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
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

    // v3 lacks date-range queries; this legacy overload currently returns no results.
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

    override fun observeRecentTemplates(
        limit: Int,
    ): Flow<List<TrainingListItem>> = dao
        .observeRecentTemplates(limit)
        .map { rows -> rows.map { row -> row.toData(labels = trainingTagDao.getTagNames(row.uuid)) } }
        .flowOn(ioDispatcher)

    override suspend fun countSessionsUsing(
        trainingUuid: String,
    ): Int = withContext(ioDispatcher) {
        sessionDao.countFinishedByTraining(Uuid.parse(trainingUuid))
    }

    override fun pagedActiveWithStats(
        filterTagUuids: Set<String>,
    ): Flow<PagingData<TrainingListItem>> {
        val pager = if (filterTagUuids.isEmpty()) {
            Pager(config = pagingConfig, pagingSourceFactory = dao::pagedActiveWithStats)
        } else {
            val parsed = filterTagUuids.map(Uuid::parse)
            Pager(
                config = pagingConfig,
                pagingSourceFactory = { dao.pagedActiveWithStatsByTags(parsed) },
            )
        }
        return pager.flow
            .map { pagingData ->
                pagingData.map { row -> row.toData(labels = trainingTagDao.getTagNames(row.uuid)) }
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun bulkArchive(
        uuids: Set<String>,
    ): BulkArchiveOutcome = withContext(ioDispatcher) {
        if (uuids.isEmpty()) return@withContext BulkArchiveOutcome(0, emptyList())
        val parsed = uuids.map(Uuid::parse)
        // Block trainings with an in-progress session — archive on those would orphan the
        // active session in the UI, so the bulk path skips them and surfaces names.
        val activeTrainingUuid = sessionDao.getActive()?.trainingUuid
        val (allowed, blocked) = parsed.partition { it != activeTrainingUuid }
        val blockedNames = blocked.mapNotNull { dao.getById(it)?.name }
        if (allowed.isNotEmpty()) {
            dao.archiveAll(allowed, System.currentTimeMillis())
        }
        BulkArchiveOutcome(archivedCount = allowed.size, blockedNames = blockedNames)
    }

    override suspend fun bulkPermanentDelete(uuids: Set<String>) {
        withContext(ioDispatcher) {
            if (uuids.isEmpty()) return@withContext
            dao.permanentDeleteAll(uuids.map(Uuid::parse))
        }
    }

    override suspend fun canBulkPermanentDelete(
        uuids: Set<String>,
    ): Boolean = withContext(ioDispatcher) {
        if (uuids.isEmpty()) return@withContext false
        val activeTrainingUuid = sessionDao.getActive()?.trainingUuid?.toString()
        uuids.all { uuid ->
            uuid != activeTrainingUuid && sessionDao.countFinishedByTraining(Uuid.parse(uuid)) == 0
        }
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
        // Snapshot existing rows before truncating so we can preserve plan_sets across an
        // edit. Plans are sub-entities of (training, exercise) — replacing the position
        // map should not silently wipe them.
        val existing = trainingExerciseDao.getByTraining(trainingUuid)
            .associateBy { it.exerciseUuid }
        trainingExerciseDao.deleteByTraining(trainingUuid)
        if (exerciseUuids.isEmpty()) return
        trainingExerciseDao.insert(
            exerciseUuids.mapIndexed { index, raw ->
                val exerciseUuid = Uuid.parse(raw)
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = exerciseUuid,
                    position = index,
                    planSets = existing[exerciseUuid]?.planSets,
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
