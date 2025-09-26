package io.github.stslex.workeeper.feature.charts.domain.interactor

import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.DefaultDispatcher
import io.github.stslex.workeeper.core.core.utils.NumUiUtils.safeDiv
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.charts.di.CHARTS_SCOPE_NAME
import io.github.stslex.workeeper.feature.charts.domain.model.ChartParams
import io.github.stslex.workeeper.feature.charts.domain.model.ChartsDomainType
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [ChartsInteractor::class])
@Scope(name = CHARTS_SCOPE_NAME)
internal class ChartsInteractorImpl(
    private val trainingRepository: TrainingRepository,
    private val exerciseRepository: ExerciseRepository,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ChartsInteractor {

    override suspend fun getChartsData(
        params: ChartParams,
    ): List<SingleChartDomainModel> = withContext(dispatcher) {
        when (params.type) {
            ChartsDomainType.TRAINING ->
                trainingRepository
                    .getTrainings(
                        query = params.name,
                        startDate = params.startDate,
                        endDate = params.endDate,
                    )
                    .groupBy { it.name }
                    .map { (name, trainings) ->
                        SingleChartDomainModel(
                            name = name,
                            values = trainings
                                .asyncMap { training ->
                                    exerciseRepository
                                        .getExercisesByUuid(training.exerciseUuids)
                                        .map { exercise ->
                                            exercise.sets.maxOfOrNull { it.weight } ?: 0.0
                                        }
                                        .let { exercise -> exercise.sumOf { it } safeDiv exercise.size }
                                        .toFloat()
                                },
                        )
                    }

            ChartsDomainType.EXERCISE ->
                exerciseRepository
                    .getExercises(
                        name = params.name,
                        startDate = params.startDate,
                        endDate = params.endDate,
                    )
                    .groupBy { it.name }
                    .map { (name, exercises) ->
                        SingleChartDomainModel(
                            name = name,
                            values = exercises
                                .map { exercise ->
                                    exercise.sets.maxOfOrNull { it.weight }?.toFloat() ?: 0f
                                },
                        )
                    }
        }
    }
}
