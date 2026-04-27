package io.github.stslex.workeeper.core.exercise.session

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.exercise.session.model.SessionDataModel
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface SessionRepository {

    fun observeActive(): Flow<SessionDataModel?>

    /**
     * Hot stream of "anything in progress?" used by Trainings tab + Training detail to
     * mark the active row and gate Start-session against another running session. Lighter
     * than [observeActive] — only loads the projection columns the UI needs.
     */
    fun observeAnyActiveSession(): Flow<ActiveSessionInfo?>

    suspend fun getAnyActiveSession(): ActiveSessionInfo?

    suspend fun getActive(): SessionDataModel?

    fun observeRecent(limit: Int): Flow<List<SessionDataModel>>

    fun pagedFinished(): Flow<PagingData<SessionDataModel>>

    fun pagedFinishedByTraining(trainingUuid: String): Flow<PagingData<SessionDataModel>>

    suspend fun getRecentFinishedByTraining(trainingUuid: String, limit: Int): List<SessionDataModel>

    suspend fun getById(uuid: String): SessionDataModel?

    suspend fun startSession(trainingUuid: String): SessionDataModel

    suspend fun resumeSession(sessionUuid: String): SessionDataModel?

    suspend fun finishSession(sessionUuid: String, finishedAt: Long)

    suspend fun deleteSession(uuid: String)
}
