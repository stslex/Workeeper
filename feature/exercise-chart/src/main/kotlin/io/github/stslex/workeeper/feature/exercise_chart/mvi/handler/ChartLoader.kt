// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel

/** D2: what [ClickHandler] needs from [CommonHandler] — a reload — bound in `ExerciseChartGraph`. */
interface ChartLoader {

    /** Re-fetch and bucket the chart for [exercise]; [ClickHandler] calls it on every change. */
    fun loadChart(exercise: ExercisePickerItemUiModel)
}
