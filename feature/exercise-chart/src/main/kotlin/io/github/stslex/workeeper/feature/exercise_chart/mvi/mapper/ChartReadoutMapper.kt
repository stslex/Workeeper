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
     * thousand-grouped; see [formatGrouped] for the pinned NBSP separator.
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
     * Round to a whole number and group thousands — `4620.0` → `4 620` with an NBSP
     * (U+00A0), the separator the mockup's `fmt()` (`toLocaleString('ru-RU')`) emits.
     * The literal is pinned here rather than taken from a locale API so the output cannot
     * drift to NNBSP (U+202F) on an ICU update: the Archivo cut has real glyphs for SPACE
     * and NBSP (cmap gid 619/620) but none for NNBSP, and only the missing one could seam.
     * (An earlier revision shipped a plain space on the claim that the cut's charset held
     * digits and `:.,-+/%` only — the cut is a full latin instance and the claim was false;
     * see licenses/README.md "Character coverage".) Shared with the footer — `fmt()` feeds
     * the readout and all three statrows alike.
     */
    internal fun formatGrouped(value: Double): String {
        val digits = value.roundToLong().toString()
        return digits
            .reversed()
            .chunked(GROUP_SIZE)
            .joinToString(separator = GROUP_SEPARATOR)
            .reversed()
    }

    private const val GROUP_SIZE = 3

    /** NBSP — present in the Archivo cut, unbreakable mid-number, what ru-RU draws. */
    private const val GROUP_SEPARATOR = "\u00A0"
}
