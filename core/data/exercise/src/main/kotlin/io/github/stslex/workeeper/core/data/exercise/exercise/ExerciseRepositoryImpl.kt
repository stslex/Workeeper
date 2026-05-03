package io.github.stslex.workeeper.core.data.exercise.exercise

import android.database.sqlite.SQLiteConstraintException
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.coroutine.asyncForEach
import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.core.coroutine.asyncMapNotNull
import io.github.stslex.workeeper.core.core.coroutine.asyncScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.images.ImageStorage
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
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel.Companion.toData
import io.github.stslex.workeeper.core.data.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.data.exercise.exercise.model.RecentExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.toData
import io.github.stslex.workeeper.core.data.exercise.exercise.model.toEntity
import io.github.stslex.workeeper.core.data.exercise.exercise.model.toSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions", "LongParameterList")
@Singleton
internal class ExerciseRepositoryImpl @Inject constructor(
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
    ): SaveResult = transition {
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
        } catch (_: SQLiteConstraintException) {
            return@transition SaveResult.DuplicateName
        }
        syncLabels(entity.uuid, item.labels)
        SaveResult.Success
    }

    override suspend fun getAdhocPlan(
        exerciseUuid: String,
    ): List<PlanSetDataModel>? = withContext(bgDispatcher) {
        val entity = dao.getById(Uuid.parse(exerciseUuid)) ?: return@withContext null
        PlanSetsConverter.fromJson(entity.lastAdhocSets)
    }

    override suspend fun createInlineAdhocExercise(
        name: String,
    ): InlineAdhocResult = transition {
        val trimmed = name.trim()
        // Case-insensitive lookup before insert so a user-typed name that already maps to a
        // library row (or a stray adhoc orphan) does not trip the UNIQUE(name) constraint.
        // Existing row is returned untouched — `is_adhoc` is intentionally not modified.
        val existing = dao.findByName(trimmed)
        if (existing != null) {
            return@transition InlineAdhocResult(
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
            createdAt = System.currentTimeMillis(),
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
    ) {
        withContext(bgDispatcher) {
            dao.updateLastAdhocSets(
                uuid = Uuid.parse(exerciseUuid),
                lastAdhocSets = PlanSetsConverter.toJson(planSets),
            )
        }
    }

    override suspend fun clearWeightsFromAllPlansForExercise(exerciseUuid: String) {
        transition {
            val parsed = Uuid.parse(exerciseUuid)
            val exercise = dao.getById(parsed) ?: return@transition
            val adhoc = PlanSetsConverter.fromJson(exercise.lastAdhocSets)
            val updateDeferred = runIfNotNull(adhoc) { adhocNotNull ->
                asyncScope {
                    val cleared = adhocNotNull.map { it.copy(weight = null) }
                    dao.updateLastAdhocSets(parsed, PlanSetsConverter.toJson(cleared))
                }
            }
            trainingExerciseDao.getAllForExercise(parsed).asyncForEach { row ->
                val parsedPlan = PlanSetsConverter.fromJson(row.planSets) ?: return@asyncForEach
                val cleared = parsedPlan.map { it.copy(weight = null) }
                trainingExerciseDao.updatePlanSets(
                    trainingUuid = row.trainingUuid,
                    exerciseUuid = row.exerciseUuid,
                    planSets = PlanSetsConverter.toJson(cleared),
                )
            }
            updateDeferred?.await()
        }
    }

    override suspend fun deleteItem(uuid: String) {
        transition {
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
            .filter { entity ->
                entity.uuid !in excluded &&
                    (query.isBlank() || entity.name.contains(query, ignoreCase = true))
            }
            .map { it.toData() }
    }

    override suspend fun deleteAllItems(uuids: List<Uuid>) {
        transition {
            // Snapshot paths before deleting rows so we can clean image files after.
            val paths = uuids.asyncMap { dao.getById(it)?.imagePath }
                .filterNotNull()

            uuids.asyncForEach { dao.permanentDelete(it) }

            paths.asyncForEach { imagePath ->
                imageStorage.deleteImage(imagePath)
            }
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
        transition {
            val imagePath = dao.getById(Uuid.parse(uuid))?.imagePath
            dao.permanentDelete(Uuid.parse(uuid))
            imagePath?.let { imageStorage.deleteImage(it) }
        }
    }

    override suspend fun canArchive(uuid: String): Boolean = withContext(bgDispatcher) {
        trainingExerciseDao.countActiveTemplatesUsing(Uuid.parse(uuid)) == 0
    }

    override suspend fun canPermanentlyDeleteImmediately(
        uuid: String,
    ): Boolean = transition {
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

    override suspend fun bulkArchive(
        uuids: Set<String>,
    ): BulkArchiveOutcome = transition {
        if (uuids.isEmpty()) return@transition BulkArchiveOutcome(0, emptyList())
        val parsed = uuids.map(Uuid::parse)
        val (allowed, blocked) = parsed.partition { uuid ->
            trainingExerciseDao.countActiveTemplatesUsing(uuid) == 0
        }
        val blockedNames = blocked.mapNotNull { dao.getById(it)?.name }
        if (allowed.isNotEmpty()) {
            val now = System.currentTimeMillis()
            allowed.forEach { dao.archive(it, now) }
        }
        BulkArchiveOutcome(archivedCount = allowed.size, blockedNames = blockedNames)
    }

    override suspend fun bulkPermanentDelete(uuids: Set<String>) {
        transition {
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
        transition {
            if (labels.isEmpty()) {
                exerciseTagDao.deleteByExercise(exerciseUuid)
                return@transition
            }
            val deleteExerciseDeferred = asyncScope {
                exerciseTagDao.deleteByExercise(exerciseUuid)
            }
            val tagUuids = labels
                .map(String::trim)
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .asyncMap { name ->
                    tagDao.findByName(name)?.uuid ?: TagEntity(name = name).also {
                        tagDao.insert(it)
                    }.uuid
                }
            deleteExerciseDeferred.await()
            exerciseTagDao.insert(
                tagUuids.map { tagUuid ->
                    ExerciseTagEntity(exerciseUuid = exerciseUuid, tagUuid = tagUuid)
                },
            )
        }
    }

    companion object {

        private val pagingConfig = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false,
        )
    }
}
