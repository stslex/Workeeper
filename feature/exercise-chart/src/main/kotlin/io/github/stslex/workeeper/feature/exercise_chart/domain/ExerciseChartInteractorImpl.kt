// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.RecentExerciseDataModel
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.FoldResult
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class ExerciseChartInteractorImpl @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val sessionRepository: SessionRepository,
    private val resourceWrapper: ResourceWrapper,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ExerciseChartInteractor {

    override suspend fun getRecentlyTrainedExercises(): List<RecentExerciseDataModel> =
        exerciseRepository.getRecentlyTrainedExercises()

    override suspend fun getLastTrainedExerciseUuid(): String? =
        exerciseRepository.getLastTrainedExerciseUuid()

    override suspend fun loadChartData(
        exerciseUuid: String,
        preset: ChartPresetUiModel,
        metric: ChartMetricUiModel,
        type: ExerciseTypeUiModel,
        now: Long,
    ): FoldResult {
        val history = sessionRepository.getHistoryByExercise(exerciseUuid)
        return withContext(defaultDispatcher) {
            ExerciseChartUiMapper.bucketAndFold(
                history = history,
                preset = preset,
                metric = metric,
                exerciseType = type,
                resourceWrapper = resourceWrapper,
                now = now,
            )
        }
    }
}
