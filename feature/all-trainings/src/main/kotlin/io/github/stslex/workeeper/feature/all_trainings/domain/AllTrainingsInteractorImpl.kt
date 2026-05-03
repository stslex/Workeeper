// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.domain

import androidx.paging.PagingData
import androidx.paging.map
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.all_trainings.domain.mapper.toDomain
import io.github.stslex.workeeper.feature.all_trainings.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_trainings.domain.model.TagDomain
import io.github.stslex.workeeper.feature.all_trainings.domain.model.TrainingListItemDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    ): Flow<PagingData<TrainingListItemDomain>> = trainingRepository
        .pagedActiveWithStats(filterTagUuids)
        .map { pagingData -> pagingData.map { it.toDomain() } }
        .flowOn(defaultDispatcher)

    override fun observeAvailableTags(): Flow<List<TagDomain>> = tagRepository
        .observeAll()
        .map { tags -> tags.map { it.toDomain() } }
        .flowOn(defaultDispatcher)

    override suspend fun archiveTrainings(
        uuids: Set<String>,
    ): BulkArchiveResult = withContext(defaultDispatcher) {
        trainingRepository.bulkArchive(uuids).toDomain()
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
