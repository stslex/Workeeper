// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain

import androidx.paging.PagingData
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseListItemDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.TagDomain
import kotlinx.coroutines.flow.Flow

internal interface AllExercisesInteractor {

    /**
     * Paged active library exercises plus per-row stats (session count, linked-trainings
     * count, last-trained timestamp) and tag labels. Footer rendering on the all-exercises
     * list reads from this stream. (v2.4 E6.)
     */
    fun observeExercises(filterTagUuids: Set<String>): Flow<PagingData<ExerciseListItemDomain>>

    fun observeAvailableTags(): Flow<List<TagDomain>>

    suspend fun archiveExercise(uuid: String): ArchiveResult

    suspend fun restoreExercise(uuid: String)

    suspend fun canPermanentlyDelete(uuid: String): Boolean

    suspend fun permanentlyDelete(uuid: String)

    suspend fun getExercise(uuid: String): ExerciseDomain?

    suspend fun countSessionsForExercise(uuid: String): Int

    /**
     * Count of distinct active library trainings (non-archived, non-adhoc) that reference
     * [uuid] via `training_exercise_table`. (v2.4 F1.)
     */
    fun observeLinkedTrainingsCount(uuid: String): Flow<Int>

    /**
     * Most recent finished session timestamp (epoch millis) for any non-skipped performed
     * exercise referencing [uuid]; `null` when no such session exists. (v2.4 F2.)
     */
    fun observeLastTrainedAt(uuid: String): Flow<Long?>

    suspend fun bulkArchive(uuids: Set<String>): BulkArchiveResult

    suspend fun bulkPermanentDelete(uuids: Set<String>): Int

    suspend fun canBulkPermanentDelete(uuids: Set<String>): Boolean
}
