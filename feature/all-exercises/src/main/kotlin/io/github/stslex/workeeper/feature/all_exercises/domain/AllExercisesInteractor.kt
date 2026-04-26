// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.domain

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.tags.model.TagDataModel
import kotlinx.coroutines.flow.Flow

internal interface AllExercisesInteractor {

    fun observeExercises(filterTagUuids: Set<String>): Flow<PagingData<ExerciseDataModel>>

    fun observeAvailableTags(): Flow<List<TagDataModel>>

    suspend fun archiveExercise(uuid: String): ArchiveResult

    suspend fun restoreExercise(uuid: String)

    suspend fun getExercise(uuid: String): ExerciseDataModel?

    suspend fun countSessionsForExercise(uuid: String): Int

    sealed interface ArchiveResult {

        data object Success : ArchiveResult

        data class Blocked(val activeTrainings: List<String>) : ArchiveResult
    }
}
