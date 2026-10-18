// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.R
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartReadoutCopyUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartReadoutUiModel
import kotlinx.collections.immutable.toImmutableMap
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

    /**
     * D3: every catalog read [toReadout] needs, resolved once per load, so the scrub never
     * suspends: all three metric names, both units, the record word, and the set plural for each
     * distinct set count among [points].
     */
    fun toReadoutCopy(
        points: List<ChartPointUiModel>,
        resourceWrapper: ResourceWrapper,
    ): ChartReadoutCopyUiModel = ChartReadoutCopyUiModel(
        heaviestWeightName = resourceWrapper.getString(R.string.feature_exercise_chart_metric_name_heaviest),
        volumePerSessionName = resourceWrapper.getString(R.string.feature_exercise_chart_metric_name_session),
        volumePerSetName = resourceWrapper.getString(R.string.feature_exercise_chart_metric_name_volume),
        kgUnit = resourceWrapper.getString(R.string.feature_exercise_chart_unit_kg),
        repsUnit = resourceWrapper.getString(R.string.feature_exercise_chart_unit_reps),
        recordLabel = resourceWrapper.getString(R.string.feature_exercise_chart_readout_record),
        setCountLabels = points.map(ChartPointUiModel::setCount).distinct().associateWith { count ->
            resourceWrapper.getQuantityString(R.plurals.feature_exercise_chart_readout_sets, count, count)
        }.toImmutableMap(),
    )

    /** The mockup's `readout()` (§4.5): metric name, caption, and the grouped value + unit. */
    fun toReadout(
        points: List<ChartPointUiModel>,
        activeIndex: Int?,
        metric: ChartMetricUiModel,
        type: ExerciseTypeUiModel,
        copy: ChartReadoutCopyUiModel,
        resourceWrapper: ResourceWrapper,
    ): ChartReadoutUiModel? {
        val point = activeIndex?.let(points::getOrNull) ?: return null
        val isRecord = activeIndex == recordIndex(points, metric)
        val caption = buildList {
            add("${resourceWrapper.formatDayMonth(point.dayMillis)} ${point.day.year}")
            add(copy.setCountLabels.getValue(point.setCount))
            if (isRecord) {
                add(copy.recordLabel)
            }
        }.joinToString(separator = " · ")
        return ChartReadoutUiModel(
            metricName = when (metric) {
                ChartMetricUiModel.HEAVIEST_WEIGHT -> copy.heaviestWeightName
                ChartMetricUiModel.VOLUME_PER_SESSION -> copy.volumePerSessionName
                ChartMetricUiModel.VOLUME_PER_SET -> copy.volumePerSetName
            },
            isRecord = isRecord,
            caption = caption,
            value = formatGrouped(point.value),
            unit = when (type) {
                ExerciseTypeUiModel.WEIGHTED -> copy.kgUnit
                ExerciseTypeUiModel.WEIGHTLESS -> copy.repsUnit
            },
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
