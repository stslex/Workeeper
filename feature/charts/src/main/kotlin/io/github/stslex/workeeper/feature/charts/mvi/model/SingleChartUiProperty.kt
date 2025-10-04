package io.github.stslex.workeeper.feature.charts.mvi.model

import androidx.compose.runtime.Stable

@Stable
internal data class SingleChartUiProperty(
    val timeX: Float,
    val valueY: Float?,
)
