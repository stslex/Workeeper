// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

/**
 * The Y-axis fold function for weighted exercises, hidden in UI for weightless ones. Declaration
 * order is the metric toggle's tab order; MetricTabs maps each entry to its tab word and the
 * readout mapper to its full name (S3).
 */
enum class ChartMetricUiModel {
    HEAVIEST_WEIGHT,
    VOLUME_PER_SESSION,
    VOLUME_PER_SET,
}
