package io.github.stslex.workeeper.feature.charts.ui.mvi.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

@Stable
internal interface ChartsState {

    @Stable
    data object Loading : ChartsState

    @Stable
    data object Empty : ChartsState

    @Stable
    data class Content(
        val charts: ImmutableList<SingleChartUiModel>,
    ) : ChartsState
}
