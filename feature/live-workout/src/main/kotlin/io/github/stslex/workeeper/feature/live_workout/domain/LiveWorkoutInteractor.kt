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
}
