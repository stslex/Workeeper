// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain

import io.github.stslex.workeeper.feature.live_workout.domain.model.AddExerciseResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.AdhocSessionResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExercisePickerEntry
import io.github.stslex.workeeper.feature.live_workout.domain.model.FinishResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.InlineAdhocResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionSnapshotDomain

@Suppress("TooManyFunctions")
interface LiveWorkoutInteractor {

    suspend fun startSession(trainingUuid: String): String

    /**
     * Creates an ad-hoc training + IN_PROGRESS session in one transaction. Used by the
     * v2.3 "Start blank" Quick start entry (with [exerciseUuids] empty) and shared with
     * Track Now via the same `SessionRepository.createAdhocSession` underneath. Returns
     * both UUIDs because the [discardAdhocSession] cleanup later needs the training UUID
     * even after the session row is gone.
     */
    suspend fun createAdhocSession(
        name: String,
        exerciseUuids: List<String>,
    ): AdhocSessionResult

    /**
     * Atomically appends [exerciseUuid] to the active session's performed list, and to the
     * training's plan **only when [attachToPlan] is true**.
     *
     * [attachToPlan] is the write half of the plan-attached axis (v3 §6.2); see
     * [LiveExerciseDomain.isPlanAttached] for why this is not `is_adhoc`. With `true` the
     * historical H1 behaviour holds — the plan row survives session finish even if no sets
     * are logged. With `false` the exercise is a one-off: it is fully part of this session
     * and counts toward progress, but the saved training template is left untouched, so the
     * next session does not inherit it.
     *
     * The plan_sets baseline is seeded from `exercise.last_adhoc_sets` on **both** paths, so
     * picking a library row with history surfaces the user's last-logged sets either way.
     *
     * Returns the new performed-exercise UUID, the parsed plan list, and whether a plan row
     * was written, so the picker handler can stitch the row directly into `State.exercises`
     * without re-loading.
     */
    suspend fun addExerciseToActiveSession(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
        attachToPlan: Boolean = true,
    ): AddExerciseResult

    /**
     * Cancels an ad-hoc session: deletes session, training, and inline-created exercises in
     * one transaction. Defence-in-depth: only `is_adhoc = 1` exercises joined via the
     * cancelled training are deleted; library exercises picked into the session are kept.
     */
    suspend fun discardAdhocSession(sessionUuid: String, trainingUuid: String)

    /**
     * Inserts a fresh `is_adhoc = 1` exercise from a single user-typed name, or surfaces an
     * existing library entry when the name already matches one (case-insensitive). The
     * caller is expected to follow up with [addExerciseToActiveSession] to attach it.
     */
    suspend fun createInlineAdhocExercise(name: String): InlineAdhocResult

    /**
     * Updates the editable training name header. Direct DAO update — does not retouch
     * exercises or labels.
     */
    suspend fun updateTrainingName(trainingUuid: String, name: String)

    /**
     * One-shot library lookup powering the inline picker bottom sheet. Filters
     * `is_adhoc = 0` and `archived = 0` at the DAO layer. [excludedUuids] holds the
     * exercise UUIDs already attached to the active session so picked rows do not appear
     * a second time.
     */
    suspend fun searchExercisesForPicker(
        query: String,
        excludedUuids: Set<String>,
    ): List<ExercisePickerEntry>

    /**
     * Single-exercise lazy PR fetch used by the mid-session add-exercise handler. Returns
     * the record-holding finished-session set for [exerciseUuid], or `null` for an exercise
     * with no history (typical for newly inline-created entries). The exercise type is read
     * from the DB by the query itself, so the handler cannot hand it a stale one. The handler
     * merges the result into `State.preSessionPrSnapshot` via map-plus semantics so parallel
     * fetches are race-safe.
     */
    suspend fun fetchPrSnapshotForExercise(exerciseUuid: String): PersonalRecordDomain?

    suspend fun loadSession(sessionUuid: String): SessionSnapshotDomain?

    suspend fun upsertSet(
        performedExerciseUuid: String,
        position: Int,
        set: PlanSetDomain,
    )

    suspend fun deleteSet(performedExerciseUuid: String, position: Int)

    /**
     * Toggles the skip flag WITHOUT touching set rows (v3 §6.1: skip is "excluded from the
     * denominator, plan untouched, reversible in place" — reversal is only lossless if the
     * logged sets survive). An earlier revision wiped the sets here, which is why a
     * confirmation dialog used to guard it; both are gone together.
     */
    suspend fun setSkipped(performedExerciseUuid: String, skipped: Boolean)

    /**
     * Removes one exercise from the active session (v3 §6.1 "deleted"): sets + performed
     * row (+ the plan row when [removeFromPlan] — §6.2's row-absence encoding) + the
     * stranded-inline-exercise cleanup, in one transaction.
     */
    suspend fun deleteExerciseFromSession(
        performedExerciseUuid: String,
        exerciseUuid: String,
        trainingUuid: String?,
        removeFromPlan: Boolean,
    )

    /**
     * The one-off toggle (v3 §6.1/§6.2): [attached] `false` deletes the pair's plan row —
     * the exercise stays in the session and out of the template — and `true` re-inserts it
     * with [planSets]. Non-ad-hoc trainings only; an ad-hoc session has no plan rows to
     * detach from by construction.
     */
    suspend fun setPlanAttachment(
        trainingUuid: String,
        exerciseUuid: String,
        attached: Boolean,
        planSets: List<PlanSetDomain>?,
    )

    suspend fun resetExerciseSets(performedExerciseUuid: String)

    /**
     * Finishes [sessionUuid] and applies the per-exercise plan updates atomically.
     * When [newTrainingName] is non-null, the training rename is applied inside the same
     * transaction as the state flip + graduation — used by the finish-dialog name-required
     * path so the rename and finish cannot land out of order. The header-blur edit path
     * still uses [updateTrainingName] directly because that rename is independent of any
     * session-state change.
     */
    suspend fun finishSession(
        sessionUuid: String,
        newTrainingName: String? = null,
    ): FinishResult?

    suspend fun cancelSession(sessionUuid: String)

    suspend fun setPlanForExercise(
        trainingUuid: String,
        exerciseUuid: String,
        plan: List<PlanSetDomain>?,
    )

    suspend fun setAdhocPlan(exerciseUuid: String, plan: List<PlanSetDomain>?)
}
