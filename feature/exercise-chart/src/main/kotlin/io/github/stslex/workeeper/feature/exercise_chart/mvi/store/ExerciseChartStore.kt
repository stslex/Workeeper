// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartTooltipUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal interface ExerciseChartStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val isLoading: Boolean,
        val initialUuid: String?,
        val selectedExercise: ExercisePickerItemUiModel?,
        val recentExercises: ImmutableList<ExercisePickerItemUiModel>,
        val preset: ChartPresetUiModel,
        val metric: ChartMetricUiModel,
        val points: ImmutableList<ChartPointUiModel>,
        val footerStats: ChartFooterStatsUiModel?,
        val activeTooltip: ChartTooltipUiModel?,
        val isPickerOpen: Boolean,
        val isEmpty: Boolean,
    ) : Store.State {

        val showMetricToggle: Boolean
            get() = selectedExercise?.type ==
                io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel.WEIGHTED

        companion object {

            fun create(initialUuid: String?): State = State(
                isLoading = true,
                initialUuid = initialUuid,
                selectedExercise = null,
                recentExercises = persistentListOf(),
                preset = ChartPresetUiModel.MONTHS_3,
                metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
                points = persistentListOf(),
                footerStats = null,
                activeTooltip = null,
                isPickerOpen = false,
                isEmpty = false,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Common : Action {
            data object Init : Common
        }

        sealed interface Click : Action {
            data class OnPresetSelect(val preset: ChartPresetUiModel) : Click
            data class OnMetricSelect(val metric: ChartMetricUiModel) : Click
            data object OnPickerOpen : Click
            data object OnPickerDismiss : Click
            data class OnPickerItemSelect(val uuid: String) : Click
            data class OnPointTap(val point: ChartPointUiModel) : Click
            data object OnTooltipDismiss : Click
            data object OnTooltipTap : Click
            data object OnEmptyCtaClick : Click
            data object OnBack : Click
        }

        sealed interface Navigation : Action {
            data class OpenPastSession(val sessionUuid: String) : Navigation
            data object OpenHome : Navigation
            data object PopBack : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
    }
}
