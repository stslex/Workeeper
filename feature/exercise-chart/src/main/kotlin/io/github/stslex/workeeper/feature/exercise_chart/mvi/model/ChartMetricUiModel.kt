// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import io.github.stslex.workeeper.feature.exercise_chart.R

/**
 * The Y-axis fold function for weighted exercises. Hidden in UI for weightless exercises
 * (chart always plots reps then) but the mapper guards both branches.
 *
 * Declaration order is presentation order (`entries` drives the metric toggle), which is the
 * mockup's tab order: Вес · Сессия · Подход. Each metric carries two strings, as the mockup
 * does: [labelRes] is the tab's short word, [nameRes] the readout's full name.
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
