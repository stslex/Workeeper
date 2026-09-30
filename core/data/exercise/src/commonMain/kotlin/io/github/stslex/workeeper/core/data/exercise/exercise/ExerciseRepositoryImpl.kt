package io.github.stslex.workeeper.core.data.exercise.exercise

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.sqlite.SQLiteException
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.coroutine.asyncForEach
import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.core.coroutine.asyncMapNotNull
import io.github.stslex.workeeper.core.core.coroutine.asyncScope
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.utils.CommonExt.runIf
import io.github.stslex.workeeper.core.core.utils.CommonExt.runIfNotNull
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagEntity
import io.github.stslex.workeeper.core.data.database.tag.TagDao
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository.BulkArchiveOutcome
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository.InlineAdhocResult
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository.SaveResult
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseListItem
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel.Companion.toData
import io.github.stslex.workeeper.core.data.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.data.exercise.exercise.model.RecentExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.toData
import io.github.stslex.workeeper.core.data.exercise.exercise.model.toEntity
import io.github.stslex.workeeper.core.data.exercise.exercise.model.toSummary
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
class ExerciseRepositoryImpl @Inject internal constructor(
    private val dao: ExerciseDao,
    private val tagDao: TagDao,
    private val exerciseTagDao: ExerciseTagDao,
    private val trainingExerciseDao: TrainingExerciseDao,
    private val sessionDao: SessionDao,
    private val setDao: SetDao,
    private val imageStorage: ImageStorage,
    private val transition: DbTransitionRunner,
    @IODispatcher private val bgDispatcher: CoroutineDispatcher,
) : ExerciseRepository {

    override val exercises: Flow<PagingData<ExerciseDataModel>> = Pager(
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
        dao.getByUuids(uuids.map(Uuid::parse)).map { it.toData() }
    }

    override suspend fun getExercise(
        uuid: String,
    ): ExerciseDataModel? = withContext(bgDispatcher) {
        dao.getById(Uuid.parse(uuid))?.toData()
    }

    override suspend fun getLabels(
        exerciseUuid: String,
    ): List<String> = withContext(bgDispatcher) {
        loadLabels(Uuid.parse(exerciseUuid))
    }

    override suspend fun saveItem(
        item: ExerciseChangeDataModel,
    ): SaveResult = transition.mutate {
        val entity = item.toEntity()
        try {
            if (item.uuid.toString().isBlank()) {
                dao.insert(entity)
            } else {
                val existing = dao.getById(entity.uuid)
                if (existing == null) {
                    dao.insert(entity)
                } else {
                    dao.update(entity)
                }
            }
        } catch (e: SQLiteException) {
            if (!e.isUniqueConstraintViolation()) throw e
            return@mutate SaveResult.DuplicateName
        }
        syncLabels(entity.uuid, item.labels)
        // A WEIGHTLESS row may leave no weights behind, here or in any referencing `plan_sets`.
        if (item.type == ExerciseTypeDataModel.WEIGHTLESS) {
            clearWeightsForExercise(entity.uuid)
        }
        // Auto-prune orphan tags in the same transaction as the link writes above.
        tagDao.deleteOrphans()
        SaveResult.Success
    }

    override suspend fun getAdhocPlan(
        exerciseUuid: String,
    ): List<PlanSetDataModel>? = withContext(bgDispatcher) {
        val entity = dao.getById(Uuid.parse(exerciseUuid)) ?: return@withContext null
        PlanSetsConverter.fromJson(entity.lastAdhocSets)
    }

    override suspend fun getAdhocPlans(
        uuids: List<String>,
    ): Map<String, List<PlanSetDataModel>?> = withContext(bgDispatcher) {
        if (uuids.isEmpty()) return@withContext emptyMap()
        val rows = dao.getAdhocPlansBatch(uuids.map(Uuid::parse))
        rows.associate { row ->
            row.uuid.toString() to PlanSetsConverter.fromJson(row.lastAdhocSets)
        }
    }

    override suspend fun createInlineAdhocExercise(
        name: String,
    ): InlineAdhocResult = transition.mutate {
        val trimmed = name.trim()
        // Case-insensitive lookup before insert so a typed name does not trip UNIQUE(name).
        val existing = dao.findByName(trimmed)
        if (existing != null) {
            return@mutate InlineAdhocResult(
                exercise = existing.toData(),
                reusedExisting = true,
            )
        }
        val entity = ExerciseEntity(
            name = trimmed,
            type = ExerciseTypeEntity.WEIGHTED,
            description = null,
            imagePath = null,
            archived = false,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            archivedAt = null,
            lastAdhocSets = null,
            isAdhoc = true,
        )
        dao.insert(entity)
        InlineAdhocResult(
            exercise = entity.toData(),
            reusedExisting = false,
        )
    }

    override suspend fun setAdhocPlan(
        exerciseUuid: String,
        planSets: List<PlanSetDataModel>?,
    ) = transition.mutate {
        dao.updateLastAdhocSets(
            uuid = Uuid.parse(exerciseUuid),
            lastAdhocSets = PlanSetsConverter.toJson(planSets),
        )
    }

    override suspend fun setExerciseType(
        exerciseUuid: String,
        type: ExerciseTypeDataModel,
    ) = transition.mutate {
        dao.updateType(
            uuid = Uuid.parse(exerciseUuid),
            type = type.toEntity(),
        )
    }

    override suspend fun clearWeightsFromAllPlansForExercise(exerciseUuid: String) {
        transition.mutate { clearWeightsForExercise(Uuid.parse(exerciseUuid)) }
    }

    /** The cascade body, callable inside an open transaction; already-null rows are skipped. */
    private suspend fun clearWeightsForExercise(exerciseUuid: Uuid) = coroutineScope {
        val exercise = dao.getById(exerciseUuid) ?: return@coroutineScope
        val adhoc = PlanSetsConverter.fromJson(exercise.lastAdhocSets)
            ?.takeIf { plan -> plan.any { it.weight != null } }
        val updateDeferred = runIfNotNull(adhoc) { plan ->
            async {
                val cleared = plan.map { it.copy(weight = null) }
                dao.updateLastAdhocSets(exerciseUuid, PlanSetsConverter.toJson(cleared))
            }
        }
        val getAllForExerciseDeferred = async {
            trainingExerciseDao.getAllForExercise(exerciseUuid).asyncForEach { row ->
                val parsedPlan = PlanSetsConverter.fromJson(row.planSets) ?: return@asyncForEach
                if (parsedPlan.none { it.weight != null }) return@asyncForEach
                val cleared = parsedPlan.map { it.copy(weight = null) }
                trainingExerciseDao.updatePlanSets(
                    trainingUuid = row.trainingUuid,
                    exerciseUuid = row.exerciseUuid,
                    planSets = PlanSetsConverter.toJson(cleared),
                )
            }
        }
        updateDeferred?.await()
        getAllForExerciseDeferred.await()
    }

    override suspend fun deleteItem(uuid: String) {
        transition.mutate {
            // Read imagePath BEFORE delete — once the row is gone, we lose the path.
            val imagePath = asyncScope { dao.getById(Uuid.parse(uuid))?.imagePath }
            val deleteDeferred = asyncScope { dao.permanentDelete(Uuid.parse(uuid)) }
            imagePath.await()?.let {
                imageStorage.deleteImage(it)
            }
            deleteDeferred.await()
        }
    }

    override suspend fun searchActiveExercises(
        query: String,
        excludeUuids: Set<String>,
    ): List<ExerciseDataModel> = withContext(bgDispatcher) {
        val excluded = excludeUuids.map(Uuid::parse).toSet()
        dao.getAllActive()
            .mapNotNull { entity ->
                runIf(
                    entity.uuid !in excluded &&
                        (query.isBlank() || entity.name.contains(query, ignoreCase = true)),
                ) {
                    entity.toData()
                }
            }
    }

    override suspend fun deleteAllItems(uuids: List<Uuid>) {
        transition.mutate {
            // Snapshot paths before deleting rows so we can clean image files after.
            val paths = uuids.asyncMap { dao.getById(it)?.imagePath }
                .filterNotNull()

            uuids.asyncForEach { dao.permanentDelete(it) }

            paths.asyncForEach { imagePath ->
                imageStorage.deleteImage(imagePath)
            }
        }
    }

    override suspend fun archive(uuid: String) = transition.mutate {
        dao.archive(Uuid.parse(uuid), Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun restore(uuid: String) = transition.mutate {
        dao.restore(Uuid.parse(uuid))
    }

    override suspend fun permanentDelete(uuid: String) {
        transition.mutate {
            val imagePath = dao.getById(Uuid.parse(uuid))?.imagePath
            dao.permanentDelete(Uuid.parse(uuid))
            imagePath?.let { imageStorage.deleteImage(it) }
        }
    }

    override suspend fun canArchive(uuid: String): Boolean = withContext(bgDispatcher) {
        trainingExerciseDao.countActiveTemplatesUsing(Uuid.parse(uuid)) == 0
    }

    override suspend fun canPermanentlyDeleteImmediately(uuid: String): Boolean = transition {
        val parsed = Uuid.parse(uuid)
        val countFinishedExercise = asyncScope {
            sessionDao.countFinishedContainingExercise(parsed) == 0
        }
        val countActiveTemplates = asyncScope {
            trainingExerciseDao.countActiveTemplatesUsing(parsed) == 0
        }
        countFinishedExercise.await() && countActiveTemplates.await()
    }

    override suspend fun getActiveTrainingsUsing(
        exerciseUuid: String,
    ): List<String> = withContext(bgDispatcher) {
        trainingExerciseDao.getActiveTemplateNamesUsing(Uuid.parse(exerciseUuid))
    }

    override fun pagedArchived(): Flow<PagingData<ExerciseDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::pagedArchived,
    ).flow
        .map { pagingData -> pagingData.map { it.toData() } }
        .flowOn(bgDispatcher)

    override fun observeArchivedCount(): Flow<Int> = dao.observeArchivedCount()
        .flowOn(bgDispatcher)

    override suspend fun countSessionsUsing(
        exerciseUuid: String,
    ): Int = withContext(bgDispatcher) {
        sessionDao.countFinishedContainingExercise(Uuid.parse(exerciseUuid))
    }

    override fun observeLinkedTrainingsCount(
        exerciseUuid: String,
    ): Flow<Int> = dao.observeLinkedTrainingsCount(Uuid.parse(exerciseUuid))
        .flowOn(bgDispatcher)

    override fun observeLastTrainedAt(
        exerciseUuid: String,
    ): Flow<Long?> = dao.observeLastTrainedAt(Uuid.parse(exerciseUuid))
        .flowOn(bgDispatcher)

    override suspend fun getLastTrainedExerciseUuid(): String? = withContext(bgDispatcher) {
        dao.getLastTrainedExerciseUuid()?.toString()
    }

    override suspend fun getRecentlyTrainedExercises(): List<RecentExerciseDataModel> =
        withContext(bgDispatcher) {
            dao.getRecentlyTrainedExercises().map { row ->
                RecentExerciseDataModel(
                    uuid = row.uuid.toString(),
                    name = row.name,
                    type = row.type.toData(),
                    lastFinishedAt = row.lastFinishedAt,
                )
            }
        }

    override suspend fun getRecentHistory(
        exerciseUuid: String,
        limit: Int,
    ): List<HistoryEntry> = transition {
        sessionDao
            .getRecentSessionsForExercise(Uuid.parse(exerciseUuid), limit)
            .asyncMap { row ->
                val sets = setDao.getByPerformedExercise(row.performedExerciseUuid)
                HistoryEntry(
                    sessionUuid = row.sessionUuid.toString(),
                    finishedAt = row.finishedAt,
                    trainingName = row.trainingName,
                    isAdhoc = row.isAdhoc,
                    sets = sets.map { entity -> entity.toSummary() },
                )
            }
    }

    override fun pagedActiveByTags(
        tagUuids: Set<String>,
    ): Flow<PagingData<ExerciseDataModel>> {
        if (tagUuids.isEmpty()) {
            return Pager(
                config = pagingConfig,
                pagingSourceFactory = dao::pagedActive,
            ).flow
                .map { pagingData -> pagingData.map { it.toData() } }
                .flowOn(bgDispatcher)
        }
        val parsed = tagUuids.map(Uuid::parse)
        // OR semantics: include the exercise when it has ANY of the selected tags.
        return Pager(
            config = pagingConfig,
            pagingSourceFactory = { dao.pagedActiveByTags(parsed) },
        ).flow
            .map { pagingData -> pagingData.map { it.toData() } }
            .flowOn(bgDispatcher)
    }

    override fun pagedActiveWithStats(
        filterTagUuids: Set<String>,
    ): Flow<PagingData<ExerciseListItem>> {
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
                // Tag names are denormalized per row; acceptable at a page size of 10.
                pagingData.map { row ->
                    row.toData(
                        tags = exerciseTagDao.getTagNames(row.exercise.uuid),
                    )
                }
            }
            .flowOn(bgDispatcher)
    }

    override suspend fun bulkArchive(
        uuids: Set<String>,
    ): BulkArchiveOutcome = transition.mutate {
        if (uuids.isEmpty()) return@mutate BulkArchiveOutcome(0, emptyList())
        val parsed = uuids.map(Uuid::parse)
        val (allowed, blocked) = parsed.partition { uuid ->
            trainingExerciseDao.countActiveTemplatesUsing(uuid) == 0
        }
        // Same query the detail-screen archive uses, so both surfaces name the blockers alike.
        val blockedExercises = blocked.mapNotNull { uuid ->
            val name = dao.getById(uuid)?.name ?: return@mapNotNull null
            BulkArchiveOutcome.BlockedExercise(
                name = name,
                activeTrainings = trainingExerciseDao.getActiveTemplateNamesUsing(uuid),
            )
        }
        if (allowed.isNotEmpty()) {
            val now = Clock.System.now().toEpochMilliseconds()
            allowed.forEach { dao.archive(it, now) }
        }
        BulkArchiveOutcome(archivedCount = allowed.size, blocked = blockedExercises)
    }

    override suspend fun bulkPermanentDelete(uuids: Set<String>) {
        transition.mutate {
            val parsed = uuids.map(Uuid::parse)
            val paths = parsed.asyncMapNotNull { dao.getById(it)?.imagePath }
            parsed.asyncForEach { dao.permanentDelete(it) }
            paths.asyncForEach { imageStorage.deleteImage(it) }
        }
    }

    override suspend fun canBulkPermanentDelete(
        uuids: Set<String>,
    ): Boolean = withContext(bgDispatcher) {
        if (uuids.isEmpty()) return@withContext false
        uuids.all { uuid -> canPermanentlyDeleteImmediately(uuid) }
    }

    private suspend fun loadLabels(exerciseUuid: Uuid): List<String> =
        exerciseTagDao.getTagNames(exerciseUuid)

    private suspend fun syncLabels(exerciseUuid: Uuid, labels: List<String>) {
        if (labels.isEmpty()) {
            exerciseTagDao.deleteByExercise(exerciseUuid)
            return
        }
        val deleteExerciseDeferred = asyncScope {
            exerciseTagDao.deleteByExercise(exerciseUuid)
        }
        val tagUuids = asyncScope {
            labels
                .map(String::trim)
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .asyncMap { name ->
                    val tagUuid = tagDao.findByName(name)?.uuid ?: TagEntity(name = name).also {
                        tagDao.insert(it)
                    }.uuid
                    ExerciseTagEntity(exerciseUuid = exerciseUuid, tagUuid = tagUuid)
                }
        }
        deleteExerciseDeferred.await()
        exerciseTagDao.insert(tagUuids.await())
    }

    /**
     * Portable UNIQUE-violation text shared by the host-test and production drivers. Keep it a
     * member — a file-private helper would need a synthetic accessor and lint objects.
     */
    private fun SQLiteException.isUniqueConstraintViolation(): Boolean =
        message.orEmpty().contains("UNIQUE constraint failed")

    companion object {

        private val pagingConfig = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false,
        )
    }
}
