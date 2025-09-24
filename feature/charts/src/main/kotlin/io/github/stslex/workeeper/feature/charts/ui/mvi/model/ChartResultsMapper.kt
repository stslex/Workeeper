package io.github.stslex.workeeper.feature.charts.ui.mvi.model

import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.feature.charts.di.CHARTS_SCOPE_NAME
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainModel
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = CHARTS_SCOPE_NAME)
internal class ChartResultsMapper : Mapper<SingleChartDomainModel, SingleChartUiModel> {

    override fun invoke(data: SingleChartDomainModel): SingleChartUiModel = SingleChartUiModel(
        name = data.name,
        properties = data.values.toImmutableList(),
    )
}
