package io.github.stslex.workeeper.feature.charts.domain.interactor

import io.github.stslex.workeeper.feature.charts.domain.model.ChartParams
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainModel

internal interface ChartsInteractor {

    suspend fun getChartsData(params: ChartParams): List<SingleChartDomainModel>
}
