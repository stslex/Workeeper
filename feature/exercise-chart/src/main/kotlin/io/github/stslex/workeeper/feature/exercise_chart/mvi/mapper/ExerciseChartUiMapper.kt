// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.R
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFoldDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFooterStatsDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartMetricDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPointDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPresetDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.RecentExerciseDomain
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartTooltipUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Pure UI-side mapping for the chart screen.
 *
 * - [ChartFoldDomain.toUiPoints] / [ChartFooterStatsDomain.toUi] convert domain types to
 *   UI types with locale-aware formatting (the fold itself runs in domain).
 * - [toTooltip] formats the tap-target tooltip body. Stays free of `Context`, time, and
 *   coroutines so the unit tests can exercise it directly.
 */
internal fun ChartPointDomain.toUi(): ChartPointUiModel = ChartPointUiModel(
    day = day,
    dayMillis = dayMillis,
    value = value,
    sessionUuid = sessionUuid,
    weight = weight,
    reps = reps,
    setCount = setCount,
)

internal fun ChartFoldDomain.toUiPoints(): ImmutableList<ChartPointUiModel> =
    points.map { it.toUi() }.toImmutableList()

internal fun ChartFooterStatsDomain.toUi(
    metric: ChartMetricDomain,
    type: ExerciseTypeDomain,
    resourceWrapper: ResourceWrapper,
): ChartFooterStatsUiModel = ChartFooterStatsUiModel(
    minLabel = resourceWrapper.getString(
        R.string.feature_exercise_chart_footer_min,
        formatMetricValue(min, type, metric, resourceWrapper),
    ),
    maxLabel = resourceWrapper.getString(
        R.string.feature_exercise_chart_footer_max,
        formatMetricValue(max, type, metric, resourceWrapper),
    ),
    lastLabel = resourceWrapper.getString(
        R.string.feature_exercise_chart_footer_last,
        formatMetricValue(last, type, metric, resourceWrapper),
    ),
)

internal fun toTooltip(
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

internal fun ExerciseTypeDomain.toUi(): ExerciseTypeUiModel = when (this) {
    ExerciseTypeDomain.WEIGHTED -> ExerciseTypeUiModel.WEIGHTED
    ExerciseTypeDomain.WEIGHTLESS -> ExerciseTypeUiModel.WEIGHTLESS
}

internal fun ExerciseTypeUiModel.toDomain(): ExerciseTypeDomain = when (this) {
    ExerciseTypeUiModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
    ExerciseTypeUiModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
}

internal fun ChartMetricDomain.toUi(): ChartMetricUiModel = when (this) {
    ChartMetricDomain.HEAVIEST_WEIGHT -> ChartMetricUiModel.HEAVIEST_WEIGHT
    ChartMetricDomain.VOLUME_PER_SET -> ChartMetricUiModel.VOLUME_PER_SET
}

internal fun ChartMetricUiModel.toDomain(): ChartMetricDomain = when (this) {
    ChartMetricUiModel.HEAVIEST_WEIGHT -> ChartMetricDomain.HEAVIEST_WEIGHT
    ChartMetricUiModel.VOLUME_PER_SET -> ChartMetricDomain.VOLUME_PER_SET
}

internal fun ChartPresetDomain.toUi(): ChartPresetUiModel = when (this) {
    ChartPresetDomain.MONTH_1 -> ChartPresetUiModel.MONTH_1
    ChartPresetDomain.MONTHS_3 -> ChartPresetUiModel.MONTHS_3
    ChartPresetDomain.YEAR_1 -> ChartPresetUiModel.YEAR_1
    ChartPresetDomain.ALL -> ChartPresetUiModel.ALL
}

internal fun ChartPresetUiModel.toDomain(): ChartPresetDomain = when (this) {
    ChartPresetUiModel.MONTH_1 -> ChartPresetDomain.MONTH_1
    ChartPresetUiModel.MONTHS_3 -> ChartPresetDomain.MONTHS_3
    ChartPresetUiModel.YEAR_1 -> ChartPresetDomain.YEAR_1
    ChartPresetUiModel.ALL -> ChartPresetDomain.ALL
}

internal fun RecentExerciseDomain.toUi(): ExercisePickerItemUiModel = ExercisePickerItemUiModel(
    uuid = uuid,
    name = name,
    type = type.toUi(),
)

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
        R.string.feature_exercise_chart_value_weight_x_reps,
        formatNumber(point.value),
        point.reps,
    )
    else -> resourceWrapper.getString(
        R.string.feature_exercise_chart_value_weight_x_reps,
        formatNumber(point.weight ?: 0.0),
        point.reps,
    )
}

private fun formatMetricValue(
    point: ChartPointDomain,
    type: ExerciseTypeDomain,
    metric: ChartMetricDomain,
    resourceWrapper: ResourceWrapper,
): String = when {
    type == ExerciseTypeDomain.WEIGHTLESS -> resourceWrapper.getQuantityString(
        R.plurals.feature_exercise_chart_value_reps,
        point.reps,
        point.reps,
    )
    metric == ChartMetricDomain.VOLUME_PER_SET -> resourceWrapper.getString(
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
