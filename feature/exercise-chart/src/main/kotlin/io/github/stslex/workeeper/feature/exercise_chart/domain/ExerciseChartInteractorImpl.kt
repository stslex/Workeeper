// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.feature.exercise_chart.domain.mapper.toDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFoldDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartMetricDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPresetDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.RecentExerciseDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class ExerciseChartInteractorImpl @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val sessionRepository: SessionRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ExerciseChartInteractor {

    override suspend fun getRecentlyTrainedExercises(): List<RecentExerciseDomain> =
        exerciseRepository.getRecentlyTrainedExercises().map { it.toDomain() }

    override suspend fun getLastTrainedExerciseUuid(): String? =
        exerciseRepository.getLastTrainedExerciseUuid()

    override suspend fun loadChartData(
        exerciseUuid: String,
        preset: ChartPresetDomain,
        metric: ChartMetricDomain,
        type: ExerciseTypeDomain,
        now: Long,
    ): ChartFoldDomain = withContext(defaultDispatcher) {
        val history = sessionRepository.getHistoryByExercise(exerciseUuid)
            .map { it.toDomain() }
        bucketAndFold(
            history = history,
            preset = preset,
            metric = metric,
            exerciseType = type,
            now = now,
        )
    }
}
