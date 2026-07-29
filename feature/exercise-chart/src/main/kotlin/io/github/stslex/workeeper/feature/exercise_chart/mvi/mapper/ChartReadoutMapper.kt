// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.R
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartReadoutUiModel
import kotlin.math.roundToLong

/**
 * The `.readout` block's mapper (extraction §4.5) — split from [ExerciseChartUiMapper]
 * along the mockup's own seam: everything here feeds the persistent inspection block and
 * the record marking, nothing here is an enum bridge or a tooltip/footer formatter.
 */
internal object ChartReadoutMapper {

    /**
     * The record among the visible points — the index the mockup marks `.pt.pr` and suffixes
     * `· рекорд` in the readout. It is the argmax by plotted value; on a tie the first (=
     * earliest, points are day-sorted) wins, consistent with the fold's own `finishedAt ASC`
     * tiebreak and with what the footer already reports as `max`.
     */
    fun recordIndex(points: List<ChartPointUiModel>): Int? {
        val record = points.maxByOrNull(ChartPointUiModel::value) ?: return null
        return points.indexOf(record)
    }

    /**
     * The mockup's `readout()` (extraction §4.5): metric long name, the active point's date +
     * set count (+ `рекорд` on the record point), and the value with its unit. The value is
     * `Math.round(n).toLocaleString('ru-RU')` in the mockup — rounded to a whole number and
     * thousand-grouped; see [formatGrouped] for the deliberate plain-space deviation.
     */
    fun toReadout(
        points: List<ChartPointUiModel>,
        activeIndex: Int?,
        metric: ChartMetricUiModel,
        type: ExerciseTypeUiModel,
        resourceWrapper: ResourceWrapper,
    ): ChartReadoutUiModel? {
        val point = activeIndex?.let(points::getOrNull) ?: return null
        val isRecord = activeIndex == recordIndex(points)
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

    /**
     * Round to a whole number and group thousands — `4620.0` → `4 620`. The group separator
     * is a PLAIN space where ru-RU's formatter would emit NBSP: the value renders in Archivo,
     * a cut whose charset is pinned to digits and `:.,-+/%` (plus the ordinary space), and a
     * missing NBSP glyph would render as tofu or a fallback-font seam mid-number. Reported
     * as a deviation with the PR. Shared with the footer — the mockup's `fmt()` feeds the
     * readout and all three statrows alike.
     */
    internal fun formatGrouped(value: Double): String {
        val digits = value.roundToLong().toString()
        return digits
            .reversed()
            .chunked(GROUP_SIZE)
            .joinToString(separator = " ")
            .reversed()
    }

    private const val GROUP_SIZE = 3
}
