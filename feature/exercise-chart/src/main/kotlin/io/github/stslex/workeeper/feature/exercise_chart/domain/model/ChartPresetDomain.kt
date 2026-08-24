// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain.model

import java.time.Instant
import java.time.ZoneId

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
     * [zone] rather than fixed 24h spans, so a DST transition cannot shift the boundary.
     */
    fun windowStartMillis(now: Long, zone: ZoneId): Long? = windowDays?.let { days ->
        Instant.ofEpochMilli(now).atZone(zone).minusDays(days).toInstant().toEpochMilli()
    }
}
