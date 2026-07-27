// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain

import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartMetricDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPresetDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.HistoryEntryDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.HistorySetDomain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertNotNull

internal class ChartFolderTest {

    private val zone: ZoneId = ZoneOffset.UTC

    @Test
    fun `empty history returns empty points and null footer`() {
        val result = bucketAndFold(
            history = emptyList(),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertTrue(result.points.isEmpty())
        assertNull(result.footer)
    }

    @Test
    fun `single set produces single point with min equal max equal last`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28),
                    sessionUuid = "s1",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(1, result.points.size)
        val point = result.points.first()
        assertEquals(100.0, point.value)
        assertEquals(LocalDate.of(2026, 4, 28), point.day)
        assertEquals(1, point.setCount)
        assertNotNull(result.footer)
        val sole = result.points.single()
        assertEquals(sole, result.footer?.min)
        assertEquals(sole, result.footer?.max)
        assertEquals(sole, result.footer?.last)
    }

    @Test
    fun `multiple days are sorted ascending and use max-of-day`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28),
                    sessionUuid = "s1",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 29),
                    sessionUuid = "s2",
                    sets = listOf(
                        set(weight = 90.0, reps = 8),
                        set(weight = 110.0, reps = 3),
                    ),
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 27),
                    sessionUuid = "s3",
                    sets = listOf(set(weight = 95.0, reps = 6)),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        val days = result.points.map { it.day }
        assertEquals(
            listOf(
                LocalDate.of(2026, 4, 27),
                LocalDate.of(2026, 4, 28),
                LocalDate.of(2026, 4, 29),
            ),
            days,
        )
        // Day 29 wins because 110 > both other sets that day.
        val day29 = result.points.first { it.day == LocalDate.of(2026, 4, 29) }
        assertEquals(110.0, day29.value)
        assertEquals(2, day29.setCount)
    }

    @Test
    fun `two sessions same day collapse to higher and setCount sums all`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 9),
                    sessionUuid = "morning",
                    sets = listOf(
                        set(weight = 80.0, reps = 5),
                        set(weight = 80.0, reps = 5),
                    ),
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 18),
                    sessionUuid = "evening",
                    sets = listOf(set(weight = 100.0, reps = 3)),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        val point = result.points.single()
        assertEquals(100.0, point.value)
        assertEquals(3, point.setCount)
        assertEquals("evening", point.sessionUuid)
    }

    @Test
    fun `setCount counts sets the chart cannot plot`() {
        // Eligibility picks the day's winner; it must not shrink "N sets this day". A weighted
        // set logged without a weight is unplottable but was still performed.
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28),
                    sessionUuid = "s1",
                    sets = listOf(
                        set(weight = 100.0, reps = 5),
                        set(weight = 110.0, reps = 3),
                        set(weight = null, reps = 8),
                    ),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        val point = result.points.single()
        assertEquals(110.0, point.value)
        assertEquals(3, point.reps)
        assertEquals(3, point.setCount)
    }

    @Test
    fun `setCount keeps the tooltip label when only one set of the day is plottable`() {
        // `setCount == 1` is not just an off-by-one: the UI mapper gates the tooltip's
        // "N sets this day" line on `setCount > 1`, so undercounting here erases the line.
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28),
                    sessionUuid = "s1",
                    sets = listOf(
                        set(weight = 100.0, reps = 5),
                        set(weight = null, reps = 8),
                    ),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        val point = result.points.single()
        assertEquals(100.0, point.value)
        assertEquals(2, point.setCount)
    }

    @Test
    fun `tie on metric value selects earlier finishedAt session`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 18),
                    sessionUuid = "evening",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 9),
                    sessionUuid = "morning",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals("morning", result.points.single().sessionUuid)
    }

    @Test
    fun `weightless exercise plots reps regardless of metric`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28),
                    sessionUuid = "s1",
                    sets = listOf(
                        set(weight = null, reps = 8),
                        set(weight = null, reps = 12),
                    ),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.VOLUME_PER_SET,
            exerciseType = ExerciseTypeDomain.WEIGHTLESS,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(12.0, result.points.single().value)
    }

    @Test
    fun `preset window filters out sets older than start`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2025, 12, 1),
                    sessionUuid = "out-of-window",
                    sets = listOf(set(weight = 999.0, reps = 1)),
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 1),
                    sessionUuid = "in-window",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.MONTH_1,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 4, 25),
            zoneId = zone,
        )

        assertEquals(1, result.points.size)
        assertEquals("in-window", result.points.single().sessionUuid)
    }

    // DST-pinned window-boundary tests. These run in a real DST-observing zone
    // (America/New_York) at that zone's actual 2026 transition dates, with `now` near local
    // midnight — the only conditions under which the epoch-millis <-> local-calendar-day
    // window-start conversion can drift a day. The existing suite runs in UTC (no DST) and so
    // can never exercise this class of bug.

    @Test
    fun `MONTH_1 window start tracks calendar days across a DST spring-forward`() {
        // 2026-03-08 springs forward (a 23h day). With `now` just after local midnight, a naive
        // `now - 30 * 24h` start drifts back an hour, across the transition, onto the PREVIOUS
        // calendar day. The window must track calendar days, not fixed-length millis.
        val nyZone = ZoneId.of("America/New_York")
        val now = zonedMillis(nyZone, 2026, 3, 20, hour = 0, minute = 30)

        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = zonedMillis(nyZone, 2026, 3, 10, hour = 12),
                    sessionUuid = "in-window",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.MONTH_1,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = now,
            zoneId = nyZone,
        )

        // 30 calendar days before 2026-03-20 is 02-18, not the 02-17 a naive 30*24h lands on.
        assertEquals(LocalDate.of(2026, 2, 18), result.windowStartDay)
    }

    @Test
    fun `MONTH_1 window start tracks calendar days across a DST fall-back`() {
        // 2026-11-01 falls back (a 25h day). With `now` just before local midnight, a naive
        // `now - 30 * 24h` start drifts forward an hour, across the transition, onto the NEXT
        // calendar day.
        val nyZone = ZoneId.of("America/New_York")
        val now = zonedMillis(nyZone, 2026, 11, 15, hour = 23, minute = 30)

        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = zonedMillis(nyZone, 2026, 11, 5, hour = 12),
                    sessionUuid = "in-window",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.MONTH_1,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = now,
            zoneId = nyZone,
        )

        // 30 calendar days before 2026-11-15 is 10-16, not the 10-17 a naive 30*24h lands on.
        assertEquals(LocalDate.of(2026, 10, 16), result.windowStartDay)
    }

    @Test
    fun `ALL preset includes very old sets`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2018, 1, 1),
                    sessionUuid = "ancient",
                    sets = listOf(set(weight = 60.0, reps = 5)),
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 1),
                    sessionUuid = "recent",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.ALL,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(2, result.points.size)
    }

    @Test
    fun `volume metric multiplies weight by reps`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28),
                    sessionUuid = "s1",
                    sets = listOf(
                        set(weight = 100.0, reps = 5), // weight×reps = 500
                        set(weight = 80.0, reps = 10), // weight×reps = 800
                    ),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.VOLUME_PER_SET,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(800.0, result.points.single().value)
    }

    @Test
    fun `volume tie across sessions keeps the earlier session`() {
        // Under VOLUME_PER_SET a tie is a trade of weight against reps, so a reps tiebreak
        // would mean "prefer the lighter set" — and would move the tooltip's navigation
        // target to the later session. Volume is not a PR metric; earliest finishedAt wins.
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 9),
                    sessionUuid = "morning",
                    sets = listOf(set(weight = 100.0, reps = 2)), // weight×reps = 200
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 18),
                    sessionUuid = "evening",
                    sets = listOf(set(weight = 50.0, reps = 4)), // weight×reps = 200
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.VOLUME_PER_SET,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        val point = result.points.single()
        assertEquals(200.0, point.value)
        assertEquals("morning", point.sessionUuid)
        assertEquals(100.0, point.weight)
        assertEquals(2, point.reps)
    }

    @Test
    fun `volume tie inside one session keeps the earlier position`() {
        // A drop set ties with itself: every set of a session shares one finishedAt, so the
        // position order the history query delivers is what decides, via a stable sort.
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28),
                    sessionUuid = "s1",
                    sets = listOf(
                        set(weight = 100.0, reps = 2), // weight×reps = 200
                        set(weight = 50.0, reps = 4), // weight×reps = 200
                    ),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.VOLUME_PER_SET,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        val point = result.points.single()
        assertEquals(200.0, point.value)
        assertEquals(100.0, point.weight)
        assertEquals(2, point.reps)
    }

    @Test
    fun `ALL preset with single point tightens window to minimum plus-minus 3 days`() {
        val pointDay = LocalDate.of(2026, 4, 20)
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(pointDay.year, pointDay.monthValue, pointDay.dayOfMonth),
                    sessionUuid = "s1",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.ALL,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(pointDay.minusDays(3), result.windowStartDay)
        assertEquals(pointDay.plusDays(3), result.windowEndDay)
    }

    @Test
    fun `ALL preset with nearby two points uses minimum padding around them`() {
        val firstDay = LocalDate.of(2026, 4, 18)
        val lastDay = LocalDate.of(2026, 4, 20)
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(firstDay.year, firstDay.monthValue, firstDay.dayOfMonth),
                    sessionUuid = "early",
                    sets = listOf(set(weight = 80.0, reps = 5)),
                ),
                entry(
                    finishedAt = utcMillis(lastDay.year, lastDay.monthValue, lastDay.dayOfMonth),
                    sessionUuid = "late",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.ALL,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(firstDay.minusDays(3), result.windowStartDay)
        assertEquals(lastDay.plusDays(3), result.windowEndDay)
    }

    @Test
    fun `ALL preset with wider two point span pads by half span`() {
        val firstDay = LocalDate.of(2026, 3, 21)
        val lastDay = LocalDate.of(2026, 4, 20)
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(firstDay.year, firstDay.monthValue, firstDay.dayOfMonth),
                    sessionUuid = "early",
                    sets = listOf(set(weight = 80.0, reps = 5)),
                ),
                entry(
                    finishedAt = utcMillis(lastDay.year, lastDay.monthValue, lastDay.dayOfMonth),
                    sessionUuid = "late",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.ALL,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(firstDay.minusDays(15), result.windowStartDay)
        assertEquals(lastDay.plusDays(15), result.windowEndDay)
    }

    @Test
    fun `ALL preset with three or more points uses first day to today without padding`() {
        val firstDay = LocalDate.of(2026, 3, 10)
        val today = LocalDate.of(2026, 5, 1)
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(firstDay.year, firstDay.monthValue, firstDay.dayOfMonth),
                    sessionUuid = "s1",
                    sets = listOf(set(weight = 80.0, reps = 5)),
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 1),
                    sessionUuid = "s2",
                    sets = listOf(set(weight = 90.0, reps = 5)),
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 20),
                    sessionUuid = "s3",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.ALL,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(today.year, today.monthValue, today.dayOfMonth),
            zoneId = zone,
        )

        assertEquals(firstDay, result.windowStartDay)
        assertEquals(today, result.windowEndDay)
    }

    @Test
    fun `bounded preset window is preset start to today regardless of point count`() {
        val today = LocalDate.of(2026, 5, 1)
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 25),
                    sessionUuid = "in-window",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.MONTH_1,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(today.year, today.monthValue, today.dayOfMonth),
            zoneId = zone,
        )

        // 1M = 30 days, so windowStart is exactly today - 30 days.
        assertEquals(today.minusDays(30), result.windowStartDay)
        assertEquals(today, result.windowEndDay)
    }

    @Test
    fun `empty fold result has null window`() {
        val result = bucketAndFold(
            history = emptyList(),
            preset = ChartPresetDomain.ALL,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertNull(result.windowStartDay)
        assertNull(result.windowEndDay)
    }

    @Test
    fun `single point case still produces footer with min equal max`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28),
                    sessionUuid = "s1",
                    sets = listOf(set(weight = 80.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertNotNull(result.footer)
        val sole = result.points.single()
        assertEquals(sole, result.footer?.min)
        assertEquals(sole, result.footer?.max)
        assertEquals(sole, result.footer?.last)
    }

    private fun entry(
        finishedAt: Long,
        sessionUuid: String,
        sets: List<HistorySetDomain>,
    ): HistoryEntryDomain = HistoryEntryDomain(
        sessionUuid = sessionUuid,
        finishedAt = finishedAt,
        sets = sets,
    )

    private fun set(weight: Double?, reps: Int): HistorySetDomain = HistorySetDomain(
        weight = weight,
        reps = reps,
    )

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
    ): Long = LocalDate.of(year, month, day)
        .atTime(hour, 0)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

    private fun zonedMillis(
        zone: ZoneId,
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
    ): Long = LocalDate.of(year, month, day)
        .atTime(hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
