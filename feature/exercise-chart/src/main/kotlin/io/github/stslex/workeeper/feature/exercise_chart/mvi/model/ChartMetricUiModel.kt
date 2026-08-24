// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import io.github.stslex.workeeper.feature.exercise_chart.R

/**
 * The Y-axis fold function for weighted exercises, hidden in UI for weightless ones. Declaration
 * order is the metric toggle's tab order; [labelRes] is the tab word, [nameRes] the full name.
 */
enum class ChartMetricUiModel(val labelRes: Int, val nameRes: Int) {
    HEAVIEST_WEIGHT(
        R.string.feature_exercise_chart_metric_heaviest,
        R.string.feature_exercise_chart_metric_name_heaviest,
    ),
    VOLUME_PER_SESSION(
        R.string.feature_exercise_chart_metric_session,
        R.string.feature_exercise_chart_metric_name_session,
    ),
    VOLUME_PER_SET(
        R.string.feature_exercise_chart_metric_volume,
        R.string.feature_exercise_chart_metric_name_volume,
    ),
}
