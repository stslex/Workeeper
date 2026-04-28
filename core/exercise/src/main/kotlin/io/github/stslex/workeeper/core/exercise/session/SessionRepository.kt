package io.github.stslex.workeeper.core.exercise.session

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionDetailDataModel
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

    /**
     * Hot stream of the most recent finished sessions (newest first), with the per-row
     * stats the Home recent list needs (training name, exercise count, set count).
     */
    fun observeRecentWithStats(limit: Int): Flow<List<RecentSessionDataModel>>

    /**
     * One-shot hierarchical fetch for the Past session detail screen. Returns the session
     * row plus all performed exercises and their sets. Returns `null` when the session
     * does not exist (e.g. it was deleted between navigation and load).
     */
    suspend fun getSessionDetail(sessionUuid: String): SessionDetailDataModel?

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

    /**
     * Atomically transitions [sessionUuid] to FINISHED at [finishedAt] and applies the
     * supplied [planUpdates] in a single SQL transaction. Replaces the manual `runCatching`
     * + compensating-rollback path: if any mutation throws, Room rolls back the whole batch
     * and the caller observes a thrown exception. Returns false when the session row is
     * missing (e.g. already deleted).
     */
    suspend fun finishSessionAtomic(
        sessionUuid: String,
        finishedAt: Long,
        planUpdates: List<PlanUpdate>,
    ): Boolean

    suspend fun deleteSession(uuid: String)

    /**
     * Date-ordered history for [exerciseUuid] across finished sessions. Each emitted entry
     * groups the rows of one finished session into a single record. Used by Exercise detail
     * recent history (v2.0) and the v2.2 charts.
     */
    fun pagedHistoryByExercise(exerciseUuid: String): Flow<PagingData<HistoryEntry>>

    /**
     * One-shot version of [pagedHistoryByExercise] for callers that need a bounded list
     * (Exercise detail recent block currently uses [SessionRepository] indirectly via
     * `getRecentSessionsForExercise`; this query backfills a sets-level surface for the v2.1
     * PR detector and v2.2 sparkline previews).
     */
    suspend fun getHistoryByExercise(exerciseUuid: String): List<HistoryEntry>

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
