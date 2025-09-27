package io.github.stslex.workeeper.feature.charts.domain.model

internal data class SingleChartDomainModel(
    val name: String,
    val dateType: ChartDataType,
    val values: List<SingleChartDomainItem>,
)

internal data class SingleChartDomainItem(
    val xValue: Float,
    val yValue: Float?,
)
