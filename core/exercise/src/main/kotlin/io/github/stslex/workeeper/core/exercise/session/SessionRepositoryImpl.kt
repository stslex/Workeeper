package io.github.stslex.workeeper.core.exercise.session

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.database.common.DbTransition
import io.github.stslex.workeeper.core.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.session.HistoryByExerciseRow
import io.github.stslex.workeeper.core.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.database.session.SessionEntity
import io.github.stslex.workeeper.core.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.database.session.SetDao
import io.github.stslex.workeeper.core.database.training.TrainingDao
import io.github.stslex.workeeper.core.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel.Companion.toData
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.exercise.exercise.model.SetSummary
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType.Companion.toData
import io.github.stslex.workeeper.core.exercise.exercise.model.toData
import io.github.stslex.workeeper.core.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.exercise.session.model.PerformedExerciseDetailDataModel
import io.github.stslex.workeeper.core.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionDetailDataModel
import io.github.stslex.workeeper.core.exercise.session.model.toData
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
internal class SessionRepositoryImpl @Inject constructor(
    private val dao: SessionDao,
    private val performedExerciseDao: PerformedExerciseDao,
    private val setDao: SetDao,
    private val trainingDao: TrainingDao,
    private val exerciseDao: ExerciseDao,
    private val trainingExerciseDao: TrainingExerciseDao,
    private val transition: DbTransition,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : SessionRepository {

    override fun observeActive(): Flow<SessionDataModel?> = dao
        .observeActive()
        .map { entity -> entity?.toData() }
        .flowOn(ioDispatcher)

    override fun observeAnyActiveSession(): Flow<ActiveSessionInfo?> = dao
        .observeAnyActiveSession()
        .map { row ->
            row?.let {
                ActiveSessionInfo(
                    sessionUuid = it.uuid.toString(),
                    trainingUuid = it.trainingUuid.toString(),
                    startedAt = it.startedAt,
                )
            }
        }
        .flowOn(ioDispatcher)

    override fun observeActiveSessionWithStats(): Flow<SessionRepository.ActiveSessionWithStats?> =
        dao
            .observeActiveSessionWithStats()
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
            .flowOn(ioDispatcher)

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

    override fun observeRecent(limit: Int): Flow<List<SessionDataModel>> = dao
        .observeRecent(limit)
        .map { list -> list.map { it.toData() } }
        .flowOn(ioDispatcher)

    override fun observeRecentWithStats(
        limit: Int,
    ): Flow<List<RecentSessionDataModel>> = dao
        .observeRecentWithStats(limit)
        .map { rows -> rows.map { it.toData() } }
        .flowOn(ioDispatcher)

    override suspend fun getSessionDetail(
        sessionUuid: String,
    ): SessionDetailDataModel? = transition {
        val sessionId = Uuid.parse(sessionUuid)
        val session = dao.getById(sessionId) ?: return@transition null
        val finishedAt = session.finishedAt ?: return@transition null
        val training = trainingDao.getById(session.trainingUuid) ?: return@transition null
        val performed = performedExerciseDao
            .getBySession(sessionId)
            .sortedBy { it.position }
        val exerciseUuids = performed.map { it.exerciseUuid }.distinct()
        val exerciseByUuid = exerciseDao
            .getByUuids(exerciseUuids)
            .associateBy { it.uuid }
        val exercises = performed.map { row ->
            PerformedExerciseDetailDataModel(
                performedExerciseUuid = row.uuid.toString(),
                exerciseUuid = row.exerciseUuid.toString(),
                exerciseName = exerciseByUuid[row.exerciseUuid]?.name.orEmpty(),
                exerciseType = exerciseByUuid[row.exerciseUuid]?.type?.toData()
                    ?: ExerciseTypeDataModel.WEIGHTED,
                position = row.position,
                skipped = row.skipped,
                sets = setDao
                    .getByPerformedExercise(row.uuid)
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
        .map { pagingData -> pagingData.map { it.toData() } }
        .flowOn(ioDispatcher)

    override fun pagedFinishedByTraining(
        trainingUuid: String,
    ): Flow<PagingData<SessionDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = { dao.pagedFinishedByTraining(Uuid.parse(trainingUuid)) },
    ).flow
        .map { pagingData -> pagingData.map { it.toData() } }
        .flowOn(ioDispatcher)

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
            startedAt = System.currentTimeMillis(),
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
            startedAt = System.currentTimeMillis(),
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
    ): Boolean = withContext(ioDispatcher) {
        transition {
            val current = dao.getById(Uuid.parse(sessionUuid))
                ?: return@transition false
            planUpdates.forEach { update ->
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
            dao.update(
                current.copy(
                    state = SessionStateEntity.FINISHED,
                    finishedAt = finishedAt,
                ),
            )
            true
        }
    }

    override suspend fun deleteSession(uuid: String) {
        withContext(ioDispatcher) {
            dao.delete(Uuid.parse(uuid))
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

    private fun List<HistoryByExerciseRow>.groupBySession(): List<HistoryEntry> = this
        .groupBy { it.sessionUuid }
        .map { (_, rows) ->
            val first = rows.first()
            HistoryEntry(
                sessionUuid = first.sessionUuid.toString(),
                finishedAt = first.finishedAt,
                trainingName = first.trainingName,
                isAdhoc = first.isAdhoc,
                sets = rows
                    .sortedBy { it.position }
                    .map { row ->
                        SetSummary(
                            weight = row.weight,
                            reps = row.reps,
                            type = row.setType.toData(),
                        )
                    },
            )
        }
        .sortedByDescending { it.finishedAt }

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
