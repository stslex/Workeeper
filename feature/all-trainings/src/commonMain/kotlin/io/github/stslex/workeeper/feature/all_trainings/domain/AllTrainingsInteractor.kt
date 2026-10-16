// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.domain

import androidx.paging.PagingData
import io.github.stslex.workeeper.feature.all_trainings.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_trainings.domain.model.TagDomain
import io.github.stslex.workeeper.feature.all_trainings.domain.model.TrainingListItemDomain
import kotlinx.coroutines.flow.Flow

interface AllTrainingsInteractor {

    fun observeTrainings(filterTagUuids: Set<String>): Flow<PagingData<TrainingListItemDomain>>

    fun observeAvailableTags(): Flow<List<TagDomain>>

    /**
     * Whether a workout is running now — gates the empty state's blank-start CTA, since
     * `createAdhocSession` has no in-progress guard. See v3-redesign-spec.md (B27).
     */
    fun observeHasActiveSession(): Flow<Boolean>

    suspend fun archiveTrainings(uuids: Set<String>): BulkArchiveResult

    suspend fun deleteTrainings(uuids: Set<String>): Int

    suspend fun canPermanentlyDelete(uuids: Set<String>): Boolean
}
