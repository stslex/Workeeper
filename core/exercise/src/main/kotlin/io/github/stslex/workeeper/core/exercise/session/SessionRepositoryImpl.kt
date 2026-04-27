package io.github.stslex.workeeper.core.exercise.session

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.database.session.SessionEntity
import io.github.stslex.workeeper.core.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.exercise.session.model.toData
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
internal class SessionRepositoryImpl @Inject constructor(
    private val dao: SessionDao,
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

    override fun observeActiveSessionWithStats(): Flow<SessionRepository.ActiveSessionWithStats?> = dao
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
        dao.getById(Uuid.parse(sessionUuid))?.takeIf { it.state == SessionStateEntity.IN_PROGRESS }?.toData()
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

    override suspend fun deleteSession(uuid: String) {
        withContext(ioDispatcher) {
            dao.delete(Uuid.parse(uuid))
        }
    }

    companion object {

        private val pagingConfig = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
        )
    }
}
