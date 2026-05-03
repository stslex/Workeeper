// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingDataModel
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
internal interface SingleTrainingInteractor {

    suspend fun getTraining(uuid: String): TrainingDataModel?

    suspend fun getTrainingExercises(trainingUuid: String): List<TrainingExerciseDetail>

    suspend fun getRecentSessions(trainingUuid: String, limit: Int): List<SessionDataModel>

    fun observeAvailableTags(): Flow<List<TagDataModel>>

    suspend fun saveTraining(snapshot: TrainingChangeDataModel)

    suspend fun createTag(name: String): TagDataModel

    suspend fun archive(uuid: String): ArchiveResult

    suspend fun permanentlyDelete(uuid: String)

    suspend fun canPermanentlyDelete(uuid: String): Boolean

    fun observeAnyActiveSession(): Flow<ActiveSessionInfo?>

    suspend fun setPlanForExercise(
        trainingUuid: String,
        exerciseUuid: String,
        plan: List<PlanSetDataModel>?,
    )

    suspend fun searchExercisesForPicker(
        query: String,
        excludeUuids: Set<String>,
    ): List<PickerExercise>

    suspend fun resolveExercises(uuids: List<String>): List<PickerExercise>

    /**
     * Resolves the at-most-one-active-session invariant for the Start session button. A
     * conflict only arises when an active session belongs to a *different* training; the
     * same-training case silently resumes (the user implicitly chose to continue).
     */
    suspend fun resolveStartSessionConflict(
        requestedTrainingUuid: String,
    ): SessionConflictResolver.Resolution

    suspend fun deleteSession(sessionUuid: String)

    /**
     * Picker projection: an ExerciseDataModel plus the labels denormalized through the
     * exercise_tag join. The labels live outside the data model (per the v3 hygiene rule)
     * so we surface them here for callers that actually want to render tags inline.
     */
    data class PickerExercise(
        val exercise: ExerciseDataModel,
        val labels: List<String>,
    )

    suspend fun getLabels(exerciseUuid: String): List<String>

    /**
     * Joined training_exercise row + linked exercise template. The plan lives on the join
     * (training_exercise.plan_sets) but the name/type/tags come from the exercise table.
     */
    data class TrainingExerciseDetail(
        val exercise: ExerciseDataModel,
        val position: Int,
        val planSets: List<PlanSetDataModel>?,
        val labels: List<String>,
    )

    sealed interface ArchiveResult {

        data object Success : ArchiveResult

        data class Blocked(val reason: String) : ArchiveResult
    }
}
