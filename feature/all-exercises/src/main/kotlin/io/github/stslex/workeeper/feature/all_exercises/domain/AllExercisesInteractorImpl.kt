// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain

import androidx.paging.PagingData
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor.ArchiveResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class AllExercisesInteractorImpl @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val tagRepository: TagRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : AllExercisesInteractor {

    override fun observeExercises(
        filterTagUuids: Set<String>,
    ): Flow<PagingData<ExerciseDataModel>> = exerciseRepository
        .pagedActiveByTags(filterTagUuids)
        .flowOn(defaultDispatcher)

    override fun observeAvailableTags(): Flow<List<TagDataModel>> = tagRepository
        .observeAll()
        .flowOn(defaultDispatcher)

    override suspend fun archiveExercise(uuid: String): ArchiveResult = withContext(defaultDispatcher) {
        val activeTrainings = exerciseRepository.getActiveTrainingsUsing(uuid)
        if (activeTrainings.isNotEmpty()) {
            ArchiveResult.Blocked(activeTrainings)
        } else {
            exerciseRepository.archive(uuid)
            ArchiveResult.Success
        }
    }

    override suspend fun restoreExercise(uuid: String) {
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

    override suspend fun getExercise(
        uuid: String,
    ): ExerciseDataModel? = withContext(defaultDispatcher) {
        exerciseRepository.getExercise(uuid)
    }

    override suspend fun countSessionsForExercise(
        uuid: String,
    ): Int = withContext(defaultDispatcher) {
        exerciseRepository.countSessionsUsing(uuid)
    }

    override suspend fun bulkArchive(
        uuids: Set<String>,
    ): io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository.BulkArchiveOutcome =
        withContext(defaultDispatcher) {
            exerciseRepository.bulkArchive(uuids)
        }

    override suspend fun bulkPermanentDelete(
        uuids: Set<String>,
    ): Int = withContext(defaultDispatcher) {
        exerciseRepository.bulkPermanentDelete(uuids)
        uuids.size
    }

    override suspend fun canBulkPermanentDelete(
        uuids: Set<String>,
    ): Boolean = withContext(defaultDispatcher) {
        exerciseRepository.canBulkPermanentDelete(uuids)
    }
}
