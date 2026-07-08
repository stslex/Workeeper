// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartScope
import io.github.stslex.workeeper.feature.exercise_chart.domain.mapper.ExerciseChartDomainMapper.toDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFoldDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartMetricDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPresetDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.RecentExerciseDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Inject
@SingleIn(ExerciseChartScope::class)
internal class ExerciseChartInteractorImpl(
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
