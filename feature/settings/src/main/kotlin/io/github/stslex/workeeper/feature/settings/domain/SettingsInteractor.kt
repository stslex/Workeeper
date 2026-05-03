// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import androidx.paging.PagingData
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.settings.domain.model.ThemeModeDomain
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
internal interface SettingsInteractor {

    fun appVersionName(): String

    fun appVersionCode(): Long

    fun observeThemeMode(): Flow<ThemeModeDomain>

    suspend fun setThemeMode(mode: ThemeModeDomain)

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
