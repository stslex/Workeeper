package io.github.stslex.workeeper.feature.charts.mvi.model

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainItem
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@ViewModelScoped
internal class ChartResultsMapper @Inject constructor() : Mapper<SingleChartDomainModel, SingleChartUiModel> {

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
