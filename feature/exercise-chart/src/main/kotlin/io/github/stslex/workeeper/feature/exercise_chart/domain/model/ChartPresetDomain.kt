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
     * Inclusive window start as epoch millis, or `null` for [ALL]. Subtracts [windowDays]
     * **calendar** days from [now] in [zone], preserving local time-of-day, so a window that
     * spans a DST transition stays aligned to the correct calendar boundary. A naive
     * `now - days * 24h` drifts by the transition's offset and, when [now] is within an hour
     * of local midnight, lands the boundary on the wrong day — a bug a UTC test zone (which
     * has no DST) can never surface.
     */
    fun windowStartMillis(now: Long, zone: ZoneId): Long? = windowDays?.let { days ->
        Instant.ofEpochMilli(now).atZone(zone).minusDays(days).toInstant().toEpochMilli()
    }
}
