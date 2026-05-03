// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.model

/**
 * The Y-axis fold function for weighted exercises. Hidden in UI for weightless exercises
 * (chart always plots reps then) but the fold logic guards both branches.
 */
internal enum class ChartMetricDomain {
    HEAVIEST_WEIGHT,
    VOLUME_PER_SET,
}
