// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.time.Instant

/**
 * Date filter presets for the chart screen. [windowStartMillis] returns the inclusive
 * start of the visible window (or `null` for [ALL], meaning unbounded).
 */
enum class ChartPresetDomain(
    private val windowDays: Long?,
) {
    MONTH_1(windowDays = 30L),
    MONTHS_3(windowDays = 90L),
    YEAR_1(windowDays = 365L),
    ALL(windowDays = null),
    ;

    /**
     * Inclusive window start as epoch millis, or `null` for [ALL]. Subtracts calendar days in
     * [timeZone] rather than fixed 24h spans, so a DST transition cannot shift the boundary.
     */
    fun windowStartMillis(now: Long, timeZone: TimeZone): Long? = windowDays?.let { days ->
        Instant.fromEpochMilliseconds(now).minus(days, DateTimeUnit.DAY, timeZone).toEpochMilliseconds()
    }
}
