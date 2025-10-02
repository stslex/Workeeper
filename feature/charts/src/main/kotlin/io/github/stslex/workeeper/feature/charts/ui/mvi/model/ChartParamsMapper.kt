package io.github.stslex.workeeper.feature.charts.ui.mvi.model

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.result.Mapper
import io.github.stslex.workeeper.feature.charts.domain.model.ChartParams
import io.github.stslex.workeeper.feature.charts.domain.model.ChartsDomainType
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore
import javax.inject.Inject

@ViewModelScoped
internal class ChartParamsMapper @Inject constructor() : Mapper<ChartsStore.State, ChartParams> {

    override fun invoke(data: ChartsStore.State): ChartParams = ChartParams(
        startDate = data.startDate.value,
        endDate = data.endDate.value,
        name = data.name,
        type = when (data.type) {
            ChartsType.TRAINING -> ChartsDomainType.TRAINING
            ChartsType.EXERCISE -> ChartsDomainType.EXERCISE
        },
    )
}
