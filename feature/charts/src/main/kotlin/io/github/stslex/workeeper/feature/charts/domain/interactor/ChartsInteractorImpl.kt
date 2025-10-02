package io.github.stslex.workeeper.feature.charts.domain.interactor

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.charts.domain.calculator.ChartDomainCalculator
import io.github.stslex.workeeper.feature.charts.domain.model.ChartParams
import io.github.stslex.workeeper.feature.charts.domain.model.ChartsDomainType
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class ChartsInteractorImpl @Inject constructor(
    private val trainingRepository: TrainingRepository,
    private val exerciseRepository: ExerciseRepository,
    private val chartsCalculator: ChartDomainCalculator,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ChartsInteractor {

    override suspend fun getChartsData(
        params: ChartParams,
    ): List<SingleChartDomainModel> {
        chartsCalculator.init(
            startTimestamp = params.startDate,
            endTimestamp = params.endDate,
        )
        return withContext(dispatcher) {
            when (params.type) {
                ChartsDomainType.TRAINING -> getTrainingCharts(params)
                ChartsDomainType.EXERCISE -> getExerciseCharts(params)
            }
        }
    }

    private suspend fun getTrainingCharts(
        params: ChartParams,
    ): List<SingleChartDomainModel> = trainingRepository
        .getTrainings(
            query = params.name,
            startDate = params.startDate,
            endDate = params.endDate,
        )
        .let {
            chartsCalculator.mapTrainings(
                startTimestamp = params.startDate,
                endTimestamp = params.endDate,
                trainings = it,
                getExercises = { uuids -> exerciseRepository.getExercisesByUuid(uuids) },
            )
        }

    private suspend fun getExerciseCharts(
        params: ChartParams,
    ): List<SingleChartDomainModel> = exerciseRepository
        .getExercises(
            name = params.name,
            startDate = params.startDate,
            endDate = params.endDate,
        )
        .let {
            chartsCalculator.mapExercises(
                startTimestamp = params.startDate,
                endTimestamp = params.endDate,
                exercises = it,
            )
        }
}
