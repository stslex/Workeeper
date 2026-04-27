// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.domain

import androidx.paging.PagingData
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingListItem
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository.BulkArchiveOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class AllTrainingsInteractorImpl @Inject constructor(
    private val trainingRepository: TrainingRepository,
    private val tagRepository: TagRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : AllTrainingsInteractor {

    override fun observeTrainings(
        filterTagUuids: Set<String>,
    ): Flow<PagingData<TrainingListItem>> = trainingRepository
        .pagedActiveWithStats(filterTagUuids)
        .flowOn(defaultDispatcher)

    override fun observeAvailableTags(): Flow<List<TagDataModel>> = tagRepository
        .observeAll()
        .flowOn(defaultDispatcher)

    override suspend fun archiveTrainings(
        uuids: Set<String>,
    ): BulkArchiveOutcome = withContext(defaultDispatcher) {
        trainingRepository.bulkArchive(uuids)
    }

    override suspend fun deleteTrainings(
        uuids: Set<String>,
    ): Int = withContext(defaultDispatcher) {
        trainingRepository.bulkPermanentDelete(uuids)
        uuids.size
    }

    override suspend fun canPermanentlyDelete(
        uuids: Set<String>,
    ): Boolean = withContext(defaultDispatcher) {
        trainingRepository.canBulkPermanentDelete(uuids)
    }
}
