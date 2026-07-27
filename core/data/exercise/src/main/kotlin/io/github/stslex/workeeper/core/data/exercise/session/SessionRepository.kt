package io.github.stslex.workeeper.core.data.exercise.session

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.data.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDetailDataModel
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
     *
     * When [newTrainingName] is non-null, the training row's name is updated inside the
     * same transaction before the session-state flip and graduation. This pairs the v2.3
     * finish-dialog rename with the finish itself so a crash between the two writes can
     * never leave a named-but-unfinished session or an unfinishable named training.
     */
    suspend fun finishSessionAtomic(
        sessionUuid: String,
        finishedAt: Long,
        planUpdates: List<PlanUpdate>,
        newTrainingName: String? = null,
    ): Boolean

    suspend fun deleteSession(uuid: String)

    /**
     * Atomically creates an ad-hoc training row + IN_PROGRESS session + one
     * `performed_exercise` row per [exerciseUuids] entry. Returns both UUIDs so callers can
     * navigate to the live session and tear it down later via [discardAdhocSession].
     *
     * The training row is created with `is_adhoc = true`; the session links to it via the
     * non-nullable `training_uuid` FK. [exerciseUuids] may be empty for a "Start blank"
     * Quick start session — the session is still created with no performed exercises.
     */
    suspend fun createAdhocSession(
        name: String,
        exerciseUuids: List<String>,
    ): AdhocSessionResult

    /**
     * Atomically attaches [exerciseUuid] to the active session: always writes a
     * `performed_exercise_table` row at the next position, and writes a
     * `training_exercise_table` plan row **only when [attachToPlan] is true**. Used both for
     * inline-created (`is_adhoc = true`) exercises and for library picks.
     *
     * [attachToPlan] is the write half of the plan-attached axis (v3 §6.2). Passing `false`
     * produces a one-off: the exercise is fully part of this session but is never added to
     * the saved training template, so the next session does not inherit it. The encoding is
     * the **absence of the plan row** — there is no column and no migration. Note this axis
     * is *not* `exercise_table.is_adhoc`: that flag describes the exercise ("created
     * inline"), this one describes the exercise↔training relation. A library exercise with
     * `is_adhoc = 0` added as a one-off today is the case that separates them.
     *
     * The new plan row's `plan_sets` is seeded from `exercise.last_adhoc_sets` so the user
     * sees their last-logged baseline as a suggestion. Inline-created exercises with no
     * history get null and render with an empty plan, matching prior behavior. Subsequent
     * finish-time `PlanUpdateRule` (grow-but-not-shrink) operates uniformly regardless of
     * how `plan_sets` was initialized.
     *
     * [AddExerciseResult.planSets] is returned on **both** paths — it is read from
     * `exercise.last_adhoc_sets`, not from the plan row — so a one-off still seeds the UI
     * baseline and still round-trips through the `getAdhocPlans` read-time fallback.
     *
     * Returns the new `performed_exercise_table.uuid`, the parsed plan list, and an echo of
     * whether a plan row was written, so the caller can stitch the row into in-memory State
     * without re-loading the session.
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
     * Atomically tears down an ad-hoc session: deletes the session row, deletes the
     * ad-hoc training row, and deletes any inline-created (`is_adhoc = 1`) exercise rows
     * referenced by this training. The defence-in-depth predicate ensures library
     * exercises picked into the session are never deleted — their `is_adhoc = 0` filters
     * them out at the join step. Shared by Track Now Cancel and Quick start empty-finish
     * Discard.
     */
    suspend fun discardAdhocSession(sessionUuid: String, trainingUuid: String)

    data class AdhocSessionResult(
        val sessionUuid: String,
        val trainingUuid: String,
    )

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
