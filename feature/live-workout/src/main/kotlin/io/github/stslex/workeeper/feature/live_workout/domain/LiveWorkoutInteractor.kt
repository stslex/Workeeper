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
     * Creates an ad-hoc training + IN_PROGRESS session in one transaction. Returns both
     * UUIDs because [discardAdhocSession] needs the training UUID after the session is gone.
     */
    suspend fun createAdhocSession(
        name: String,
        exerciseUuids: List<String>,
    ): AdhocSessionResult

    /**
     * Appends [exerciseUuid] to the active session, and to the training's plan only when
     * [attachToPlan]. Seeds plan_sets from `last_adhoc_sets` on both paths. See v3 §6.2.
     */
    suspend fun addExerciseToActiveSession(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
        attachToPlan: Boolean = true,
    ): AddExerciseResult

    /** Deletes an ad-hoc session, its training and its inline-created exercises in one go. */
    suspend fun discardAdhocSession(sessionUuid: String, trainingUuid: String)

    /** Inserts a fresh `is_adhoc = 1` exercise, or returns a case-insensitive name match. */
    suspend fun createInlineAdhocExercise(name: String): InlineAdhocResult

    /** Updates the editable training-name header; direct DAO update, nothing else touched. */
    suspend fun updateTrainingName(trainingUuid: String, name: String)

    /** One-shot picker lookup over non-adhoc, non-archived rows, minus [excludedUuids]. */
    suspend fun searchExercisesForPicker(
        query: String,
        excludedUuids: Set<String>,
    ): List<ExercisePickerEntry>

    /**
     * Single-exercise PR fetch for the mid-session add path; `null` when there is no history.
     * Merged into `State.preSessionPrSnapshot` with map-plus so parallel fetches are safe.
     */
    suspend fun fetchPrSnapshotForExercise(exerciseUuid: String): PersonalRecordDomain?

    suspend fun loadSession(sessionUuid: String): SessionSnapshotDomain?

    suspend fun upsertSet(
        performedExerciseUuid: String,
        position: Int,
        set: PlanSetDomain,
    )

    suspend fun deleteSet(performedExerciseUuid: String, position: Int)

    /** Toggles the skip flag WITHOUT touching set rows, so reversal stays lossless. */
    suspend fun setSkipped(performedExerciseUuid: String, skipped: Boolean)

    /**
     * Removes one exercise from the active session: sets + performed row (+ the plan row
     * when [removeFromPlan]) + stranded-inline cleanup, in one transaction.
     */
    suspend fun deleteExerciseFromSession(
        performedExerciseUuid: String,
        exerciseUuid: String,
        trainingUuid: String?,
        removeFromPlan: Boolean,
    )

    /**
     * The one-off toggle: [attached] `false` deletes the pair's plan row, `true` re-inserts
     * it with [planSets]. Non-ad-hoc trainings only.
     */
    suspend fun setPlanAttachment(
        trainingUuid: String,
        exerciseUuid: String,
        attached: Boolean,
        planSets: List<PlanSetDomain>?,
    )

    suspend fun resetExerciseSets(performedExerciseUuid: String)

    /**
     * Finishes [sessionUuid] and applies the per-exercise plan updates atomically. A non-null
     * [newTrainingName] renames inside the same transaction so it cannot land out of order.
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
