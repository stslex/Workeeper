// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.time

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class WeekWindowTest {

    private val zone = TimeZone.of("Europe/Moscow")

    private fun millisOf(dateTime: LocalDateTime): Long =
        dateTime.toInstant(zone).toEpochMilliseconds()

    // 2026-08-05 is a Wednesday; its ISO week runs Mon 2026-08-03 .. Sun 2026-08-09.
    private val wednesdayNoon = millisOf(LocalDateTime(2026, 8, 5, 12, 0))

    @Test
    fun `window of a Wednesday starts on that week's Monday midnight`() {
        val window = weekWindowOf(wednesdayNoon, zone)
        assertEquals(millisOf(LocalDateTime(2026, 8, 3, 0, 0)), window.startMillis)
    }

    @Test
    fun `window of a Wednesday ends on next Monday midnight, exclusive`() {
        val window = weekWindowOf(wednesdayNoon, zone)
        assertEquals(millisOf(LocalDateTime(2026, 8, 10, 0, 0)), window.endMillis)
    }

    @Test
    fun `a Monday belongs to the week it starts`() {
        val mondayMorning = millisOf(LocalDateTime(2026, 8, 3, 0, 30))
        val window = weekWindowOf(mondayMorning, zone)
        assertEquals(millisOf(LocalDateTime(2026, 8, 3, 0, 0)), window.startMillis)
    }

    @Test
    fun `a Sunday night belongs to the week it ends`() {
        val sundayNight = millisOf(LocalDateTime(2026, 8, 9, 23, 59))
        val window = weekWindowOf(sundayNight, zone)
        assertEquals(millisOf(LocalDateTime(2026, 8, 3, 0, 0)), window.startMillis)
    }

    @Test
    fun `weekday index is Monday-first`() {
        assertEquals(0, weekdayIndexOf(millisOf(LocalDateTime(2026, 8, 3, 10, 0)), zone))
        assertEquals(2, weekdayIndexOf(wednesdayNoon, zone))
        assertEquals(6, weekdayIndexOf(millisOf(LocalDateTime(2026, 8, 9, 10, 0)), zone))
    }

    @Test
    fun `weekday index respects the zone, not UTC`() {
        // 2026-08-03 01:00 Moscow is still 2026-08-02 (Sunday) in UTC.
        val mondaySmallHours = millisOf(LocalDateTime(2026, 8, 3, 1, 0))
        assertEquals(0, weekdayIndexOf(mondaySmallHours, zone))
        assertEquals(6, weekdayIndexOf(mondaySmallHours, TimeZone.UTC))
    }
}
