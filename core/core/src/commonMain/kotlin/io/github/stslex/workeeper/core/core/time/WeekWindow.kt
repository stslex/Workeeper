// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val DAYS_IN_WEEK = 7

/** A calendar week as an epoch-millis half-open range: `[startMillis, endMillis)`. */
data class WeekWindow(
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * The calendar week containing [nowMillis], Monday-first (ISO 8601).
 * GUARD: both bounds are computed date-side — `start + 7 * 24h` drifts across a DST transition.
 */
@OptIn(ExperimentalTime::class)
fun weekWindowOf(
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): WeekWindow {
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
    val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    val nextMonday = monday.plus(DAYS_IN_WEEK, DateTimeUnit.DAY)
    return WeekWindow(
        startMillis = monday.atStartOfDayIn(timeZone).toEpochMilliseconds(),
        endMillis = nextMonday.atStartOfDayIn(timeZone).toEpochMilliseconds(),
    )
}

/** Weekday of [millis] in [timeZone] as a Monday-first index: 0 = Monday … 6 = Sunday. */
@OptIn(ExperimentalTime::class)
fun weekdayIndexOf(
    millis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Int {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
    return date.dayOfWeek.isoDayNumber - 1
}
