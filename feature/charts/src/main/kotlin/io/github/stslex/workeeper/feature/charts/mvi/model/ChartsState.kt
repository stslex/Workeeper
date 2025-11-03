package io.github.stslex.workeeper.feature.charts.mvi.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

@Stable
internal sealed interface ChartsState {

    val content: Content?
        get() = this as? Content

    @Stable
    data object Loading : ChartsState

    @Stable
    data object Empty : ChartsState

    @Stable
    data class Content(
        val charts: ImmutableList<SingleChartUiModel>,
        val chartsTitles: ImmutableList<String>,
        val selectedChartIndex: Int,
    ) : ChartsState
}
