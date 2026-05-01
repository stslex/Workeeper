// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.R
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.SPARSE_PADDING_DAYS
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.bucketAndFold
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.toTooltip
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

    /**
     * On the [ChartPresetUiModel.ALL] preset, when the exercise has only 1-2 finished
     * sessions in its full history, the natural window (`first finished_at … today`) can
     * stretch a year-old single point across the whole canvas — points cluster against the
     * right edge and look like an outlier rather than the data they are. Tighten the window
     * to ±[SPARSE_PADDING_DAYS] around the actual data so sparse history reads as centred
     * data, not as noise.
     */
    private const val SPARSE_PADDING_DAYS = 14L

    data class FoldResult(
        val points: ImmutableList<ChartPointUiModel>,
        val footer: ChartFooterStatsUiModel?,
        val windowStartDay: LocalDate?,
        val windowEndDay: LocalDate?,
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

        if (flat.isEmpty()) return FoldResult(
            points = persistentListOf(),
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

        val (effectiveStart, effectiveEnd) = computeWindow(preset, pointsByDay, windowStart, today, zoneId)

        return FoldResult(
            points = pointsByDay.toImmutableList(),
            footer = pointsByDay.toFooter(metric, exerciseType, resourceWrapper),
            windowStartDay = effectiveStart,
            windowEndDay = effectiveEnd,
        )
    }

    /**
     * Choose the time range the canvas should render. Three cases:
     *
     * - `ALL` preset + sparse history (≤ 2 points): pad ±[SPARSE_PADDING_DAYS] around the
     *   data so the points sit near the centre instead of glued to the right edge.
     * - Bounded preset (`1M` / `3M` / `1Y`): always `[preset.start, today]` — even when the
     *   user has fewer points than the window can hold, the window is what they asked for.
     * - `ALL` preset + 3+ points: `[firstPoint, today]` — natural span looks fine, no
     *   tightening.
     */
    private fun computeWindow(
        preset: ChartPresetUiModel,
        pointsByDay: List<ChartPointUiModel>,
        windowStartMillis: Long?,
        today: LocalDate,
        zoneId: ZoneId,
    ): Pair<LocalDate, LocalDate> = when {
        preset == ChartPresetUiModel.ALL && pointsByDay.size <= 2 -> {
            val firstDay = pointsByDay.first().day
            val lastDay = pointsByDay.last().day
            firstDay.minusDays(SPARSE_PADDING_DAYS) to lastDay.plusDays(SPARSE_PADDING_DAYS)
        }
        else -> {
            val start = windowStartMillis
                ?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
                ?: pointsByDay.first().day
            start to today
        }
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
