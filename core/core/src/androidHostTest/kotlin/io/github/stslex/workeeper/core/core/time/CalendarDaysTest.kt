// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.time

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class CalendarDaysTest {

    private val zone = TimeZone.of("Europe/Moscow")

    private fun millisOf(dateTime: LocalDateTime): Long =
        dateTime.toInstant(zone).toEpochMilliseconds()

    @Test
    fun `same day is zero days`() {
        assertEquals(
            0,
            calendarDaysBetween(
                fromMillis = millisOf(LocalDateTime(2026, 8, 5, 9, 0)),
                toMillis = millisOf(LocalDateTime(2026, 8, 5, 23, 0)),
                timeZone = zone,
            ),
        )
    }

    @Test
    fun `late yesterday to early today is one day, not zero`() {
        // Two hours of elapsed time across midnight — date arithmetic, not 24h buckets.
        assertEquals(
            1,
            calendarDaysBetween(
                fromMillis = millisOf(LocalDateTime(2026, 8, 4, 23, 0)),
                toMillis = millisOf(LocalDateTime(2026, 8, 5, 1, 0)),
                timeZone = zone,
            ),
        )
    }

    @Test
    fun `a full week is seven days`() {
        assertEquals(
            7,
            calendarDaysBetween(
                fromMillis = millisOf(LocalDateTime(2026, 7, 29, 12, 0)),
                toMillis = millisOf(LocalDateTime(2026, 8, 5, 12, 0)),
                timeZone = zone,
            ),
        )
    }

    @Test
    fun `the day boundary is the zone's, not UTC's`() {
        // 2026-08-05 01:00 Moscow is 2026-08-04 22:00 UTC — same UTC day as the from value.
        val from = millisOf(LocalDateTime(2026, 8, 4, 23, 30))
        val to = millisOf(LocalDateTime(2026, 8, 5, 1, 0))
        assertEquals(1, calendarDaysBetween(from, to, zone))
        assertEquals(0, calendarDaysBetween(from, to, TimeZone.UTC))
    }
}
