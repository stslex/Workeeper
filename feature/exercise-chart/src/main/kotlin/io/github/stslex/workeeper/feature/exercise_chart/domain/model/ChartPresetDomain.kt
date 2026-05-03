// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.model

/**
 * Date filter presets for the chart screen. `windowStartMillis(now)` returns the inclusive
 * start of the visible window (or `null` for [ALL], meaning unbounded).
 */
internal enum class ChartPresetDomain(
    private val windowDays: Long?,
) {
    MONTH_1(windowDays = 30L),
    MONTHS_3(windowDays = 90L),
    YEAR_1(windowDays = 365L),
    ALL(windowDays = null),
    ;

    fun windowStartMillis(now: Long): Long? = windowDays?.let { days ->
        now - days * MILLIS_PER_DAY
    }

    private companion object {
        const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L
    }
}
