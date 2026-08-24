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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
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
    fun `multiple sessions are sorted by completion time and use each session maximum`() {
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
        // The Apr 29 session contributes its own best set.
        val day29 = result.points.first { it.day == LocalDate.of(2026, 4, 29) }
        assertEquals(110.0, day29.value)
        assertEquals(2, day29.setCount)
    }

    @ParameterizedTest
    @EnumSource(ChartMetricDomain::class)
    fun `two same-day sessions remain separate for every metric`(metric: ChartMetricDomain) {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 9),
                    sessionUuid = "morning",
                    sets = listOf(
                        set(weight = 80.0, reps = 5),
                        set(weight = 70.0, reps = 10),
                    ),
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 18),
                    sessionUuid = "evening",
                    sets = listOf(set(weight = 100.0, reps = 3)),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = metric,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(listOf("morning", "evening"), result.points.map { it.sessionUuid })
        assertEquals(listOf(2, 1), result.points.map { it.setCount })
        assertEquals(1, result.points.map { it.day }.distinct().size)
        assertEquals(
            when (metric) {
                ChartMetricDomain.HEAVIEST_WEIGHT -> listOf(80.0, 100.0)
                ChartMetricDomain.VOLUME_PER_SET -> listOf(700.0, 300.0)
                ChartMetricDomain.VOLUME_PER_SESSION -> listOf(1_100.0, 300.0)
            },
            result.points.map { it.value },
        )
    }

    @ParameterizedTest
    @EnumSource(ChartMetricDomain::class)
    fun `two different-day sessions remain chronological for every metric`(
        metric: ChartMetricDomain,
    ) {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 29, hour = 18),
                    sessionUuid = "later",
                    sets = listOf(set(weight = 100.0, reps = 3)),
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 9),
                    sessionUuid = "earlier",
                    sets = listOf(set(weight = 80.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = metric,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(listOf("earlier", "later"), result.points.map { it.sessionUuid })
        assertEquals(
            listOf(LocalDate.of(2026, 4, 28), LocalDate.of(2026, 4, 29)),
            result.points.map { it.day },
        )
    }

    @Test
    fun `setCount counts sets the chart cannot plot`() {
        // Eligibility picks the winner; it must not shrink the set count.
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
    fun `setCount keeps the readout accurate when only one set is plottable`() {
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
    fun `equal same-day values keep both sessions in completion order`() {
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

        assertEquals(listOf("morning", "evening"), result.points.map { it.sessionUuid })
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

    // DST-pinned window-boundary tests: a real DST zone (America/New_York) at its 2026
    // transitions, with `now` near local midnight. The rest of the suite runs in UTC.

    @Test
    fun `MONTH_1 filter tracks calendar days across a DST spring-forward`() {
        // 2026-03-08 springs forward. A naive `now - 30 * 24h` boundary drifts back onto the
        // previous calendar day and would admit an entry the calendar window excludes.
        val nyZone = ZoneId.of("America/New_York")
        val now = zonedMillis(nyZone, 2026, 3, 20, hour = 0, minute = 30)

        val result = bucketAndFold(
            history = listOf(
                entry(
                    // 02-18 00:00 — after the naive boundary, before the calendar one.
                    finishedAt = zonedMillis(nyZone, 2026, 2, 18, hour = 0, minute = 0),
                    sessionUuid = "naive-would-admit",
                    sets = listOf(set(weight = 200.0, reps = 1)),
                ),
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

        assertEquals(listOf("in-window"), result.points.map { it.sessionUuid })
    }

    @Test
    fun `MONTH_1 filter tracks calendar days across a DST fall-back`() {
        // 2026-11-01 falls back. A naive boundary drifts forward onto the next calendar day
        // and would drop an entry the calendar window includes; 10-17 00:00 discriminates.
        val nyZone = ZoneId.of("America/New_York")
        val now = zonedMillis(nyZone, 2026, 11, 15, hour = 23, minute = 30)

        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = zonedMillis(nyZone, 2026, 10, 17, hour = 0, minute = 0),
                    sessionUuid = "calendar-includes",
                    sets = listOf(set(weight = 100.0, reps = 5)),
                ),
            ),
            preset = ChartPresetDomain.MONTH_1,
            metric = ChartMetricDomain.HEAVIEST_WEIGHT,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = now,
            zoneId = nyZone,
        )

        assertEquals(listOf("calendar-includes"), result.points.map { it.sessionUuid })
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
    fun `volume tie across sessions preserves both in completion order`() {
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

        assertEquals(listOf("morning", "evening"), result.points.map { it.sessionUuid })
        assertEquals(listOf(200.0, 200.0), result.points.map { it.value })
        assertEquals(listOf(100.0, 50.0), result.points.map { it.weight })
        assertEquals(listOf(2, 4), result.points.map { it.reps })
    }

    @Test
    fun `volume tie inside one session keeps the earlier position`() {
        // A drop set ties with itself; position order from the history query decides.
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
    fun `session volume sums every eligible set of the session`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28),
                    sessionUuid = "s1",
                    sets = listOf(
                        set(weight = 100.0, reps = 5), // 500
                        set(weight = 80.0, reps = 10), // 800
                    ),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.VOLUME_PER_SESSION,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        val point = result.points.single()
        assertEquals(1300.0, point.value)
        assertEquals("s1", point.sessionUuid)
        // Aggregate point: no single set is "the" point.
        assertNull(point.weight)
        assertEquals(0, point.reps)
        assertEquals(2, point.setCount)
    }

    @Test
    fun `session volume excludes ineligible sets from the sum but not from setCount`() {
        // A weight-null set on a WEIGHTED exercise contributes nothing: the shared eligibility
        // floor is the rule, not an arithmetic accident.
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28),
                    sessionUuid = "s1",
                    sets = listOf(
                        set(weight = 100.0, reps = 5), // 500
                        set(weight = null, reps = 8), // ineligible
                        set(weight = 90.0, reps = 0), // ineligible
                    ),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.VOLUME_PER_SESSION,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        val point = result.points.single()
        assertEquals(500.0, point.value)
        assertEquals(3, point.setCount)
    }

    @Test
    fun `same-day session volume retains each session total`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 9),
                    sessionUuid = "morning",
                    sets = listOf(
                        set(weight = 80.0, reps = 5), // 400
                        set(weight = 80.0, reps = 5), // 400
                    ),
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 18),
                    sessionUuid = "evening",
                    sets = listOf(set(weight = 100.0, reps = 3)), // 300
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.VOLUME_PER_SESSION,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(listOf("morning", "evening"), result.points.map { it.sessionUuid })
        assertEquals(listOf(800.0, 300.0), result.points.map { it.value })
        assertEquals(listOf(2, 1), result.points.map { it.setCount })
    }

    @Test
    fun `session total tie preserves both sessions in completion order`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 18),
                    sessionUuid = "evening",
                    sets = listOf(set(weight = 50.0, reps = 8)), // 400
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 28, hour = 9),
                    sessionUuid = "morning",
                    sets = listOf(set(weight = 100.0, reps = 4)), // 400
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.VOLUME_PER_SESSION,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(listOf("morning", "evening"), result.points.map { it.sessionUuid })
        assertEquals(listOf(400.0, 400.0), result.points.map { it.value })
    }

    @Test
    fun `weightless session volume sums reps`() {
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
            metric = ChartMetricDomain.VOLUME_PER_SESSION,
            exerciseType = ExerciseTypeDomain.WEIGHTLESS,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(20.0, result.points.single().value)
    }

    @Test
    fun `session volume footer tracks session totals across days`() {
        val result = bucketAndFold(
            history = listOf(
                entry(
                    finishedAt = utcMillis(2026, 4, 26),
                    sessionUuid = "s1",
                    sets = listOf(set(weight = 100.0, reps = 5)), // 500
                ),
                entry(
                    finishedAt = utcMillis(2026, 4, 28),
                    sessionUuid = "s2",
                    sets = listOf(
                        set(weight = 60.0, reps = 10), // 600
                        set(weight = 60.0, reps = 10), // 600
                    ),
                ),
            ),
            preset = ChartPresetDomain.MONTHS_3,
            metric = ChartMetricDomain.VOLUME_PER_SESSION,
            exerciseType = ExerciseTypeDomain.WEIGHTED,
            now = utcMillis(2026, 5, 1),
            zoneId = zone,
        )

        assertEquals(500.0, result.footer?.min?.value)
        assertEquals(1200.0, result.footer?.max?.value)
        assertEquals(1200.0, result.footer?.last?.value)
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
