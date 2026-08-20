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
        // performed doesn't depend on session — start in parallel with session fetch.
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

        // Past this point session is valid — fan out the remaining reads in parallel.
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
        // Unfilled sets are discarded as part of the finish (v3 §6.1), inside this
        // transaction. A rollback anywhere below must put them back — the caller treats a
        // false/throw as "session still active", and rows deleted outside would be gone with
        // no finish to justify them.
        discardedSetUuids.forEach { setUuid -> setDao.delete(Uuid.parse(setUuid)) }
        // Pair the optional rename with the finish: same transaction, single Room batch.
        // A throw at any point in this block rolls back the rename, plan updates,
        // graduation, and state flip together — no half-finished named training can leak.
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
        // Adhoc lifecycle (v2.3): on finish, the training row and every exercise
        // plan-attached to it graduate to regular library entries. Runs inside the same
        // transaction as the state flip so a failed finish does not leak half-graduated rows.
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
        // Seed plan_sets from the exercise's last_adhoc_sets so picking a library row with
        // history surfaces the user's last-logged sets as a baseline. Null when there's no
        // history (fresh inline-created exercise) — caller renders an empty plan.
        val initialPlanJson = exerciseDao.getById(exerciseId)?.lastAdhocSets
        val parsedPlan = PlanSetsConverter.fromJson(initialPlanJson)
        val nextPerformedPosition =
            (performedExerciseDao.getMaxPosition(sessionId) ?: -1) + 1
        // The plan-attached fork (v3 §6.2). Skipping this insert is the entire encoding of
        // "one-off": absence of the row, no column, no migration. The performed row below is
        // written unconditionally — a one-off is real work and counts toward progress.
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
            // Order matters: sets → performed row → plan row → orphan check. The orphan
            // predicate reads performed_exercise_table, so the performed row must be gone
            // before it runs or an only-session inline exercise would survive as a stranded
            // is_adhoc = 1 row.
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
            // Defence-in-depth predicate: rows must be `is_adhoc = 1` AND joined via the
            // session being discarded. Library exercises picked into the session have
            // `is_adhoc = 0` and so are filtered out at the join step. The join runs through
            // `performed_exercise_table` so one-off (non-plan-attached) inline exercises are
            // cleaned up too — they have no plan row to be found by.
            val adhocExerciseUuids = exerciseDao
                .getAdhocExercisesForSession(Uuid.parse(sessionUuid))
                .map { it.uuid }
            // session_table cascades performed_exercise_table + set_table via FK on
            // session_uuid; training_table cascades training_exercise_table via FK on
            // training_uuid. Only the ad-hoc exercise rows need explicit cleanup.
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
     * The PagingSource emits one row per (session, set). For chart-style consumers a
     * single-set entry is enough; the recent-history grid uses [getHistoryByExercise]
     * (one-shot, grouped) to render multi-set summaries per session.
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
