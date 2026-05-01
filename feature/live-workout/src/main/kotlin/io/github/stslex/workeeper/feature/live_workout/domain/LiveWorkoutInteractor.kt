// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain

import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.personal_record.PersonalRecordDataModel
import io.github.stslex.workeeper.core.exercise.session.model.PerformedExerciseDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionDataModel

@Suppress("TooManyFunctions")
internal interface LiveWorkoutInteractor {

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
     * Atomically appends [exerciseUuid] to the active session's plan and performed list.
     * Per H1 (mid-session changes mutate the plan permanently), the plan row written here
     * survives session finish even if no sets are logged for it. Returns the new
     * performed-exercise UUID so the picker handler can stitch the row directly into
     * `State.exercises` without re-loading the session.
     */
    suspend fun addExerciseToActiveSession(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
    ): String

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
     * the heaviest finished-session set for [exerciseUuid] under the [type]-aware ordering,
     * or `null` for an exercise with no history (typical for newly inline-created entries).
     * The handler merges the result into `State.preSessionPrSnapshot` via map-plus
     * semantics so parallel fetches are race-safe.
     */
    suspend fun fetchPrSnapshotForExercise(
        exerciseUuid: String,
        type: ExerciseTypeDataModel,
    ): PersonalRecordDataModel?

    suspend fun loadSession(sessionUuid: String): SessionSnapshot?

    suspend fun upsertSet(
        performedExerciseUuid: String,
        position: Int,
        set: PlanSetDataModel,
    )

    suspend fun deleteSet(performedExerciseUuid: String, position: Int)

    suspend fun setSkipped(performedExerciseUuid: String, skipped: Boolean)

    suspend fun resetExerciseSets(performedExerciseUuid: String)

    suspend fun finishSession(sessionUuid: String): FinishResult?

    suspend fun cancelSession(sessionUuid: String)

    suspend fun setPlanForExercise(
        trainingUuid: String,
        exerciseUuid: String,
        plan: List<PlanSetDataModel>?,
    )

    suspend fun setAdhocPlan(exerciseUuid: String, plan: List<PlanSetDataModel>?)

    /**
     * Captures the per-exercise PR snapshot taken once at session load. The flow underlying
     * this map is intentionally not held open — Q6 lock requires session-frozen comparator
     * semantics, so the snapshot does not refresh mid-session even if other screens log new
     * sets that would otherwise bump the PR.
     */
    data class SessionSnapshot(
        val session: SessionDataModel,
        val trainingName: String,
        val isAdhoc: Boolean,
        val exercises: List<PerformedExerciseSnapshot>,
        val preSessionPrSnapshot: Map<String, PersonalRecordDataModel?>,
    )

    data class PerformedExerciseSnapshot(
        val performed: PerformedExerciseDataModel,
        val exerciseName: String,
        val exerciseType: ExerciseTypeDataModel,
        val planSets: List<PlanSetDataModel>?,
        val performedSets: List<PlanSetDataModel>,
        val performedSetUuids: List<String>,
    )

    data class FinishResult(
        val durationMillis: Long,
        val doneCount: Int,
        val totalCount: Int,
        val skippedCount: Int,
        val setsLogged: Int,
    )

    /** Domain result of [createAdhocSession] — both UUIDs are needed for cleanup later. */
    data class AdhocSessionResult(
        val sessionUuid: String,
        val trainingUuid: String,
    )

    /**
     * Domain result of [createInlineAdhocExercise]. [reusedExisting] is `true` when the
     * typed name matched a library entry; the picker silently surfaces it instead of
     * raising an error.
     */
    data class InlineAdhocResult(
        val exerciseUuid: String,
        val name: String,
        val type: ExerciseTypeDataModel,
        val reusedExisting: Boolean,
    )

    data class ExercisePickerEntry(
        val uuid: String,
        val name: String,
        val type: ExerciseTypeDataModel,
    )
}
