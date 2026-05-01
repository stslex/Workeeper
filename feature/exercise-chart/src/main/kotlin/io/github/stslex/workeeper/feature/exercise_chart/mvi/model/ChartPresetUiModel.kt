// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import io.github.stslex.workeeper.feature.exercise_chart.R

/**
 * Date filter presets for the chart screen. `windowStartMillis(now)` returns the inclusive
 * start of the visible window (or `null` for [ALL], meaning unbounded).
 */
internal enum class ChartPresetUiModel(
    val labelRes: Int,
    private val windowDays: Long?,
) {
    MONTH_1(R.string.feature_exercise_chart_preset_1m, windowDays = 30L),
    MONTHS_3(R.string.feature_exercise_chart_preset_3m, windowDays = 90L),
    YEAR_1(R.string.feature_exercise_chart_preset_1y, windowDays = 365L),
    ALL(R.string.feature_exercise_chart_preset_all, windowDays = null),
    ;

    fun windowStartMillis(now: Long): Long? = windowDays?.let { days ->
        now - days * MILLIS_PER_DAY
    }

    private companion object {
        const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L
    }
}
