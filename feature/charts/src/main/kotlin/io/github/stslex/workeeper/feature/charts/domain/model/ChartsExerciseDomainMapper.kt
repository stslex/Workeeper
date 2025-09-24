package io.github.stslex.workeeper.feature.charts.domain.model

import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.feature.charts.di.CHARTS_SCOPE_NAME
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = CHARTS_SCOPE_NAME)
internal class ChartsExerciseDomainMapper : Mapper<ExerciseDataModel, SingleChartDomainModel> {

    override fun invoke(data: ExerciseDataModel): SingleChartDomainModel = SingleChartDomainModel(
        name = data.name,
        values = data.sets.map { it.weight },
    )
}
