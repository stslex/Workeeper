// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.domain

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingListItem
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository.BulkArchiveOutcome
import kotlinx.coroutines.flow.Flow

internal interface AllTrainingsInteractor {

    fun observeTrainings(filterTagUuids: Set<String>): Flow<PagingData<TrainingListItem>>

    fun observeAvailableTags(): Flow<List<TagDataModel>>

    suspend fun archiveTrainings(uuids: Set<String>): BulkArchiveOutcome

    suspend fun deleteTrainings(uuids: Set<String>): Int

    suspend fun canPermanentlyDelete(uuids: Set<String>): Boolean
}
