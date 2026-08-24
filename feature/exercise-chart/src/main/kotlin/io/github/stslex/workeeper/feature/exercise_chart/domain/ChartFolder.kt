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
 * Pure folding logic for the chart screen: raw history into the points + footer the canvas
 * renders. The preset acts as a filter only — the §4.6 canvas is index-spaced.
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
    // data point, and a weight-null set on a WEIGHTED exercise is excluded, never coerced.
    val eligible = flat.filter { it.isEligible(exerciseType) }

    if (eligible.isEmpty()) return ChartFoldDomain(points = emptyList(), footer = null)

    // Eligibility decides which set represents a session, not how many sets were logged, so
    // the readout count comes from the unfiltered window.
    val setsPerSession = flat.groupingBy(FlatSet::sessionUuid).eachCount()

    // Which set — or, under VOLUME_PER_SESSION, which total — represents each session. Metric
    // DESC first; reps DESC only breaks HEAVIEST_WEIGHT ties. See v2.2-exercise-charts.md.
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
 * The per-session fold (§11.2): a session's total is the sum of its sets' per-set volumes.
 * The point is an aggregate, so `weight`/`reps` carry no meaning — see [ChartPointDomain].
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

/** Plottable under the same rule that makes a set PR-eligible, minus the session-state clauses. */
private fun FlatSet.isEligible(type: ExerciseTypeDomain): Boolean =
    reps > 0 && (type == ExerciseTypeDomain.WEIGHTLESS || weight != null)

/**
 * Branches on the metric first, then on what it can mean for the exercise type; WEIGHTLESS
 * collapses both metrics to reps, and `weight` is non-null here because [isEligible] ran.
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
