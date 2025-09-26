package io.github.stslex.workeeper.feature.charts.ui.mvi.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

@Stable
internal data class SingleChartUiModel(
    val name: String,
    val properties: ImmutableList<SingleChartUiProperty>,
)