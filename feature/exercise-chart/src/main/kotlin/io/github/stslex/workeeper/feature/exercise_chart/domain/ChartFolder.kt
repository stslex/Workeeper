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

/**
 * Pure folding logic for the chart screen — converts raw history into the points + footer
 * the canvas needs to render. Lives in the domain layer; the UI mapper consumes the
 * resulting [ChartFoldDomain] and produces UI types with locale-aware formatting.
 *
 * The preset acts as a **filter** only: the §4.6 canvas is index-spaced, so there is no
 * render window to compute — the visible span IS the point list. (The date-proportional
 * canvas this fold used to serve carried a window-tightening pass for sparse ALL-preset
 * history; it died with date-spacing.)
 */
internal fun bucketAndFold(
    history: List<HistoryEntryDomain>,
    preset: ChartPresetDomain,
    metric: ChartMetricDomain,
    exerciseType: ExerciseTypeDomain,
    now: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): ChartFoldDomain {
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

    if (eligible.isEmpty()) return ChartFoldDomain(points = emptyList(), footer = null)

    // Eligibility decides which set represents a session; it does not decide how many sets
    // the user logged in that session. Both grounds `isEligible` drops on were still logged,
    // so the readout count comes from the unfiltered window.
    val setsPerSession = flat.groupingBy(FlatSet::sessionUuid).eachCount()

    // Which set — or, under VOLUME_PER_SESSION, which total — represents each session. The
    // metric comes first — that is what the axis plots. Two ordering chains exist, and no
    // third: under HEAVIEST_WEIGHT a tie on the metric *is* a tie on weight, so reps DESC ranks
    // equal-weight sets exactly as the PR rule does. Both volume metrics use the other chain —
    // metric DESC only: a volume tie means the candidates traded weight against reps
    // (100×2 == 50×4), where reps DESC would quietly read as
    // "prefer the lighter set" — volume is not a PR metric, so nothing licenses that key.
    // Position ASC is the last criterion either way and comes for free — `sortedWith` is
    // stable and each session's sets arrive in position order from the history query.
    val byMetric = compareByDescending<FlatSet> { f -> metricValue(f, metric, exerciseType) }
    val pointsBySession = when (metric) {
        ChartMetricDomain.HEAVIEST_WEIGHT -> eligible.foldSessionWinners(
            comparator = byMetric.thenByDescending(FlatSet::reps),
            metric = metric,
            exerciseType = exerciseType,
            setsPerSession = setsPerSession,
        )

        ChartMetricDomain.VOLUME_PER_SET -> eligible.foldSessionWinners(
            comparator = byMetric,
            metric = metric,
            exerciseType = exerciseType,
            setsPerSession = setsPerSession,
        )

        ChartMetricDomain.VOLUME_PER_SESSION -> eligible.foldSessionTotals(
            metric = metric,
            exerciseType = exerciseType,
            setsPerSession = setsPerSession,
        )
    }

    return ChartFoldDomain(
        points = pointsBySession,
        footer = pointsBySession.toFooter(),
    )
}

/** The per-set fold: one winning set represents each completed session. */
private fun List<FlatSet>.foldSessionWinners(
    comparator: Comparator<FlatSet>,
    metric: ChartMetricDomain,
    exerciseType: ExerciseTypeDomain,
    setsPerSession: Map<String, Int>,
): List<ChartPointDomain> = groupBy(FlatSet::sessionUuid)
    .values
    .sortedBy { sessionSets -> sessionSets.first().finishedAt }
    .map { sessionSets ->
        val winner = sessionSets.sortedWith(comparator).first()
        ChartPointDomain(
            day = winner.day,
            dayMillis = winner.dayMillis,
            value = metricValue(winner, metric, exerciseType),
            sessionUuid = winner.sessionUuid,
            weight = winner.weight,
            reps = winner.reps,
            setCount = setsPerSession.getValue(winner.sessionUuid),
        )
    }

/**
 * The per-session fold (§11.2). A session's total is the sum of its sets' contributions —
 * [metricValue] under [ChartMetricDomain.VOLUME_PER_SESSION] is the per-set volume, so the
 * session metric is definitionally "the sum of Подход over the session" and introduces no
 * new per-set value. Every completed session remains a point, including multiple sessions
 * on the same calendar day.
 *
 * The resulting point is an aggregate: no single set is "the" point, so `weight`/`reps`
 * carry no meaning and are null/0 — see the [ChartPointDomain] contract.
 */
private fun List<FlatSet>.foldSessionTotals(
    metric: ChartMetricDomain,
    exerciseType: ExerciseTypeDomain,
    setsPerSession: Map<String, Int>,
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
    .sortedBy(SessionTotal::finishedAt)
    .map { session ->
        ChartPointDomain(
            day = session.day,
            dayMillis = session.dayMillis,
            value = session.total,
            sessionUuid = session.sessionUuid,
            weight = null,
            reps = 0,
            setCount = setsPerSession.getValue(session.sessionUuid),
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
