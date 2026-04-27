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

    /**
     * Hot stream that powers the Home active-session banner. Carries the training name +
     * `isAdhoc` flag plus the done/total exercise counts so the banner can render without
     * an extra round trip per session. `doneCount` is heuristic — an exercise counts as
     * done when it has at least one logged set.
     */
    fun observeActiveSessionWithStats(): Flow<ActiveSessionWithStats?>

    suspend fun getAnyActiveSession(): ActiveSessionInfo?

    suspend fun getActive(): SessionDataModel?

    fun observeRecent(limit: Int): Flow<List<SessionDataModel>>

    fun pagedFinished(): Flow<PagingData<SessionDataModel>>

    fun pagedFinishedByTraining(trainingUuid: String): Flow<PagingData<SessionDataModel>>

    suspend fun getRecentFinishedByTraining(trainingUuid: String, limit: Int): List<SessionDataModel>

    suspend fun getById(uuid: String): SessionDataModel?

    suspend fun startSession(trainingUuid: String): SessionDataModel

    /**
     * Atomically creates a new IN_PROGRESS session for [trainingUuid] and seeds one
     * performed_exercise row per `(exerciseUuid, position)` pair. Used by Live workout to
     * spin up a session from a training in a single transaction.
     */
    suspend fun startSessionWithExercises(
        trainingUuid: String,
        exerciseUuids: List<Pair<String, Int>>,
    ): SessionDataModel

    suspend fun resumeSession(sessionUuid: String): SessionDataModel?

    suspend fun finishSession(sessionUuid: String, finishedAt: Long)

    suspend fun deleteSession(uuid: String)

    data class ActiveSessionWithStats(
        val sessionUuid: String,
        val trainingUuid: String,
        val trainingName: String,
        val isAdhoc: Boolean,
        val startedAt: Long,
        val totalCount: Int,
        val doneCount: Int,
    )
}
