package io.github.stslex.workeeper.feature.charts.domain.model

import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.feature.charts.di.CHARTS_SCOPE_NAME
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = CHARTS_SCOPE_NAME)
internal class ChartsTrainingDomainMapper : Mapper<TrainingDataModel, SingleChartDomainModel> {

    override fun invoke(data: TrainingDataModel): SingleChartDomainModel = SingleChartDomainModel(
        name = data.name,
        values = listOf(0.0) // todo Placeholder for actual values
    )
}