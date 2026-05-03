// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain

import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFoldDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartMetricDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPresetDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.RecentExerciseDomain

internal interface ExerciseChartInteractor {

    suspend fun getRecentlyTrainedExercises(): List<RecentExerciseDomain>

    suspend fun getLastTrainedExerciseUuid(): String?

    /**
     * Fetch the history for [exerciseUuid] and bucket it into chart points + footer.
     * The repository call runs on IO; the bucketAndFold runs on Default per the spec's
     * architectural notes.
     */
    suspend fun loadChartData(
        exerciseUuid: String,
        preset: ChartPresetDomain,
        metric: ChartMetricDomain,
        type: ExerciseTypeDomain,
        now: Long,
    ): ChartFoldDomain
}
