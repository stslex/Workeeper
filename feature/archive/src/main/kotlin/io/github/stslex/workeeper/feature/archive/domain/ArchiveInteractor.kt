// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.domain

import androidx.paging.PagingData
import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface ArchiveInteractor {

    fun observeArchivedExerciseCount(): Flow<Int>

    fun observeArchivedTrainingCount(): Flow<Int>

    fun pagedArchivedExercises(): Flow<PagingData<ArchivedItem.Exercise>>

    fun pagedArchivedTrainings(): Flow<PagingData<ArchivedItem.Training>>

    suspend fun restoreExercise(uuid: String)

    suspend fun restoreTraining(uuid: String)

    suspend fun reArchiveExercise(uuid: String)

    suspend fun reArchiveTraining(uuid: String)

    suspend fun countExerciseSessions(uuid: String): Int

    suspend fun countTrainingSessions(uuid: String): Int

    suspend fun permanentlyDeleteExercise(uuid: String)

    suspend fun permanentlyDeleteTraining(uuid: String)
}
