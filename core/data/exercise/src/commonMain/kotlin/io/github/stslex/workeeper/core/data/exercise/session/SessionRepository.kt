package io.github.stslex.workeeper.core.data.exercise.session

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionProgressInfo
import io.github.stslex.workeeper.core.data.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDetailDataModel
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface SessionRepository {

    fun observeActive(): Flow<SessionDataModel?>

    /** Light "anything in progress?" projection; loads fewer columns than [observeActive]. */
    fun observeAnyActiveSession(): Flow<ActiveSessionInfo?>

    /**
     * Home active-session banner stream. GUARD: `doneCount` is heuristic — an exercise is done
     * once it has one logged set; [getActiveSessionProgress] is the strict count.
     */
    fun observeActiveSessionWithStats(): Flow<ActiveSessionWithStats?>

    suspend fun getAnyActiveSession(): ActiveSessionInfo?

    /**
     * Atomic snapshot of the active session and its strict completion: an exercise is done
     * only when every planned or performed set position is present.
     */
    suspend fun getActiveSessionProgress(): ActiveSessionProgressInfo?

    suspend fun getActive(): SessionDataModel?

    /** Paged finished sessions, newest first, with per-row stats for the Home recent list. */
    fun pagedRecentWithStats(): Flow<PagingData<RecentSessionDataModel>>

    /** Finish timestamps (epoch millis) in `[startInclusive, endExclusive)`; Home week readout. */
    fun observeFinishedTimesBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<Long>>

    /** The most recent finished session; null while none has ever finished. */
    fun observeLastFinishedSession(): Flow<LastFinishedSession?>

    /** The «Дни без тренировки» anchor: when the last finished session ended, and its name. */
    data class LastFinishedSession(
        val sessionUuid: String,
        val finishedAt: Long,
        val trainingName: String,
        val isAdhoc: Boolean,
    )

    /** Session row plus performed exercises and sets for Past session detail; null if deleted. */
    suspend fun getSessionDetail(sessionUuid: String): SessionDetailDataModel?

    fun pagedFinished(): Flow<PagingData<SessionDataModel>>

    fun pagedFinishedByTraining(trainingUuid: String): Flow<PagingData<SessionDataModel>>

    suspend fun getRecentFinishedByTraining(trainingUuid: String, limit: Int): List<SessionDataModel>

    suspend fun getById(uuid: String): SessionDataModel?

    suspend fun startSession(trainingUuid: String): SessionDataModel

    /** Atomically creates an IN_PROGRESS session for [trainingUuid] and its performed rows. */
    suspend fun startSessionWithExercises(
        trainingUuid: String,
        exerciseUuids: List<Pair<String, Int>>,
    ): SessionDataModel

    suspend fun resumeSession(sessionUuid: String): SessionDataModel?

    suspend fun finishSession(sessionUuid: String, finishedAt: Long)

    /**
     * Atomically finishes [sessionUuid] with [planUpdates] and an optional [newTrainingName];
     * false when the row is missing. GUARD: [discardedSetUuids] are deleted in this transaction.
     */
    suspend fun finishSessionAtomic(
        sessionUuid: String,
        finishedAt: Long,
        planUpdates: List<PlanUpdate>,
        newTrainingName: String? = null,
        discardedSetUuids: List<String> = emptyList(),
    ): Boolean

    suspend fun deleteSession(uuid: String)

    /**
     * Atomically creates an ad-hoc training + IN_PROGRESS session + one performed row per
     * [exerciseUuids] entry; an empty [exerciseUuids] is the "Start blank" Quick start case.
     */
    suspend fun createAdhocSession(
        name: String,
        exerciseUuids: List<String>,
    ): AdhocSessionResult

    /**
     * Attaches [exerciseUuid] to the active session; [attachToPlan] false writes no plan row
     * (a one-off). [AddExerciseResult.planSets] reads `exercise.last_adhoc_sets` on both paths.
     */
    suspend fun addExerciseToActiveSession(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
        attachToPlan: Boolean = true,
    ): AddExerciseResult

    data class AddExerciseResult(
        val performedExerciseUuid: String,
        val planSets: List<PlanSetDataModel>?,
        val isPlanAttached: Boolean,
    )

    /**
     * Atomically tears down an ad-hoc session: session row, ad-hoc training row, and the
     * inline-created (`is_adhoc = 1`) exercises; library picks filter out at the join.
     */
    suspend fun discardAdhocSession(sessionUuid: String, trainingUuid: String)

    /**
     * Removes one exercise from an in-progress session in one transaction: its sets, its
     * performed row, optionally its plan row, and the entity itself when inline-created.
     */
    suspend fun removeExerciseFromSession(
        performedExerciseUuid: String,
        exerciseUuid: String,
        trainingUuid: String?,
        removeFromPlan: Boolean,
    )

    data class AdhocSessionResult(
        val sessionUuid: String,
        val trainingUuid: String,
    )

    /** Date-ordered history for [exerciseUuid] across finished sessions. */
    fun pagedHistoryByExercise(exerciseUuid: String): Flow<PagingData<HistoryEntry>>

    /** One-shot, bounded version of [pagedHistoryByExercise]. */
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
