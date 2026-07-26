// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.model

import io.github.stslex.workeeper.feature.exercise_chart.R

/**
 * Date filter presets for the chart screen — the selectable chips. [labelRes] is the
 * display label; the window length + boundary computation lives in the domain
 * [ChartPresetDomain][io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPresetDomain].
 */
enum class ChartPresetUiModel(
    val labelRes: Int,
) {
    MONTH_1(R.string.feature_exercise_chart_preset_1m),
    MONTHS_3(R.string.feature_exercise_chart_preset_3m),
    YEAR_1(R.string.feature_exercise_chart_preset_1y),
    ALL(R.string.feature_exercise_chart_preset_all),
}
