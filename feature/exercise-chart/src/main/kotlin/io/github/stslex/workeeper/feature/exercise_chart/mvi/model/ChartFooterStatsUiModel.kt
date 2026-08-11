// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import androidx.compose.runtime.Stable

/**
 * The three `.statrow`s (extraction §4.7). Values are the mockup's `fmt()` — rounded,
 * thousand-grouped — with the unit as its own dimmer span; [unit] is shared by all three
 * rows (one metric, one unit) and null for weightless exercises, whose values are rep
 * plurals with the unit built into the words.
 */
@Stable
data class ChartFooterStatsUiModel(
    val minTitle: String,
    val minValue: String,
    val maxTitle: String,
    val maxValue: String,
    val lastTitle: String,
    val lastValue: String,
    val unit: String?,
)
