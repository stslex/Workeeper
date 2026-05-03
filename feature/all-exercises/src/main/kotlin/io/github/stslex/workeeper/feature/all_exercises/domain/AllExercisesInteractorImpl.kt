// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain

import androidx.paging.PagingData
import androidx.paging.map
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.feature.all_exercises.domain.mapper.toDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.TagDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    ): Flow<PagingData<ExerciseDomain>> = exerciseRepository
        .pagedActiveByTags(filterTagUuids)
        .map { pagingData -> pagingData.map { it.toDomain() } }
        .flowOn(defaultDispatcher)

    override fun observeAvailableTags(): Flow<List<TagDomain>> = tagRepository
        .observeAll()
        .map { tags -> tags.map { it.toDomain() } }
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
    ): ExerciseDomain? = withContext(defaultDispatcher) {
        exerciseRepository.getExercise(uuid)?.toDomain()
    }

    override suspend fun countSessionsForExercise(
        uuid: String,
    ): Int = withContext(defaultDispatcher) {
        exerciseRepository.countSessionsUsing(uuid)
    }

    override suspend fun bulkArchive(
        uuids: Set<String>,
    ): BulkArchiveResult = withContext(defaultDispatcher) {
        exerciseRepository.bulkArchive(uuids).toDomain()
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
