// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import androidx.compose.runtime.Stable
import java.time.LocalDate

/**
 * One point on the chart — one calendar day. [value] is the Y value after the active metric
 * fold. [sessionUuid] is the session that owned the winning set; tooltip tap navigates to
 * that session's [io.github.stslex.workeeper.core.ui.navigation.Screen.PastSession].
 */
@Stable
internal data class ChartPointUiModel(
    val day: LocalDate,
    val dayMillis: Long,
    val value: Double,
    val sessionUuid: String,
    val weight: Double?,
    val reps: Int,
    val setCount: Int,
)
