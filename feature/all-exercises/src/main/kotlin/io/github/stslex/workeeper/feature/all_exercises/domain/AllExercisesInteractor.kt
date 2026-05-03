// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain

import androidx.paging.PagingData
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.TagDomain
import kotlinx.coroutines.flow.Flow

internal interface AllExercisesInteractor {

    fun observeExercises(filterTagUuids: Set<String>): Flow<PagingData<ExerciseDomain>>

    fun observeAvailableTags(): Flow<List<TagDomain>>

    suspend fun archiveExercise(uuid: String): ArchiveResult

    suspend fun restoreExercise(uuid: String)

    suspend fun canPermanentlyDelete(uuid: String): Boolean

    suspend fun permanentlyDelete(uuid: String)

    suspend fun getExercise(uuid: String): ExerciseDomain?

    suspend fun countSessionsForExercise(uuid: String): Int

    suspend fun bulkArchive(uuids: Set<String>): BulkArchiveResult

    suspend fun bulkPermanentDelete(uuids: Set<String>): Int

    suspend fun canBulkPermanentDelete(uuids: Set<String>): Boolean
}
