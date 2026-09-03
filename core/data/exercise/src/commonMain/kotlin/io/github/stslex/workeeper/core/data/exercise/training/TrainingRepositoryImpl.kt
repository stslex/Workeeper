package io.github.stslex.workeeper.core.data.exercise.training

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.core.coroutine.asyncMapIndexed
import io.github.stslex.workeeper.core.core.coroutine.asyncScope
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.database.tag.TagDao
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository.BulkArchiveOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions", "LongParameterList")
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class TrainingRepositoryImpl @Inject internal constructor(
    private val dao: TrainingDao,
    private val trainingExerciseDao: TrainingExerciseDao,
    private val tagDao: TagDao,
    private val trainingTagDao: TrainingTagDao,
    private val sessionDao: SessionDao,
    private val exerciseRepository: ExerciseRepository,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val dbTransition: DbTransitionRunner,
) : TrainingRepository {

    override fun getTrainingsUnique(query: String): Flow<PagingData<TrainingDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::pagedTemplates,
    ).flow
        .map { pagingData -> pagingData.map { it.toData() } }
        .flowOn(ioDispatcher)

    override suspend fun updateName(uuid: String, name: String) {
        withContext(ioDispatcher) {
            dao.updateName(Uuid.parse(uuid), name)
        }
    }

    override suspend fun updateTraining(training: TrainingChangeDataModel) {
        withContext(ioDispatcher) {
            writeTraining(training)
        }
    }

    override suspend fun updateTrainingWithPlans(
        training: TrainingChangeDataModel,
        plans: List<TrainingRepository.ExercisePlanWrite>,
    ) {
        withContext(ioDispatcher) {
            dbTransition {
                // GUARD: plan updates run after the sync, which re-inserts the rows they hit.
                val trainingUuid = writeTraining(training)
                plans.forEach { plan ->
                    trainingExerciseDao.updatePlanSets(
                        trainingUuid = trainingUuid,
                        exerciseUuid = Uuid.parse(plan.exerciseUuid),
                        planSets = PlanSetsConverter.toJson(plan.planSets),
                    )
                }
                // Auto-prune orphan tags here, not in `writeTraining` — `updateTraining` shares
                // that helper with no transaction around it.
                tagDao.deleteOrphans()
            }
        }
    }

    /** The one write path both save shapes share. Returns the row's resolved uuid. */
    private suspend fun writeTraining(training: TrainingChangeDataModel): Uuid = coroutineScope {
        val entity = training.toEntity()
        val existing = dao.getById(entity.uuid)
        if (existing == null) {
            dao.insert(entity)
        } else {
            dao.update(entity)
        }
        val syncLabelsDeferred = async {
            syncLabels(entity.uuid, training.labels)
        }
        syncExercises(entity.uuid, training.exerciseUuids)
        syncLabelsDeferred.await()
        entity.uuid
    }

    override suspend fun removeTraining(uuid: String) {
        withContext(ioDispatcher) {
            dao.permanentDelete(Uuid.parse(uuid))
        }
    }

    override suspend fun getTraining(
        uuid: String,
    ): TrainingDataModel? = dbTransition {
        val entityUuid = Uuid.parse(uuid)
        val labels = async { trainingTagDao.getTagNames(entityUuid) }
        val exerciseUuids = async {
            trainingExerciseDao.getByTraining(entityUuid)
                .map { it.exerciseUuid.toString() }
        }
        dao.getById(entityUuid)?.toData(
            labels = labels.await(),
            exerciseUuids = exerciseUuids.await(),
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
                val labelsDeferred = asyncScope {
                    trainingTagDao.getTagNames(entity.uuid)
                }
                val exerciseUuidsDeferred = asyncScope {
                    trainingExerciseDao.getByTraining(entity.uuid)
                        .map { it.exerciseUuid.toString() }
                }
                entity.toData(
                    labels = labelsDeferred.await(),
                    exerciseUuids = exerciseUuidsDeferred.await(),
                )
            }
        }
        .flowOn(ioDispatcher)

    override suspend fun removeAll(uuids: List<String>) = withContext(ioDispatcher) {
        dao.permanentDeleteAll(uuids.map { Uuid.parse(it) })
    }

    override suspend fun archive(uuid: String) {
        withContext(ioDispatcher) {
            dao.archive(Uuid.parse(uuid), Clock.System.now().toEpochMilliseconds())
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
                    exerciseUuids = trainingExerciseDao.getByTraining(entity.uuid)
                        .map { it.exerciseUuid.toString() },
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

    override fun observeMostForgottenTemplate(): Flow<TrainingListItem?> = dao
        .observeMostForgottenTemplate()
        .map { row -> row?.toData() }
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
        // Archiving a training with an in-progress session would orphan it; skip and name it.
        val activeTrainingUuid = sessionDao.getActive()?.trainingUuid
        val (allowed, blocked) = parsed.partition { it != activeTrainingUuid }
        val blockedNames = blocked.mapNotNull { dao.getById(it)?.name }
        if (allowed.isNotEmpty()) {
            dao.archiveAll(allowed, Clock.System.now().toEpochMilliseconds())
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
        val tags = labels.filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .asyncMap { name -> tagDao.findByName(name) ?: TagEntity(name = name) }

        val tagsInsertionAwait = asyncScope {
            tagDao.insertAll(tags)
        }

        trainingTagDao.insert(
            tags.map { tag ->
                TrainingTagEntity(
                    trainingUuid = trainingUuid,
                    tagUuid = tag.uuid,
                )
            },
        )
        tagsInsertionAwait.await()
    }

    private suspend fun syncExercises(trainingUuid: Uuid, exerciseUuids: List<String>) {
        // Snapshot existing rows before truncating so plan_sets survives a position-map edit.
        val existing = asyncScope {
            trainingExerciseDao.getByTraining(trainingUuid)
                .associateBy { it.exerciseUuid }
        }
        trainingExerciseDao.deleteByTraining(trainingUuid)
        if (exerciseUuids.isEmpty()) return

        val rows = exerciseUuids.asyncMapIndexed { index, rawUuid ->
            val exerciseUuid = Uuid.parse(rawUuid)
            val planSets = if (existing.await().containsKey(exerciseUuid)) {
                // Already attached — preserve whatever the user has, including empty.
                existing.await()[exerciseUuid]?.planSets
            } else {
                // New attachment — inherit the exercise's own default plan. GUARD: the
                // `containsKey` above, not `?:`, keeps a deliberately-cleared plan empty.
                PlanSetsConverter.toJson(exerciseRepository.getAdhocPlan(rawUuid))
            }
            TrainingExerciseEntity(
                trainingUuid = trainingUuid,
                exerciseUuid = exerciseUuid,
                position = index,
                planSets = planSets,
            )
        }
        trainingExerciseDao.insert(rows)
    }

    companion object {

        private val pagingConfig = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false,
        )
    }
}
