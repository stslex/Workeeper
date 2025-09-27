package io.github.stslex.workeeper.feature.charts.ui.mvi.model

import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.feature.charts.di.CHARTS_SCOPE_NAME
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainItem
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(name = CHARTS_SCOPE_NAME)
internal class ChartResultsMapper : Mapper<SingleChartDomainModel, SingleChartUiModel> {

    override fun invoke(data: SingleChartDomainModel): SingleChartUiModel = SingleChartUiModel(
        name = data.name,
        properties = calculateProperty(data.values),
    )

    private fun calculateProperty(
        items: List<SingleChartDomainItem>,
    ): ImmutableList<SingleChartUiProperty> = items
        .map { item ->
            SingleChartUiProperty(
                timeX = item.xValue,
                valueY = item.yValue,
            )
        }
        .toImmutableList()
}
