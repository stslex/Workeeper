// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.R
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartTooltipUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure mapping logic for the chart screen. Two entry points:
 *
 * - [bucketAndFold] consumes raw history and produces the points + footer for the canvas.
 * - [toTooltip] formats the tap-target tooltip body. Both stay free of `Context`, time, and
 *   coroutines so the unit tests can exercise them directly.
 */
internal object ExerciseChartUiMapper {

    data class FoldResult(
        val points: ImmutableList<ChartPointUiModel>,
        val footer: ChartFooterStatsUiModel?,
    )

    fun bucketAndFold(
        history: List<HistoryEntry>,
        preset: ChartPresetUiModel,
        metric: ChartMetricUiModel,
        exerciseType: ExerciseTypeUiModel,
        resourceWrapper: ResourceWrapper,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): FoldResult {
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

        if (flat.isEmpty()) return FoldResult(persistentListOf(), null)

        // The earliest finishedAt wins on tie, matching the v2.1 PR semantics.
        val foldComparator = compareByDescending<FlatSet> { f ->
            metricValue(f, metric, exerciseType)
        }.thenBy(FlatSet::finishedAt)

        val pointsByDay = flat
            .groupBy(FlatSet::day)
            .map { (_, dailySets) ->
                val winner = dailySets.sortedWith(foldComparator).first()
                ChartPointUiModel(
                    day = winner.day,
                    dayMillis = winner.dayMillis,
                    value = metricValue(winner, metric, exerciseType),
                    sessionUuid = winner.sessionUuid,
                    weight = winner.weight,
                    reps = winner.reps,
                    setCount = dailySets.size,
                )
            }
            .sortedBy(ChartPointUiModel::day)

        return FoldResult(
            points = pointsByDay.toImmutableList(),
            footer = pointsByDay.toFooter(metric, exerciseType, resourceWrapper),
        )
    }

    fun toTooltip(
        point: ChartPointUiModel,
        exercise: ExercisePickerItemUiModel?,
        metric: ChartMetricUiModel,
        resourceWrapper: ResourceWrapper,
    ): ChartTooltipUiModel {
        val type = exercise?.type ?: ExerciseTypeUiModel.WEIGHTED
        return ChartTooltipUiModel(
            sessionUuid = point.sessionUuid,
            exerciseName = exercise?.name.orEmpty(),
            dateLabel = resourceWrapper.formatMediumDate(point.dayMillis),
            displayLabel = formatDisplay(point, type, metric, resourceWrapper),
            setCountLabel = if (point.setCount > 1) {
                resourceWrapper.getQuantityString(
                    R.plurals.feature_exercise_chart_tooltip_set_count,
                    point.setCount,
                    point.setCount,
                )
            } else {
                null
            },
        )
    }

    private fun metricValue(
        set: FlatSet,
        metric: ChartMetricUiModel,
        type: ExerciseTypeUiModel,
    ): Double = when {
        type == ExerciseTypeUiModel.WEIGHTLESS -> set.reps.toDouble()
        metric == ChartMetricUiModel.HEAVIEST_WEIGHT -> set.weight ?: 0.0
        else -> (set.weight ?: 0.0) * set.reps
    }

    private fun List<ChartPointUiModel>.toFooter(
        metric: ChartMetricUiModel,
        type: ExerciseTypeUiModel,
        resourceWrapper: ResourceWrapper,
    ): ChartFooterStatsUiModel? {
        if (isEmpty()) return null
        val minPoint = minBy(ChartPointUiModel::value)
        val maxPoint = maxBy(ChartPointUiModel::value)
        val lastPoint = last()
        return ChartFooterStatsUiModel(
            minLabel = resourceWrapper.getString(
                R.string.feature_exercise_chart_footer_min,
                formatMetricValue(minPoint, type, metric, resourceWrapper),
            ),
            maxLabel = resourceWrapper.getString(
                R.string.feature_exercise_chart_footer_max,
                formatMetricValue(maxPoint, type, metric, resourceWrapper),
            ),
            lastLabel = resourceWrapper.getString(
                R.string.feature_exercise_chart_footer_last,
                formatMetricValue(lastPoint, type, metric, resourceWrapper),
            ),
        )
    }

    private fun formatDisplay(
        point: ChartPointUiModel,
        type: ExerciseTypeUiModel,
        metric: ChartMetricUiModel,
        resourceWrapper: ResourceWrapper,
    ): String = when {
        type == ExerciseTypeUiModel.WEIGHTLESS -> resourceWrapper.getQuantityString(
            R.plurals.feature_exercise_chart_value_reps,
            point.reps,
            point.reps,
        )
        metric == ChartMetricUiModel.VOLUME_PER_SET -> resourceWrapper.getString(
            R.string.feature_exercise_chart_value_volume,
            formatNumber(point.value),
        )
        else -> resourceWrapper.getString(
            R.string.feature_exercise_chart_value_weight_x_reps,
            formatNumber(point.weight ?: 0.0),
            point.reps,
        )
    }

    private fun formatMetricValue(
        point: ChartPointUiModel,
        type: ExerciseTypeUiModel,
        metric: ChartMetricUiModel,
        resourceWrapper: ResourceWrapper,
    ): String = when {
        type == ExerciseTypeUiModel.WEIGHTLESS -> resourceWrapper.getQuantityString(
            R.plurals.feature_exercise_chart_value_reps,
            point.reps,
            point.reps,
        )
        metric == ChartMetricUiModel.VOLUME_PER_SET -> resourceWrapper.getString(
            R.string.feature_exercise_chart_value_volume,
            formatNumber(point.value),
        )
        else -> resourceWrapper.getString(
            R.string.feature_exercise_chart_value_weight,
            formatNumber(point.weight ?: 0.0),
        )
    }

    private fun formatNumber(value: Double): String {
        val rounded = value
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else "%.1f".format(rounded)
    }

    private data class FlatSet(
        val day: LocalDate,
        val dayMillis: Long,
        val sessionUuid: String,
        val finishedAt: Long,
        val weight: Double?,
        val reps: Int,
    )
}
