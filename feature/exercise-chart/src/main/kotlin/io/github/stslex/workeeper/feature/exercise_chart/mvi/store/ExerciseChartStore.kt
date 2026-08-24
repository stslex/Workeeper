// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartReadoutUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

interface ExerciseChartStore : Store<State, Action, Event> {

    /** Why the chart canvas is not rendered. Four cases, four different recovery actions. */
    @Stable
    enum class EmptyReason {
        /** Fresh install or no finished sessions ever — no exercises to pick from. */
        NO_FINISHED_SESSIONS,

        /** `initialUuid` was given but the exercise is not in the picker list. */
        EXERCISE_NOT_FOUND,

        /** Selected, but fewer than [State.MIN_CHART_POINTS] points in the preset window. */
        NO_DATA_FOR_EXERCISE,

        /** A read threw: no answer rather than an answer of "nothing". Recovery is a retry. */
        LOAD_FAILED,
    }

    /** What the screen may draw — one decision, so an unplottable dataset never reaches draw. */
    @Stable
    sealed interface Content {

        /** Nothing plottable and no resolved reason yet — the first load. */
        data object Loading : Content

        /** Resolved: there is a reason there is no chart. Carries the recovery affordances. */
        data class Empty(val reason: EmptyReason) : Content

        /** Resolved and plottable: at least [State.MIN_CHART_POINTS] points. */
        data object Plot : Content
    }

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
        // The scrubbed point (§4.5/§4.6); a metric switch preserves it, a preset switch resets.
        val activeIndex: Int?,
        val readout: ChartReadoutUiModel?,
        val isPickerOpen: Boolean,
        /** The picker's filter-as-you-type text; reset whenever the sheet opens or closes. */
        val pickerQuery: String,
        val emptyReason: EmptyReason?,
    ) : Store.State {

        val showMetricToggle: Boolean
            get() = selectedExercise?.type == ExerciseTypeUiModel.WEIGHTED

        /** See [Content]. `isLoading` stays out: a reload keeps the resolved content drawn. */
        val content: Content
            get() = when {
                emptyReason != null -> Content.Empty(emptyReason)
                points.size >= MIN_CHART_POINTS -> Content.Plot
                else -> Content.Loading
            }

        companion object {

            /** §4.8: "График появится после двух записанных сессий с этим упражнением." */
            const val MIN_CHART_POINTS = 2

            fun create(initialUuid: String?): State = State(
                isLoading = true,
                initialUuid = initialUuid,
                selectedExercise = null,
                recentExercises = persistentListOf(),
                // The chart is an exploration surface: show the full picture by default.
                preset = ChartPresetUiModel.ALL,
                metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
                points = persistentListOf(),
                footerStats = null,
                activeIndex = null,
                readout = null,
                isPickerOpen = false,
                pickerQuery = "",
                emptyReason = null,
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
            data class OnPickerQueryChange(val query: String) : Click

            /** The §4.6 scrub gesture; the handler dedups repeats and owns the haptic tick. */
            data class OnScrub(val index: Int) : Click
            data object OnEmptyCtaClick : Click
            data object OnRetryLoad : Click
            data object OnBack : Click
        }

        sealed interface Navigation : Action {
            data object OpenHome : Navigation
            data object PopBack : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
    }
}
