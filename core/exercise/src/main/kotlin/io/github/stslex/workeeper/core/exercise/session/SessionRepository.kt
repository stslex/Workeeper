package io.github.stslex.workeeper.core.exercise.session

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.session.model.SessionDataModel
import kotlinx.coroutines.flow.Flow

interface SessionRepository {

    fun observeActive(): Flow<SessionDataModel?>

    suspend fun getActive(): SessionDataModel?

    fun observeRecent(limit: Int): Flow<List<SessionDataModel>>

    fun pagedFinished(): Flow<PagingData<SessionDataModel>>

    fun pagedFinishedByTraining(trainingUuid: String): Flow<PagingData<SessionDataModel>>

    suspend fun getById(uuid: String): SessionDataModel?

    suspend fun startSession(trainingUuid: String): SessionDataModel

    suspend fun resumeSession(sessionUuid: String): SessionDataModel?

    suspend fun finishSession(sessionUuid: String, finishedAt: Long)

    suspend fun deleteSession(uuid: String)
}
