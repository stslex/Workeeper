// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain

import io.github.stslex.workeeper.feature.single_training.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.single_training.domain.model.PickerExercise
import io.github.stslex.workeeper.feature.single_training.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.single_training.domain.model.TagDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingChangeDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingExerciseDetail
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
internal interface SingleTrainingInteractor {

    suspend fun getTraining(uuid: String): TrainingDomain?

    suspend fun getTrainingExercises(trainingUuid: String): List<TrainingExerciseDetail>

    suspend fun getRecentSessions(trainingUuid: String, limit: Int): List<SessionDomain>

    fun observeAvailableTags(): Flow<List<TagDomain>>

    suspend fun saveTraining(snapshot: TrainingChangeDomain)

    suspend fun createTag(name: String): TagDomain

    suspend fun archive(uuid: String): ArchiveResult

    suspend fun permanentlyDelete(uuid: String)

    suspend fun canPermanentlyDelete(uuid: String): Boolean

    fun observeAnyActiveSession(): Flow<ActiveSessionDomain?>

    suspend fun setPlanForExercise(
        trainingUuid: String,
        exerciseUuid: String,
        plan: List<PlanSetDomain>?,
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
    ): StartSessionConflict

    suspend fun deleteSession(sessionUuid: String)

    suspend fun getLabels(exerciseUuid: String): List<String>
}
