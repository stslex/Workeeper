// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

/**
 * Date filter presets for the chart screen — the selectable chips. PresetChipsRow maps each
 * entry to its display label (S3); the window length + boundary computation lives in the domain
 * [ChartPresetDomain][io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPresetDomain].
 */
enum class ChartPresetUiModel {
    MONTH_1,
    MONTHS_3,
    YEAR_1,
    ALL,
}
