// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain

import androidx.paging.PagingData
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseListItemDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.TagDomain
import kotlinx.coroutines.flow.Flow

interface AllExercisesInteractor {

    /**
     * Paged active library exercises with per-row stats (session count, linked trainings, last
     * trained) and tag labels; feeds the list-row footer.
     */
    fun observeExercises(filterTagUuids: Set<String>): Flow<PagingData<ExerciseListItemDomain>>

    fun observeAvailableTags(): Flow<List<TagDomain>>

    suspend fun archiveExercise(uuid: String): ArchiveResult

    suspend fun restoreExercise(uuid: String)

    suspend fun canPermanentlyDelete(uuid: String): Boolean

    suspend fun permanentlyDelete(uuid: String)

    suspend fun getExercise(uuid: String): ExerciseDomain?

    suspend fun countSessionsForExercise(uuid: String): Int

    /** Count of distinct active library trainings (non-archived, non-adhoc) referencing [uuid]. */
    fun observeLinkedTrainingsCount(uuid: String): Flow<Int>

    /** Latest finished-session epoch millis for a non-skipped performed [uuid]; null when none. */
    fun observeLastTrainedAt(uuid: String): Flow<Long?>

    suspend fun bulkArchive(uuids: Set<String>): BulkArchiveResult

    suspend fun bulkPermanentDelete(uuids: Set<String>): Int

    suspend fun canBulkPermanentDelete(uuids: Set<String>): Boolean
}
