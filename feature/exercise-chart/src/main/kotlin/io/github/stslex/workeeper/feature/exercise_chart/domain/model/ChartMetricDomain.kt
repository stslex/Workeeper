// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.model

/**
 * The Y-axis fold function for weighted exercises. Hidden in UI for weightless exercises
 * (chart always plots reps then) but the fold logic guards both branches.
 *
 * Declaration order is presentation order (`entries` drives the metric toggle), which is the
 * mockup's tab order: Вес · Сессия · Подход.
 */
enum class ChartMetricDomain {
    HEAVIEST_WEIGHT,
    VOLUME_PER_SESSION,
    VOLUME_PER_SET,
}
