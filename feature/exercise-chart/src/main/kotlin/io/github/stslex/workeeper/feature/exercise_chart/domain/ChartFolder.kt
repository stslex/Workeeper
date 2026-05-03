// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.domain

import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFoldDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFooterStatsDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartMetricDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPointDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPresetDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.HistoryEntryDomain
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure folding logic for the chart screen — converts raw history into the points + footer
 * the canvas needs to render. Lives in the domain layer; the UI mapper consumes the
 * resulting [ChartFoldDomain] and produces UI types with locale-aware formatting.
 *
 * On the [ChartPresetDomain.ALL] preset, when the exercise has only 1-2 finished sessions
 * in its full history, the natural window (`first finished_at … today`) can stretch a
 * year-old single point across the whole canvas — points cluster against the right edge
 * and look like an outlier rather than the data they are. Tighten the window by padding
 * relative to the actual data span, with at least [MIN_PADDING_DAYS] on each side, so
 * sparse history reads as centred data, not as noise.
 */
private const val MIN_PADDING_DAYS = 3L

internal fun bucketAndFold(
    history: List<HistoryEntryDomain>,
    preset: ChartPresetDomain,
    metric: ChartMetricDomain,
    exerciseType: ExerciseTypeDomain,
    now: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): ChartFoldDomain {
    val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    val windowStart = preset.windowStartMillis(now)

    val flat = history
        .asSequence()
        .filter { entry -> windowStart == null || entry.finishedAt >= windowStart }
        .flatMap { entry ->
            val day = Instant.ofEpochMilli(entry.finishedAt).atZone(zoneId).toLocalDate()
            val dayMillis = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
            entry.sets.asSequence().map { set ->
                FlatSet(
                    day = day,
                    dayMillis = dayMillis,
                    sessionUuid = entry.sessionUuid,
                    finishedAt = entry.finishedAt,
                    weight = set.weight,
                    reps = set.reps,
                )
            }
        }
        .toList()

    if (flat.isEmpty()) return ChartFoldDomain(
        points = emptyList(),
        footer = null,
        windowStartDay = null,
        windowEndDay = null,
    )

    // The earliest finishedAt wins on tie, matching the v2.1 PR semantics.
    val foldComparator = compareByDescending<FlatSet> { f ->
        metricValue(f, metric, exerciseType)
    }.thenBy(FlatSet::finishedAt)

    val pointsByDay = flat
        .groupBy(FlatSet::day)
        .map { (_, dailySets) ->
            val winner = dailySets.sortedWith(foldComparator).first()
            ChartPointDomain(
                day = winner.day,
                dayMillis = winner.dayMillis,
                value = metricValue(winner, metric, exerciseType),
                sessionUuid = winner.sessionUuid,
                weight = winner.weight,
                reps = winner.reps,
                setCount = dailySets.size,
            )
        }
        .sortedBy(ChartPointDomain::day)

    val (effectiveStart, effectiveEnd) = computeWindow(preset, pointsByDay, windowStart, today, zoneId)

    return ChartFoldDomain(
        points = pointsByDay,
        footer = pointsByDay.toFooter(),
        windowStartDay = effectiveStart,
        windowEndDay = effectiveEnd,
    )
}

/**
 * Choose the time range the canvas should render. Three cases:
 *
 * - `ALL` preset + sparse history (≤ 2 points): pad relative to the data span so the
 *   points sit near the centre instead of glued to the right edge or buried in empty space.
 * - Bounded preset (`1M` / `3M` / `1Y`): always `[preset.start, today]` — even when the
 *   user has fewer points than the window can hold, the window is what they asked for.
 * - `ALL` preset + 3+ points: `[firstPoint, today]` — natural span looks fine, no
 *   tightening.
 */
private fun computeWindow(
    preset: ChartPresetDomain,
    pointsByDay: List<ChartPointDomain>,
    windowStartMillis: Long?,
    today: LocalDate,
    zoneId: ZoneId,
): Pair<LocalDate, LocalDate> = when {
    preset == ChartPresetDomain.ALL && pointsByDay.size <= 2 && pointsByDay.isNotEmpty() -> {
        val firstDay = pointsByDay.first().day
        val lastDay = pointsByDay.last().day
        val spanDays = ChronoUnit.DAYS.between(firstDay, lastDay)
        val paddingDays = (spanDays / 2L).coerceAtLeast(MIN_PADDING_DAYS)
        firstDay.minusDays(paddingDays) to lastDay.plusDays(paddingDays)
    }
    else -> {
        val start = windowStartMillis
            ?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            ?: pointsByDay.first().day
        start to today
    }
}

private fun metricValue(
    set: FlatSet,
    metric: ChartMetricDomain,
    type: ExerciseTypeDomain,
): Double = when {
    type == ExerciseTypeDomain.WEIGHTLESS -> set.reps.toDouble()
    metric == ChartMetricDomain.HEAVIEST_WEIGHT -> set.weight ?: 0.0
    else -> (set.weight ?: 0.0) * set.reps
}

private fun List<ChartPointDomain>.toFooter(): ChartFooterStatsDomain? {
    if (isEmpty()) return null
    return ChartFooterStatsDomain(
        min = minBy(ChartPointDomain::value),
        max = maxBy(ChartPointDomain::value),
        last = last(),
    )
}

private data class FlatSet(
    val day: LocalDate,
    val dayMillis: Long,
    val sessionUuid: String,
    val finishedAt: Long,
    val weight: Double?,
    val reps: Int,
)
