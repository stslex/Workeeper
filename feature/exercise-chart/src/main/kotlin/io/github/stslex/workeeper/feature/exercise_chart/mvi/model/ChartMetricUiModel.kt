// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import io.github.stslex.workeeper.feature.exercise_chart.R

/**
 * The Y-axis fold function for weighted exercises. Hidden in UI for weightless exercises
 * (chart always plots reps then) but the mapper guards both branches.
 */
internal enum class ChartMetricUiModel(val labelRes: Int) {
    HEAVIEST_WEIGHT(R.string.feature_exercise_chart_metric_heaviest),
    VOLUME_PER_SET(R.string.feature_exercise_chart_metric_volume),
}
