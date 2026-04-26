// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.paging.PagingData
import androidx.paging.map
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class SettingsInteractorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commonDataStore: CommonDataStore,
    private val exerciseRepository: ExerciseRepository,
    private val trainingRepository: TrainingRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : SettingsInteractor {

    private val packageInfo: PackageInfo by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }

    override fun appVersionName(): String = packageInfo.versionName.orEmpty()

    override fun appVersionCode(): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }

    override fun observeThemeMode(): Flow<ThemeMode> = commonDataStore.themePreference
        .map { value -> runCatching { ThemeMode.valueOf(value) }.getOrDefault(ThemeMode.SYSTEM) }
        .flowOn(defaultDispatcher)

    override suspend fun setThemeMode(mode: ThemeMode) {
        commonDataStore.setThemePreference(mode.name)
    }

    override fun observeArchivedExerciseCount(): Flow<Int> = exerciseRepository
        .observeArchivedCount()
        .flowOn(defaultDispatcher)

    override fun observeArchivedTrainingCount(): Flow<Int> = trainingRepository
        .observeArchivedCount()
        .flowOn(defaultDispatcher)

    override fun pagedArchivedExercises(): Flow<PagingData<ArchivedItem.Exercise>> = exerciseRepository
        .pagedArchived()
        .map { pagingData ->
            pagingData.map { exercise ->
                ArchivedItem.Exercise(
                    uuid = exercise.uuid,
                    name = exercise.name,
                    tags = exercise.labels,
                    archivedAt = exercise.archivedAt ?: exercise.timestamp,
                    type = exercise.type,
                )
            }
        }
        .flowOn(defaultDispatcher)

    override fun pagedArchivedTrainings(): Flow<PagingData<ArchivedItem.Training>> = trainingRepository
        .pagedArchived()
        .map { pagingData ->
            pagingData.map { training ->
                ArchivedItem.Training(
                    uuid = training.uuid,
                    name = training.name,
                    tags = training.labels,
                    archivedAt = training.archivedAt ?: training.timestamp,
                    exerciseCount = training.exerciseUuids.size,
                )
            }
        }
        .flowOn(defaultDispatcher)

    override suspend fun restoreExercise(uuid: String) {
        withContext(defaultDispatcher) { exerciseRepository.restore(uuid) }
    }

    override suspend fun restoreTraining(uuid: String) {
        withContext(defaultDispatcher) { trainingRepository.restore(uuid) }
    }

    override suspend fun reArchiveExercise(uuid: String) {
        withContext(defaultDispatcher) { exerciseRepository.archive(uuid) }
    }

    override suspend fun reArchiveTraining(uuid: String) {
        withContext(defaultDispatcher) { trainingRepository.archive(uuid) }
    }

    override suspend fun countExerciseSessions(
        uuid: String,
    ): Int = withContext(defaultDispatcher) {
        exerciseRepository.countSessionsUsing(uuid)
    }

    override suspend fun countTrainingSessions(
        uuid: String,
    ): Int = withContext(defaultDispatcher) {
        trainingRepository.countSessionsUsing(uuid)
    }

    override suspend fun permanentlyDeleteExercise(uuid: String) {
        withContext(defaultDispatcher) { exerciseRepository.permanentDelete(uuid) }
    }

    override suspend fun permanentlyDeleteTraining(uuid: String) {
        withContext(defaultDispatcher) { trainingRepository.permanentDelete(uuid) }
    }
}
