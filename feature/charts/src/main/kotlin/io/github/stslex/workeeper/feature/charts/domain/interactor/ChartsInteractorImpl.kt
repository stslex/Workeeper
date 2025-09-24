package io.github.stslex.workeeper.feature.charts.domain.interactor

import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.charts.di.CHARTS_SCOPE_NAME
import io.github.stslex.workeeper.feature.charts.domain.model.ChartParams
import io.github.stslex.workeeper.feature.charts.domain.model.ChartsDomainType
import io.github.stslex.workeeper.feature.charts.domain.model.ChartsExerciseDomainMapper
import io.github.stslex.workeeper.feature.charts.domain.model.ChartsTrainingDomainMapper
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
    private val chartsTrainingDomainMapper: ChartsTrainingDomainMapper,
    private val chartsExerciseDomainMapper: ChartsExerciseDomainMapper,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher
) : ChartsInteractor {

    override suspend fun getChartsData(
        params: ChartParams
    ): List<SingleChartDomainModel> = withContext(dispatcher) {
        when (params.type) {
            ChartsDomainType.TRAINING -> trainingRepository
                .getTrainings(
                    query = params.name,
                    startDate = params.startDate,
                    endDate = params.endDate
                )
                .asyncMap(chartsTrainingDomainMapper::invoke)

            ChartsDomainType.EXERCISE -> exerciseRepository
                .getExercises(
                    name = params.name,
                    startDate = params.startDate,
                    endDate = params.endDate
                )
                .asyncMap(chartsExerciseDomainMapper::invoke)
        }
    }
}