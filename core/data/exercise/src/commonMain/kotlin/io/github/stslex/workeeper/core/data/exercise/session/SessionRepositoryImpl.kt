package io.github.stslex.workeeper.core.data.exercise.session

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.coroutine.asyncForEach
import io.github.stslex.workeeper.core.core.coroutine.asyncScope
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.data.database.session.HistoryByExerciseRow
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.training.TrainingDao
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel.Companion.toData
import io.github.stslex.workeeper.core.data.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetSummary
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType.Companion.toData
import io.github.stslex.workeeper.core.data.exercise.exercise.model.toData
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionProgressInfo
import io.github.stslex.workeeper.core.data.exercise.session.model.PerformedExerciseDetailDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDetailDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.toData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions", "LongParameterList")
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class SessionRepositoryImpl @Inject internal constructor(
    private val dao: SessionDao,
    private val performedExerciseDao: PerformedExerciseDao,
    private val setDao: SetDao,
    private val trainingDao: TrainingDao,
    private val exerciseDao: ExerciseDao,
    private val trainingExerciseDao: TrainingExerciseDao,
    private val transition: DbTransitionRunner,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : SessionRepository {

    override fun observeActive(): Flow<SessionDataModel?> = dao
        .observeActive()
        .flowOn(ioDispatcher)
        .map { entity -> entity?.toData() }

    override fun observeAnyActiveSession(): Flow<ActiveSessionInfo?> = dao
        .observeAnyActiveSession()
        .flowOn(ioDispatcher)
        .map { row ->
            row?.let {
                ActiveSessionInfo(
                    sessionUuid = it.uuid.toString(),
                    trainingUuid = it.trainingUuid.toString(),
                    startedAt = it.startedAt,
                )
            }
        }

    override fun observeActiveSessionWithStats(): Flow<SessionRepository.ActiveSessionWithStats?> =
        dao
            .observeActiveSessionWithStats()
            .flowOn(ioDispatcher)
            .map { row ->
                row?.let {
                    SessionRepository.ActiveSessionWithStats(
                        sessionUuid = it.uuid.toString(),
                        trainingUuid = it.trainingUuid.toString(),
                        trainingName = it.trainingName,
                        isAdhoc = it.isAdhoc,
                        startedAt = it.startedAt,
                        totalCount = it.totalCount,
                        doneCount = it.doneCount,
                    )
                }
            }

    override suspend fun getAnyActiveSession(): ActiveSessionInfo? = withContext(ioDispatcher) {
        dao.getActive()?.let { entity ->
            ActiveSessionInfo(
                sessionUuid = entity.uuid.toString(),
                trainingUuid = entity.trainingUuid.toString(),
                startedAt = entity.startedAt,
            )
        }
    }

    override suspend fun getActiveSessionProgress(): ActiveSessionProgressInfo? = transition {
        val session = dao.getActive() ?: return@transition null
        val performed = performedExerciseDao.getBySession(session.uuid)
        if (performed.isEmpty()) {
            return@transition ActiveSessionProgressInfo(
                sessionUuid = session.uuid.toString(),
                trainingUuid = session.trainingUuid.toString(),
                startedAt = session.startedAt,
                doneCount = 0,
                totalCount = 0,
            )
        }

        val exerciseUuids = performed.map { it.exerciseUuid }.distinct()
        val setsByPerformed = setDao
            .getByPerformedExercises(performed.map { it.uuid })
            .groupBy { it.performedExerciseUuid }
        val isAdhoc = trainingDao.getById(session.trainingUuid)?.isAdhoc == true
        val trainingPlans = if (isAdhoc) {
            emptyMap()
        } else {
            trainingExerciseDao
                .getPlanSetsBatch(session.trainingUuid, exerciseUuids)
                .associate { row ->
                    row.exerciseUuid to PlanSetsConverter.fromJson(row.planSets)
                }
        }
        val fallbackExerciseUuids = if (isAdhoc) {
            exerciseUuids
        } else {
            exerciseUuids.filter { trainingPlans[it] == null }
        }
        val fallbackPlans = if (fallbackExerciseUuids.isEmpty()) {
            emptyMap()
        } else {
            exerciseDao
                .getAdhocPlansBatch(fallbackExerciseUuids)
                .associate { row ->
                    row.uuid to PlanSetsConverter.fromJson(row.lastAdhocSets)
                }
        }
        val doneCount = performed.count { row ->
            if (row.skipped) return@count false
            val planSize = if (isAdhoc) {
                fallbackPlans[row.exerciseUuid]?.size ?: 0
            } else {
                (trainingPlans[row.exerciseUuid] ?: fallbackPlans[row.exerciseUuid])?.size ?: 0
            }
            val completedPositions = setsByPerformed[row.uuid]
                .orEmpty()
                .mapTo(mutableSetOf()) { it.position }
            val expectedPositions = buildSet {
                addAll(0 until planSize)
                addAll(completedPositions)
            }
            expectedPositions.isNotEmpty() && expectedPositions.all(completedPositions::contains)
        }
        ActiveSessionProgressInfo(
            sessionUuid = session.uuid.toString(),
            trainingUuid = session.trainingUuid.toString(),
            startedAt = session.startedAt,
            doneCount = doneCount,
            totalCount = performed.size,
        )
    }

    override suspend fun getActive(): SessionDataModel? = withContext(ioDispatcher) {
        dao.getActive()?.toData()
    }

    override fun pagedRecentWithStats(): Flow<PagingData<RecentSessionDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::pagedRecentWithStats,
    ).flow
        .flowOn(ioDispatcher)
        .map { pagingData -> pagingData.map { it.toData() } }

    override fun observeFinishedTimesBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<Long>> = dao
        .observeFinishedTimesBetween(startInclusive, endExclusive)
        .flowOn(ioDispatcher)

    override fun observeLastFinishedSession(): Flow<SessionRepository.LastFinishedSession?> = dao
        .observeLastFinishedSession()
        .map { row ->
            row?.let {
                SessionRepository.LastFinishedSession(
                    sessionUuid = it.sessionUuid.toString(),
                    finishedAt = it.finishedAt,
                    trainingName = it.trainingName,
                    isAdhoc = it.isAdhoc,
                )
            }
        }
        .flowOn(ioDispatcher)

    override suspend fun getSessionDetail(
        sessionUuid: String,
    ): SessionDetailDataModel? = transition {
        val sessionId = Uuid.parse(sessionUuid)
        val performedDeferred = async {
            performedExerciseDao.getBySession(sessionId).sortedBy { it.position }
        }
        val session = dao.getById(sessionId) ?: run {
            performedDeferred.cancel()
            return@transition null
        }
        val finishedAt = session.finishedAt ?: run {
            performedDeferred.cancel()
            return@transition null
        }

        val trainingDeferred = async { trainingDao.getById(session.trainingUuid) }
        val performed = performedDeferred.await()
        val performedUuids = performed.map { it.uuid }
        val exerciseUuids = performed.map { it.exerciseUuid }.distinct()

        val exerciseByUuidDeferred = async {
            exerciseDao.getByUuids(exerciseUuids).associateBy { it.uuid }
        }
        val setsByPerformedDeferred = async {
            setDao.getByPerformedExercises(performedUuids)
                .groupBy { it.performedExerciseUuid }
        }

        val training = trainingDeferred.await() ?: return@transition null
        val exerciseByUuid = exerciseByUuidDeferred.await()
        val setsByPerformed = setsByPerformedDeferred.await()

        val exercises = performed.map { row ->
            PerformedExerciseDetailDataModel(
                performedExerciseUuid = row.uuid.toString(),
                exerciseUuid = row.exerciseUuid.toString(),
                exerciseName = exerciseByUuid[row.exerciseUuid]?.name.orEmpty(),
                exerciseType = exerciseByUuid[row.exerciseUuid]?.type?.toData()
                    ?: ExerciseTypeDataModel.WEIGHTED,
                position = row.position,
                skipped = row.skipped,
                sets = setsByPerformed[row.uuid].orEmpty()
                    .sortedBy { it.position }
                    .map { it.toData() },
            )
        }

        SessionDetailDataModel(
            sessionUuid = session.uuid.toString(),
            trainingUuid = session.trainingUuid.toString(),
            trainingName = training.name,
            isAdhoc = training.isAdhoc,
            startedAt = session.startedAt,
            finishedAt = finishedAt,
            exercises = exercises,
        )
    }

    override fun pagedFinished(): Flow<PagingData<SessionDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::pagedFinished,
    ).flow
        .flowOn(ioDispatcher)
        .map { pagingData -> pagingData.map { it.toData() } }

    override fun pagedFinishedByTraining(
        trainingUuid: String,
    ): Flow<PagingData<SessionDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = { dao.pagedFinishedByTraining(Uuid.parse(trainingUuid)) },
    ).flow
        .flowOn(ioDispatcher)
        .map { pagingData -> pagingData.map { it.toData() } }

    override suspend fun getRecentFinishedByTraining(
        trainingUuid: String,
        limit: Int,
    ): List<SessionDataModel> = withContext(ioDispatcher) {
        dao.getRecentFinishedByTraining(Uuid.parse(trainingUuid), limit).map { it.toData() }
    }

    override suspend fun getById(uuid: String): SessionDataModel? = withContext(ioDispatcher) {
        dao.getById(Uuid.parse(uuid))?.toData()
    }

    override suspend fun startSession(
        trainingUuid: String,
    ): SessionDataModel = withContext(ioDispatcher) {
        val entity = SessionEntity(
            trainingUuid = Uuid.parse(trainingUuid),
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = Clock.System.now().toEpochMilliseconds(),
            finishedAt = null,
        )
        dao.insert(entity)
        entity.toData()
    }

    override suspend fun startSessionWithExercises(
        trainingUuid: String,
        exerciseUuids: List<Pair<String, Int>>,
    ): SessionDataModel = withContext(ioDispatcher) {
        val session = SessionEntity(
            trainingUuid = Uuid.parse(trainingUuid),
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = Clock.System.now().toEpochMilliseconds(),
            finishedAt = null,
        )
        val performed = exerciseUuids.map { (exerciseUuid, position) ->
            PerformedExerciseEntity(
                sessionUuid = session.uuid,
                exerciseUuid = Uuid.parse(exerciseUuid),
                position = position,
                skipped = false,
            )
        }
        dao.startSessionWithExercises(session, performed)
        session.toData()
    }

    override suspend fun resumeSession(
        sessionUuid: String,
    ): SessionDataModel? = withContext(ioDispatcher) {
        dao.getById(Uuid.parse(sessionUuid))?.takeIf { it.state == SessionStateEntity.IN_PROGRESS }
            ?.toData()
    }

    override suspend fun finishSession(sessionUuid: String, finishedAt: Long) {
        withContext(ioDispatcher) {
            val current = dao.getById(Uuid.parse(sessionUuid)) ?: return@withContext
            dao.update(
                current.copy(
                    state = SessionStateEntity.FINISHED,
                    finishedAt = finishedAt,
                ),
            )
        }
    }

    override suspend fun finishSessionAtomic(
        sessionUuid: String,
        finishedAt: Long,
        planUpdates: List<PlanUpdate>,
        newTrainingName: String?,
        discardedSetUuids: List<String>,
    ): Boolean = transition {
        val current = dao.getById(Uuid.parse(sessionUuid))
            ?: return@transition false
        // GUARD: discard unfilled sets inside this transaction — the caller treats a false or
        // throw as "session still active", so a rollback has to put them back.
        discardedSetUuids.forEach { setUuid -> setDao.delete(Uuid.parse(setUuid)) }
        // The optional rename shares the finish's transaction, so no half-renamed training leaks.
        if (newTrainingName != null) {
            trainingDao.updateName(current.trainingUuid, newTrainingName)
        }
        planUpdates.asyncForEach { update ->
            val planJson = PlanSetsConverter.toJson(update.newPlan)
            if (update.isAdhoc) {
                exerciseDao.updateLastAdhocSets(
                    uuid = Uuid.parse(update.exerciseUuid),
                    lastAdhocSets = planJson,
                )
            } else {
                trainingExerciseDao.updatePlanSets(
                    trainingUuid = Uuid.parse(update.trainingUuid),
                    exerciseUuid = Uuid.parse(update.exerciseUuid),
                    planSets = planJson,
                )
            }
        }
        // On finish the training and its performed exercises graduate, in this transaction.
        val exerciseAdhoc = asyncScope {
            exerciseDao.graduateAdhocForSession(current.uuid)
        }
        val trainingGraduate = asyncScope {
            trainingDao.graduateTraining(current.trainingUuid)
        }
        val sessionGraduate = asyncScope {
            dao.update(
                current.copy(
                    state = SessionStateEntity.FINISHED,
                    finishedAt = finishedAt,
                ),
            )
        }
        exerciseAdhoc.await()
        trainingGraduate.await()
        sessionGraduate.await()
        true
    }

    override suspend fun deleteSession(uuid: String) {
        withContext(ioDispatcher) {
            dao.delete(Uuid.parse(uuid))
        }
    }

    override suspend fun createAdhocSession(
        name: String,
        exerciseUuids: List<String>,
    ): SessionRepository.AdhocSessionResult = transition {
        val now = Clock.System.now().toEpochMilliseconds()
        val training = TrainingEntity(
            name = name,
            description = null,
            isAdhoc = true,
            archived = false,
            createdAt = now,
            archivedAt = null,
        )
        val session = SessionEntity(
            trainingUuid = training.uuid,
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = now,
            finishedAt = null,
        )
        val planRows = exerciseUuids.mapIndexed { index, exerciseUuid ->
            TrainingExerciseEntity(
                trainingUuid = training.uuid,
                exerciseUuid = Uuid.parse(exerciseUuid),
                position = index,
                planSets = null,
            )
        }
        val performedRows = exerciseUuids.mapIndexed { index, exerciseUuid ->
            PerformedExerciseEntity(
                sessionUuid = session.uuid,
                exerciseUuid = Uuid.parse(exerciseUuid),
                position = index,
                skipped = false,
            )
        }
        trainingDao.insert(training)
        if (planRows.isNotEmpty()) {
            trainingExerciseDao.insert(planRows)
        }
        dao.startSessionWithExercises(session, performedRows)
        SessionRepository.AdhocSessionResult(
            sessionUuid = session.uuid.toString(),
            trainingUuid = training.uuid.toString(),
        )
    }

    override suspend fun addExerciseToActiveSession(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
        attachToPlan: Boolean,
    ): SessionRepository.AddExerciseResult = transition {
        val sessionId = Uuid.parse(sessionUuid)
        val trainingId = Uuid.parse(trainingUuid)
        val exerciseId = Uuid.parse(exerciseUuid)
        // Seed plan_sets from last_adhoc_sets as the baseline; null renders as an empty plan.
        val initialPlanJson = exerciseDao.getById(exerciseId)?.lastAdhocSets
        val parsedPlan = PlanSetsConverter.fromJson(initialPlanJson)
        val nextPerformedPosition =
            (performedExerciseDao.getMaxPosition(sessionId) ?: -1) + 1
        // Skipping this insert IS the one-off encoding. See v3-redesign-spec.md §6.2.
        if (attachToPlan) {
            val nextPlanPosition = (trainingExerciseDao.getMaxPosition(trainingId) ?: -1) + 1
            trainingExerciseDao.insert(
                TrainingExerciseEntity(
                    trainingUuid = trainingId,
                    exerciseUuid = exerciseId,
                    position = nextPlanPosition,
                    planSets = initialPlanJson,
                ),
            )
        }
        val performed = PerformedExerciseEntity(
            sessionUuid = sessionId,
            exerciseUuid = exerciseId,
            position = nextPerformedPosition,
            skipped = false,
        )
        performedExerciseDao.insert(performed)
        SessionRepository.AddExerciseResult(
            performedExerciseUuid = performed.uuid.toString(),
            planSets = parsedPlan,
            isPlanAttached = attachToPlan,
        )
    }

    override suspend fun removeExerciseFromSession(
        performedExerciseUuid: String,
        exerciseUuid: String,
        trainingUuid: String?,
        removeFromPlan: Boolean,
    ) {
        transition {
            val performedId = Uuid.parse(performedExerciseUuid)
            val exerciseId = Uuid.parse(exerciseUuid)
            // GUARD: sets → performed row → plan row → orphan check. The orphan predicate reads
            // performed_exercise_table, so the performed row must already be gone.
            setDao.deleteAllForPerformedExercise(performedId)
            performedExerciseDao.deleteByUuid(performedId)
            if (removeFromPlan && trainingUuid != null) {
                trainingExerciseDao.deleteByTrainingAndExercise(
                    trainingUuid = Uuid.parse(trainingUuid),
                    exerciseUuid = exerciseId,
                )
            }
            exerciseDao.deleteIfAdhocOrphan(exerciseId)
        }
    }

    override suspend fun discardAdhocSession(sessionUuid: String, trainingUuid: String) {
        transition {
            val trainingId = Uuid.parse(trainingUuid)
            // Rows must be `is_adhoc = 1` AND joined to this session via performed_exercise_table:
            // library picks survive, one-offs with no plan row are still cleaned up.
            val adhocExerciseUuids = exerciseDao
                .getAdhocExercisesForSession(Uuid.parse(sessionUuid))
                .map { it.uuid }
            // FK cascades cover the performed/set/plan rows; only ad-hoc exercises need cleanup.
            dao.delete(Uuid.parse(sessionUuid))
            trainingDao.permanentDelete(trainingId)
            if (adhocExerciseUuids.isNotEmpty()) {
                exerciseDao.deleteByUuids(adhocExerciseUuids)
            }
        }
    }

    override fun pagedHistoryByExercise(
        exerciseUuid: String,
    ): Flow<PagingData<HistoryEntry>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = { dao.pagedHistoryByExercise(Uuid.parse(exerciseUuid)) },
    ).flow
        .map { pagingData -> pagingData.map { row -> row.toSingleEntry() } }
        .flowOn(ioDispatcher)

    override suspend fun getHistoryByExercise(
        exerciseUuid: String,
    ): List<HistoryEntry> = withContext(ioDispatcher) {
        dao.getHistoryByExercise(Uuid.parse(exerciseUuid)).groupBySession()
    }

    // Order is established by DAO query — see SessionDao.getHistoryByExercise
    private fun List<HistoryByExerciseRow>.groupBySession(): List<HistoryEntry> = this
        .groupBy { it.sessionUuid }
        .map { (_, rows) ->
            val first = rows.first()
            HistoryEntry(
                sessionUuid = first.sessionUuid.toString(),
                finishedAt = first.finishedAt,
                trainingName = first.trainingName,
                isAdhoc = first.isAdhoc,
                sets = rows.map { row ->
                    SetSummary(row.weight, row.reps, row.setType.toData())
                },
            )
        }

    /**
     * The PagingSource emits one row per (session, set), so each entry carries one set.
     * Consumers needing multi-set summaries per session use [getHistoryByExercise].
     */
    private fun HistoryByExerciseRow.toSingleEntry(): HistoryEntry = HistoryEntry(
        sessionUuid = sessionUuid.toString(),
        finishedAt = finishedAt,
        trainingName = trainingName,
        isAdhoc = isAdhoc,
        sets = listOf(
            SetSummary(
                weight = weight,
                reps = reps,
                type = setType.toData(),
            ),
        ),
    )

    companion object {

        private val pagingConfig = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
        )
    }
}
