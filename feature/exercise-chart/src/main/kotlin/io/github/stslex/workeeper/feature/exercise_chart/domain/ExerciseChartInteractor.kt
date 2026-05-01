// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain

import io.github.stslex.workeeper.core.exercise.exercise.model.RecentExerciseDataModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.FoldResult
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel

internal interface ExerciseChartInteractor {

    suspend fun getRecentlyTrainedExercises(): List<RecentExerciseDataModel>

    suspend fun getLastTrainedExerciseUuid(): String?

    /**
     * Fetch the history for [exerciseUuid] and bucket it into chart points + footer.
     * The repository call runs on IO; the [io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.bucketAndFold]
     * fold runs on Default per the spec's architectural notes.
     */
    suspend fun loadChartData(
        exerciseUuid: String,
        preset: ChartPresetUiModel,
        metric: ChartMetricUiModel,
        type: ExerciseTypeUiModel,
        now: Long,
    ): FoldResult
}
