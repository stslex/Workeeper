// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain

import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
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

    data class SessionSnapshot(
        val session: SessionDataModel,
        val trainingName: String,
        val isAdhoc: Boolean,
        val exercises: List<PerformedExerciseSnapshot>,
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
