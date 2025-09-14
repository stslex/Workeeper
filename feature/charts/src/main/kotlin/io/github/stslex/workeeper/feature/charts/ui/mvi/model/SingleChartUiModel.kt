package io.github.stslex.workeeper.feature.charts.ui.mvi.model

import androidx.compose.runtime.Stable

@Stable
internal data class SingleChartUiModel(
    val name: String,
    val properties: List<Double>,
)