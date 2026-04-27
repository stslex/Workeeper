// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.SaveResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class ExerciseInteractorImpl @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val tagRepository: TagRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ExerciseInteractor {

    override suspend fun getExercise(
        uuid: String,
    ): ExerciseDataModel? = withContext(defaultDispatcher) {
        exerciseRepository.getExercise(uuid)
    }

    override suspend fun getLabels(
        exerciseUuid: String,
    ): List<String> = withContext(defaultDispatcher) {
        exerciseRepository.getLabels(exerciseUuid)
    }

    override suspend fun getRecentHistory(
        exerciseUuid: String,
        limit: Int,
    ): List<HistoryEntry> = withContext(defaultDispatcher) {
        exerciseRepository.getRecentHistory(exerciseUuid, limit)
    }

    override fun observeAvailableTags(): Flow<List<TagDataModel>> = tagRepository
        .observeAll()
        .flowOn(defaultDispatcher)

    override suspend fun saveExercise(
        snapshot: ExerciseChangeDataModel,
    ): SaveResult = withContext(defaultDispatcher) {
        when (exerciseRepository.saveItem(snapshot)) {
            ExerciseRepository.SaveResult.Success -> SaveResult.Success(snapshot.uuid)
            ExerciseRepository.SaveResult.DuplicateName -> SaveResult.DuplicateName
        }
    }

    override suspend fun createTag(name: String): TagDataModel = withContext(defaultDispatcher) {
        tagRepository.add(name)
    }

    override suspend fun archive(uuid: String): ArchiveResult = withContext(defaultDispatcher) {
        val activeTrainings = exerciseRepository.getActiveTrainingsUsing(uuid)
        if (activeTrainings.isNotEmpty()) {
            ArchiveResult.Blocked(activeTrainings)
        } else {
            exerciseRepository.archive(uuid)
            ArchiveResult.Success
        }
    }

    override suspend fun restore(uuid: String) {
        withContext(defaultDispatcher) { exerciseRepository.restore(uuid) }
    }

    override suspend fun canPermanentlyDelete(
        uuid: String,
    ): Boolean = withContext(defaultDispatcher) {
        exerciseRepository.canPermanentlyDeleteImmediately(uuid)
    }

    override suspend fun permanentlyDelete(uuid: String) {
        withContext(defaultDispatcher) { exerciseRepository.permanentDelete(uuid) }
    }

    override suspend fun getAdhocPlan(
        uuid: String,
    ): List<PlanSetDataModel>? = withContext(defaultDispatcher) {
        exerciseRepository.getAdhocPlan(uuid)
    }

    override suspend fun setAdhocPlan(uuid: String, plan: List<PlanSetDataModel>?) {
        withContext(defaultDispatcher) {
            exerciseRepository.setAdhocPlan(uuid, plan)
        }
    }

    override suspend fun clearWeightsFromAllPlansForExercise(uuid: String) {
        withContext(defaultDispatcher) {
            exerciseRepository.clearWeightsFromAllPlansForExercise(uuid)
        }
    }
}
