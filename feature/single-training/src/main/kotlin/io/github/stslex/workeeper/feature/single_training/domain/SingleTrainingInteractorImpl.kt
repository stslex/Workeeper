// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor.ArchiveResult
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor.TrainingExerciseDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongParameterList")
@ViewModelScoped
internal class SingleTrainingInteractorImpl @Inject constructor(
    private val trainingRepository: TrainingRepository,
    private val trainingExerciseRepository: TrainingExerciseRepository,
    private val exerciseRepository: ExerciseRepository,
    private val tagRepository: TagRepository,
    private val sessionRepository: SessionRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : SingleTrainingInteractor {

    override suspend fun getTraining(
        uuid: String,
    ): TrainingDataModel? = withContext(defaultDispatcher) {
        trainingRepository.getTraining(uuid)
    }

    override suspend fun getTrainingExercises(
        trainingUuid: String,
    ): List<TrainingExerciseDetail> = withContext(defaultDispatcher) {
        val rows = trainingExerciseRepository.getRowsForTraining(trainingUuid)
        if (rows.isEmpty()) return@withContext emptyList()
        val exercises = exerciseRepository
            .getExercisesByUuid(rows.map { it.exerciseUuid })
            .associateBy { it.uuid }
        rows.mapNotNull { row ->
            val exercise = exercises[row.exerciseUuid] ?: return@mapNotNull null
            TrainingExerciseDetail(
                exercise = exercise,
                position = row.position,
                planSets = row.planSets,
            )
        }
    }

    override suspend fun getRecentSessions(
        trainingUuid: String,
        limit: Int,
    ): List<SessionDataModel> = withContext(defaultDispatcher) {
        sessionRepository.getRecentFinishedByTraining(trainingUuid, limit)
    }

    override fun observeAvailableTags(): Flow<List<TagDataModel>> = tagRepository
        .observeAll()
        .flowOn(defaultDispatcher)

    override suspend fun saveTraining(snapshot: TrainingChangeDataModel) {
        withContext(defaultDispatcher) {
            trainingRepository.updateTraining(snapshot)
        }
    }

    override suspend fun createTag(name: String): TagDataModel = withContext(defaultDispatcher) {
        tagRepository.add(name)
    }

    override suspend fun archive(uuid: String): ArchiveResult = withContext(defaultDispatcher) {
        val active = sessionRepository.getAnyActiveSession()
        if (active != null && active.trainingUuid == uuid) {
            ArchiveResult.Blocked(reason = "active_session")
        } else {
            trainingRepository.archive(uuid)
            ArchiveResult.Success
        }
    }

    override suspend fun permanentlyDelete(uuid: String) {
        withContext(defaultDispatcher) {
            trainingRepository.permanentDelete(uuid)
        }
    }

    override suspend fun canPermanentlyDelete(
        uuid: String,
    ): Boolean = withContext(defaultDispatcher) {
        val active = sessionRepository.getAnyActiveSession()
        active?.trainingUuid != uuid &&
            trainingRepository.countSessionsUsing(uuid) == 0
    }

    override fun observeAnyActiveSession(): Flow<ActiveSessionInfo?> = sessionRepository
        .observeAnyActiveSession()
        .flowOn(defaultDispatcher)

    override suspend fun setPlanForExercise(
        trainingUuid: String,
        exerciseUuid: String,
        plan: List<PlanSetDataModel>?,
    ) {
        withContext(defaultDispatcher) {
            trainingExerciseRepository.setPlan(trainingUuid, exerciseUuid, plan)
        }
    }

    override suspend fun searchExercisesForPicker(
        query: String,
        excludeUuids: Set<String>,
    ): List<ExerciseDataModel> = withContext(defaultDispatcher) {
        exerciseRepository.searchActiveExercises(query, excludeUuids)
    }

    override suspend fun resolveExercises(
        uuids: List<String>,
    ): List<ExerciseDataModel> = withContext(defaultDispatcher) {
        exerciseRepository.getExercisesByUuid(uuids)
    }
}
