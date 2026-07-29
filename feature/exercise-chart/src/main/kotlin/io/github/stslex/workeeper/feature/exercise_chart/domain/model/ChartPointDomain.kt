// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.model

import java.time.LocalDate

/**
 * One plotted day. Under the per-set metrics the point is a concrete set and `weight`/`reps`
 * are that set's. Under `ChartMetricDomain.VOLUME_PER_SESSION` the point is a session
 * aggregate — no single set is "the" point — so `weight` is null and `reps` is 0; consumers
 * must branch on the metric before reading either.
 */
data class ChartPointDomain(
    val day: LocalDate,
    val dayMillis: Long,
    val value: Double,
    val sessionUuid: String,
    val weight: Double?,
    val reps: Int,
    val setCount: Int,
)
