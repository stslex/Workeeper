// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain

import io.github.stslex.workeeper.feature.single_training.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.single_training.domain.model.ExercisePlanDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.PickerExercise
import io.github.stslex.workeeper.feature.single_training.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.single_training.domain.model.TagDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingChangeDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingExerciseDetail
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface SingleTrainingInteractor {

    suspend fun getTraining(uuid: String): TrainingDomain?

    suspend fun getTrainingExercises(trainingUuid: String): List<TrainingExerciseDetail>

    suspend fun getRecentSessions(trainingUuid: String, limit: Int): List<SessionDomain>

    /** Total finished sessions of this training — the История head's count (§3.3). */
    suspend fun countSessions(trainingUuid: String): Int

    fun observeAvailableTags(): Flow<List<TagDomain>>

    /** The training row and every listed exercise's plan, persisted as one transaction. */
    suspend fun saveTraining(
        snapshot: TrainingChangeDomain,
        plans: List<ExercisePlanDomain>,
    )

    suspend fun createTag(name: String): TagDomain

    suspend fun archive(uuid: String): ArchiveResult

    suspend fun permanentlyDelete(uuid: String)

    suspend fun canPermanentlyDelete(uuid: String): Boolean

    fun observeAnyActiveSession(): Flow<ActiveSessionDomain?>

    suspend fun searchExercisesForPicker(
        query: String,
        excludeUuids: Set<String>,
    ): List<PickerExercise>

    suspend fun resolveExercises(uuids: List<String>): List<PickerExercise>

    /** Only a session belonging to a different training is a conflict; same-training resumes. */
    suspend fun resolveStartSessionConflict(
        requestedTrainingUuid: String,
    ): StartSessionConflict

    suspend fun deleteSession(sessionUuid: String)

    suspend fun getLabels(exerciseUuid: String): List<String>
}
