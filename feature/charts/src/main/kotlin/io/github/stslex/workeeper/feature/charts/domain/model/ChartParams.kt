package io.github.stslex.workeeper.feature.charts.domain.model

internal data class ChartParams(
    val startDate: Long,
    val endDate: Long,
    val name: String,
    val type: ChartsDomainType
)
