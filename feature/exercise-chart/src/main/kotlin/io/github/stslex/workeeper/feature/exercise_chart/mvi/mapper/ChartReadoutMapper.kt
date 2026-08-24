// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.R
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartReadoutUiModel
import kotlin.math.roundToLong

/** The `.readout` block's mapper (extraction §4.5), split from [ExerciseChartUiMapper]. */
internal object ChartReadoutMapper {

    /** The record among the visible points; weight ties use reps, then the earliest session. */
    fun recordIndex(
        points: List<ChartPointUiModel>,
        metric: ChartMetricUiModel,
    ): Int? {
        val byMetric = compareBy<ChartPointUiModel>(ChartPointUiModel::value)
        val comparator = when (metric) {
            ChartMetricUiModel.HEAVIEST_WEIGHT -> byMetric.thenBy(ChartPointUiModel::reps)
            ChartMetricUiModel.VOLUME_PER_SESSION,
            ChartMetricUiModel.VOLUME_PER_SET,
            -> byMetric
        }
        val record = points.maxWithOrNull(comparator) ?: return null
        return points.indexOf(record)
    }

    /** The mockup's `readout()` (§4.5): metric name, caption, and the grouped value + unit. */
    fun toReadout(
        points: List<ChartPointUiModel>,
        activeIndex: Int?,
        metric: ChartMetricUiModel,
        type: ExerciseTypeUiModel,
        resourceWrapper: ResourceWrapper,
    ): ChartReadoutUiModel? {
        val point = activeIndex?.let(points::getOrNull) ?: return null
        val isRecord = activeIndex == recordIndex(points, metric)
        val caption = buildList {
            add("${resourceWrapper.formatDayMonth(point.dayMillis)} ${point.day.year}")
            add(
                resourceWrapper.getQuantityString(
                    R.plurals.feature_exercise_chart_readout_sets,
                    point.setCount,
                    point.setCount,
                ),
            )
            if (isRecord) {
                add(resourceWrapper.getString(R.string.feature_exercise_chart_readout_record))
            }
        }.joinToString(separator = " · ")
        return ChartReadoutUiModel(
            metricName = resourceWrapper.getString(metric.nameRes),
            isRecord = isRecord,
            caption = caption,
            value = formatGrouped(point.value),
            unit = resourceWrapper.getString(
                when (type) {
                    ExerciseTypeUiModel.WEIGHTED -> R.string.feature_exercise_chart_unit_kg
                    ExerciseTypeUiModel.WEIGHTLESS -> R.string.feature_exercise_chart_unit_reps
                },
            ),
        )
    }

    /** Round and group thousands — `4620.0` → `4 620` with the pinned [GROUP_SEPARATOR]. */
    internal fun formatGrouped(value: Double): String {
        val digits = value.roundToLong().toString()
        return digits
            .reversed()
            .chunked(GROUP_SIZE)
            .joinToString(separator = GROUP_SEPARATOR)
            .reversed()
    }

    private const val GROUP_SIZE = 3

    /** NBSP — the Archivo cut has this glyph but not NNBSP (U+202F); never a locale API. */
    private const val GROUP_SEPARATOR = "\u00A0"
}
