// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableMap

/** D3: the readout's catalog copy, resolved once per load so the scrub never suspends. */
@Stable
data class ChartReadoutCopyUiModel(
    val heaviestWeightName: String,
    val volumePerSessionName: String,
    val volumePerSetName: String,
    val kgUnit: String,
    val repsUnit: String,
    val recordLabel: String,
    val setCountLabels: ImmutableMap<Int, String>,
)
