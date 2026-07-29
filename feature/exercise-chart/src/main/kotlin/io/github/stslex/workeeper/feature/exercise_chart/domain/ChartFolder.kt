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
    val windowStart = preset.windowStartMillis(now, zoneId)

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

    // Same eligibility as the PR rule (`SessionDao.PR_ELIGIBILITY`): a zero-rep set is not a
    // data point, and for a WEIGHTED exercise a weight-null set is excluded rather than
    // coerced to 0.0 — coercion invents a point at the bottom of the axis.
    val eligible = flat.filter { it.isEligible(exerciseType) }

    if (eligible.isEmpty()) return ChartFoldDomain(
        points = emptyList(),
        footer = null,
        windowStartDay = null,
        windowEndDay = null,
    )

    // Eligibility decides which set *represents* a day; it does not decide how many sets the
    // user logged that day. Both grounds `isEligible` drops on — a weighted set saved without
    // a weight, and a set left at zero reps — were still logged against that day, so the
    // tooltip's "N sets this day" counts over the unfiltered window. That is the spec's
    // definition, and it agrees with the app's other set counter: `SessionDao`'s
    // `set_count` is a bare `COUNT(*)` over `set_table` with no reps or weight predicate.
    val setsPerDay = flat.groupingBy(FlatSet::day).eachCount()

    // Which set — or, under VOLUME_PER_SESSION, which session — represents the day. The
    // metric comes first — that is what the axis plots. Two ordering chains exist, and no
    // third: under HEAVIEST_WEIGHT the keys below the metric are `SessionDao.PR_ORDER` (a tie
    // on the metric *is* a tie on weight, so reps DESC ranks equal-weight sets exactly as the
    // PR rule does, and the earliest finishedAt settles the rest). Both volume metrics use the
    // other chain — metric DESC, then earliest finishedAt: a volume tie means the candidates
    // traded weight against reps (100×2 == 50×4), where reps DESC would quietly read as
    // "prefer the lighter set" — volume is not a PR metric, so nothing licenses that key.
    // A session-total tie is the same kind of trade, so the session fold joins that chain
    // rather than growing a fourth. Position ASC is the last criterion either way and comes
    // for free — `sortedWith` is stable and each session's sets arrive in position order from
    // `SessionDao.getHistoryByExercise`.
    val byMetric = compareByDescending<FlatSet> { f -> metricValue(f, metric, exerciseType) }
    val pointsByDay = when (metric) {
        ChartMetricDomain.HEAVIEST_WEIGHT -> eligible.foldDayWinners(
            comparator = byMetric.thenByDescending(FlatSet::reps).thenBy(FlatSet::finishedAt),
            metric = metric,
            exerciseType = exerciseType,
            setsPerDay = setsPerDay,
        )

        ChartMetricDomain.VOLUME_PER_SET -> eligible.foldDayWinners(
            comparator = byMetric.thenBy(FlatSet::finishedAt),
            metric = metric,
            exerciseType = exerciseType,
            setsPerDay = setsPerDay,
        )

        ChartMetricDomain.VOLUME_PER_SESSION -> eligible.foldSessionTotals(
            metric = metric,
            exerciseType = exerciseType,
            setsPerDay = setsPerDay,
        )
    }.sortedBy(ChartPointDomain::day)

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

/** The per-set fold: one winning set represents the day. */
private fun List<FlatSet>.foldDayWinners(
    comparator: Comparator<FlatSet>,
    metric: ChartMetricDomain,
    exerciseType: ExerciseTypeDomain,
    setsPerDay: Map<LocalDate, Int>,
): List<ChartPointDomain> = groupBy(FlatSet::day)
    .map { (day, dailySets) ->
        val winner = dailySets.sortedWith(comparator).first()
        ChartPointDomain(
            day = winner.day,
            dayMillis = winner.dayMillis,
            value = metricValue(winner, metric, exerciseType),
            sessionUuid = winner.sessionUuid,
            weight = winner.weight,
            reps = winner.reps,
            setCount = setsPerDay.getValue(day),
        )
    }

/**
 * The per-session fold (§11.2). A session's total is the sum of its sets' contributions —
 * [metricValue] under [ChartMetricDomain.VOLUME_PER_SESSION] is the per-set volume, so the
 * session metric is definitionally "the sum of Подход over the session" and introduces no
 * new per-set value. When two sessions land on one day, the winner is chosen by the volume
 * chain (total DESC, earliest finishedAt) — see the ordering comment in [bucketAndFold].
 *
 * The resulting point is an aggregate: no single set is "the" point, so `weight`/`reps`
 * carry no meaning and are null/0 — see the [ChartPointDomain] contract.
 */
private fun List<FlatSet>.foldSessionTotals(
    metric: ChartMetricDomain,
    exerciseType: ExerciseTypeDomain,
    setsPerDay: Map<LocalDate, Int>,
): List<ChartPointDomain> = groupBy(FlatSet::sessionUuid)
    .map { (sessionUuid, sets) ->
        val first = sets.first()
        SessionTotal(
            day = first.day,
            dayMillis = first.dayMillis,
            sessionUuid = sessionUuid,
            finishedAt = first.finishedAt,
            total = sets.sumOf { set -> metricValue(set, metric, exerciseType) },
        )
    }
    .groupBy(SessionTotal::day)
    .map { (day, sessions) ->
        val winner = sessions
            .sortedWith(compareByDescending(SessionTotal::total).thenBy(SessionTotal::finishedAt))
            .first()
        ChartPointDomain(
            day = winner.day,
            dayMillis = winner.dayMillis,
            value = winner.total,
            sessionUuid = winner.sessionUuid,
            weight = null,
            reps = 0,
            setCount = setsPerDay.getValue(day),
        )
    }

/**
 * A set is plottable under the same rule that makes it PR-eligible, minus the session-state
 * clauses the history query has already applied.
 */
private fun FlatSet.isEligible(type: ExerciseTypeDomain): Boolean =
    reps > 0 && (type == ExerciseTypeDomain.WEIGHTLESS || weight != null)

/**
 * Branches on the metric first, then on what that metric can mean for the exercise type — the
 * other way round reads as if the type silences the metric.
 *
 * For a WEIGHTLESS exercise both metrics genuinely collapse to reps: there is no weight to be
 * heaviest, and per-set volume with an unknown constant bodyweight is reps up to that
 * constant. Nothing is lost by the collapse, and the metric toggle is not offered for those
 * exercises anyway (`ExerciseChartStore.State.showMetricToggle`).
 *
 * `weight` is non-null here: [isEligible] dropped weight-null rows for WEIGHTED exercises
 * before this is ever called.
 *
 * Under [ChartMetricDomain.VOLUME_PER_SESSION] this is the set's *contribution* to the
 * session total, which is exactly the per-set volume — [foldSessionTotals] sums it. One
 * value definition per metric; the session metric adds a fold, not a new per-set value.
 */
private fun metricValue(
    set: FlatSet,
    metric: ChartMetricDomain,
    type: ExerciseTypeDomain,
): Double = when (metric) {
    ChartMetricDomain.HEAVIEST_WEIGHT -> when (type) {
        ExerciseTypeDomain.WEIGHTLESS -> set.reps.toDouble()
        ExerciseTypeDomain.WEIGHTED -> set.weight ?: 0.0
    }
    ChartMetricDomain.VOLUME_PER_SET, ChartMetricDomain.VOLUME_PER_SESSION -> when (type) {
        ExerciseTypeDomain.WEIGHTLESS -> set.reps.toDouble()
        ExerciseTypeDomain.WEIGHTED -> (set.weight ?: 0.0) * set.reps
    }
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

/** One session's summed metric on the day it finished; input to the per-session day pick. */
private data class SessionTotal(
    val day: LocalDate,
    val dayMillis: Long,
    val sessionUuid: String,
    val finishedAt: Long,
    val total: Double,
)
