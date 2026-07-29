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
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal object ExerciseChartUiMapper {

    fun ChartPointDomain.toUi(): ChartPointUiModel = ChartPointUiModel(
        day = day,
        dayMillis = dayMillis,
        value = value,
        sessionUuid = sessionUuid,
        weight = weight,
        reps = reps,
        setCount = setCount,
    )

    fun ChartFoldDomain.toUiPoints(): ImmutableList<ChartPointUiModel> =
        points.map { it.toUi() }.toImmutableList()

    /**
     * The three `.statrow`s (§4.7): values are the mockup's `fmt()` — the same rounded,
     * grouped form the readout uses — with one shared unit span. The metric does not touch
     * the format: all three metrics read in кг for a weighted exercise, and a weightless
     * exercise's values are rep plurals with no separable unit.
     */
    fun ChartFooterStatsDomain.toUi(
        type: ExerciseTypeDomain,
        resourceWrapper: ResourceWrapper,
    ): ChartFooterStatsUiModel {
        val format: (ChartPointDomain) -> String = when (type) {
            ExerciseTypeDomain.WEIGHTED -> { point -> ChartReadoutMapper.formatGrouped(point.value) }
            ExerciseTypeDomain.WEIGHTLESS -> { point ->
                resourceWrapper.getQuantityString(
                    R.plurals.feature_exercise_chart_value_reps,
                    point.value.toInt(),
                    point.value.toInt(),
                )
            }
        }
        return ChartFooterStatsUiModel(
            minTitle = resourceWrapper.getString(R.string.feature_exercise_chart_footer_min),
            minValue = format(min),
            maxTitle = resourceWrapper.getString(R.string.feature_exercise_chart_footer_max),
            maxValue = format(max),
            lastTitle = resourceWrapper.getString(R.string.feature_exercise_chart_footer_last),
            lastValue = format(last),
            unit = when (type) {
                ExerciseTypeDomain.WEIGHTED ->
                    resourceWrapper.getString(R.string.feature_exercise_chart_unit_kg)

                ExerciseTypeDomain.WEIGHTLESS -> null
            },
        )
    }

    fun ExerciseTypeDomain.toUi(): ExerciseTypeUiModel = when (this) {
        ExerciseTypeDomain.WEIGHTED -> ExerciseTypeUiModel.WEIGHTED
        ExerciseTypeDomain.WEIGHTLESS -> ExerciseTypeUiModel.WEIGHTLESS
    }

    fun ExerciseTypeUiModel.toDomain(): ExerciseTypeDomain = when (this) {
        ExerciseTypeUiModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
        ExerciseTypeUiModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
    }

    fun ChartMetricDomain.toUi(): ChartMetricUiModel = when (this) {
        ChartMetricDomain.HEAVIEST_WEIGHT -> ChartMetricUiModel.HEAVIEST_WEIGHT
        ChartMetricDomain.VOLUME_PER_SESSION -> ChartMetricUiModel.VOLUME_PER_SESSION
        ChartMetricDomain.VOLUME_PER_SET -> ChartMetricUiModel.VOLUME_PER_SET
    }

    fun ChartMetricUiModel.toDomain(): ChartMetricDomain = when (this) {
        ChartMetricUiModel.HEAVIEST_WEIGHT -> ChartMetricDomain.HEAVIEST_WEIGHT
        ChartMetricUiModel.VOLUME_PER_SESSION -> ChartMetricDomain.VOLUME_PER_SESSION
        ChartMetricUiModel.VOLUME_PER_SET -> ChartMetricDomain.VOLUME_PER_SET
    }

    fun ChartPresetDomain.toUi(): ChartPresetUiModel = when (this) {
        ChartPresetDomain.MONTH_1 -> ChartPresetUiModel.MONTH_1
        ChartPresetDomain.MONTHS_3 -> ChartPresetUiModel.MONTHS_3
        ChartPresetDomain.YEAR_1 -> ChartPresetUiModel.YEAR_1
        ChartPresetDomain.ALL -> ChartPresetUiModel.ALL
    }

    fun ChartPresetUiModel.toDomain(): ChartPresetDomain = when (this) {
        ChartPresetUiModel.MONTH_1 -> ChartPresetDomain.MONTH_1
        ChartPresetUiModel.MONTHS_3 -> ChartPresetDomain.MONTHS_3
        ChartPresetUiModel.YEAR_1 -> ChartPresetDomain.YEAR_1
        ChartPresetUiModel.ALL -> ChartPresetDomain.ALL
    }

    fun RecentExerciseDomain.toUi(): ExercisePickerItemUiModel = ExercisePickerItemUiModel(
        uuid = uuid,
        name = name,
        type = type.toUi(),
    )
}
