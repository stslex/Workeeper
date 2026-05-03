// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.model

import java.time.LocalDate

internal data class ChartPointDomain(
    val day: LocalDate,
    val dayMillis: Long,
    val value: Double,
    val sessionUuid: String,
    val weight: Double?,
    val reps: Int,
    val setCount: Int,
)
