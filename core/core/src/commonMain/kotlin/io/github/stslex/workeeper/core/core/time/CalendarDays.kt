// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.time

import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Whole calendar days between two instants in [timeZone] — date arithmetic, not 24-hour
 * buckets: a session that finished yesterday at 23:00 is «1 день назад» at 01:00 today,
 * where elapsed-millis division would say zero. Negative when [toMillis] precedes
 * [fromMillis]; callers with monotonic inputs never see that.
 */
@OptIn(ExperimentalTime::class)
fun calendarDaysBetween(
    fromMillis: Long,
    toMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Int {
    val from = Instant.fromEpochMilliseconds(fromMillis).toLocalDateTime(timeZone).date
    val to = Instant.fromEpochMilliseconds(toMillis).toLocalDateTime(timeZone).date
    return from.daysUntil(to)
}
