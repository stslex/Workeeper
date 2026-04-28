package io.github.stslex.workeeper.core.exercise.exercise

import android.database.sqlite.SQLiteConstraintException
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.database.session.SetDao
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.database.tag.ExerciseTagEntity
import io.github.stslex.workeeper.core.database.tag.TagDao
import io.github.stslex.workeeper.core.database.tag.TagEntity
import io.github.stslex.workeeper.core.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository.BulkArchiveOutcome
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository.SaveResult
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.exercise.exercise.model.toData
import io.github.stslex.workeeper.core.exercise.exercise.model.toEntity
import io.github.stslex.workeeper.core.exercise.exercise.model.toSummary
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

    // v3 lacks date-range queries; this legacy overload currently returns no results.
    override suspend fun getExercises(
        name: String,
        startDate: Long,
        endDate: Long,
    ): List<ExerciseDataModel> = emptyList()

    override suspend fun saveItem(item: ExerciseChangeDataModel): SaveResult = withContext(bgDispatcher) {
        val entity = item.toEntity()
        try {
            if (item.uuid.toString().isBlank()) {
                dao.insert(entity)
            } else {
                val existing = dao.getById(entity.uuid)
                if (existing == null) dao.insert(entity) else dao.update(entity)
            }
        } catch (_: SQLiteConstraintException) {
            return@withContext SaveResult.DuplicateName
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
        // The clear is idempotent (already cleared = no-op), so a partial-failure
        // recovery is safe to retry. Avoiding `withTransaction` keeps this module free
        // of the room-ktx dep that core/database does not transitively expose.
        withContext(bgDispatcher) {
            val parsed = Uuid.parse(exerciseUuid)
            val exercise = dao.getById(parsed) ?: return@withContext
            val adhoc = PlanSetsConverter.fromJson(exercise.lastAdhocSets)
            if (adhoc != null) {
                val cleared = adhoc.map { it.copy(weight = null) }
                dao.updateLastAdhocSets(parsed, PlanSetsConverter.toJson(cleared))
            }
            trainingExerciseDao.getAllForExercise(parsed).forEach { row ->
                val parsedPlan = PlanSetsConverter.fromJson(row.planSets) ?: return@forEach
                val cleared = parsedPlan.map { it.copy(weight = null) }
                trainingExerciseDao.updatePlanSets(
                    trainingUuid = row.trainingUuid,
                    exerciseUuid = row.exerciseUuid,
                    planSets = PlanSetsConverter.toJson(cleared),
                )
            }
        }
    }

    override suspend fun deleteItem(uuid: String) {
        withContext(bgDispatcher) {
            // Read imagePath BEFORE delete — once the row is gone, we lose the path.
            val imagePath = dao.getById(Uuid.parse(uuid))?.imagePath
            dao.permanentDelete(Uuid.parse(uuid))
            imagePath?.let { imageStorage.deleteImage(it) }
        }
    }

    // v3 has no autocomplete query; return empty until feature redesign.
    override suspend fun searchItemsWithExclude(query: String): List<ExerciseDataModel> = emptyList()

    override suspend fun searchActiveExercises(
        query: String,
        excludeUuids: Set<String>,
    ): List<ExerciseDataModel> = withContext(bgDispatcher) {
        val excluded = excludeUuids.map(Uuid::parse).toSet()
        dao.getAllActive()
            .filter { entity -> entity.uuid !in excluded }
            .filter { entity ->
                query.isBlank() || entity.name.contains(query, ignoreCase = true)
            }
            .map { it.toData() }
    }

    override suspend fun deleteAllItems(uuids: List<Uuid>) {
        withContext(bgDispatcher) {
            // Snapshot paths before deleting rows so we can clean image files after.
            val paths = uuids.mapNotNull { dao.getById(it)?.imagePath }
            uuids.forEach { dao.permanentDelete(it) }
            paths.forEach { imageStorage.deleteImage(it) }
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
    ): Boolean = withContext(bgDispatcher) {
        val parsed = Uuid.parse(uuid)
        sessionDao.countFinishedContainingExercise(parsed) == 0 &&
            trainingExerciseDao.countActiveTemplatesUsing(parsed) == 0
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

    override suspend fun getRecentHistory(
        exerciseUuid: String,
        limit: Int,
    ): List<HistoryEntry> = withContext(bgDispatcher) {
        sessionDao
            .getRecentSessionsForExercise(Uuid.parse(exerciseUuid), limit)
            .map { row ->
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
    ): BulkArchiveOutcome = withContext(bgDispatcher) {
        if (uuids.isEmpty()) return@withContext BulkArchiveOutcome(0, emptyList())
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
        withContext(bgDispatcher) {
            val parsed = uuids.map(Uuid::parse)
            val paths = parsed.mapNotNull { dao.getById(it)?.imagePath }
            parsed.forEach { dao.permanentDelete(it) }
            paths.forEach { imageStorage.deleteImage(it) }
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
