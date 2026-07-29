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

    /**
     * Why the chart canvas is not currently rendered. Three distinct cases drive three
     * distinct empty-state UIs and CTAs — never collapse them into a single "isEmpty"
     * flag, the recovery action differs.
     */
    @Stable
    enum class EmptyReason {
        /** Fresh install or no finished sessions ever — no exercises to pick from. */
        NO_FINISHED_SESSIONS,

        /**
         * `initialUuid` was provided but the exercise is not in the picker list (archived,
         * permanently deleted, or its only performed rows are skipped / set-less). Picker
         * stays accessible — it is the user's recovery path.
         */
        EXERCISE_NOT_FOUND,

        /**
         * An exercise is selected but produced fewer than two points for the active preset
         * window (§4.8: the chart appears after two recorded sessions — one point is no
         * line, so sub-threshold is this state, not a degenerate chart). Picker stays
         * accessible; preset chips stay accessible — a wider window may show data.
         */
        NO_DATA_FOR_EXERCISE,
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
        // The scrubbed point (§4.5/§4.6): drives the readout and the canvas's scrub line +
        // enlarged point. Defaults to the last (most recent) point on load; a metric switch
        // preserves it (same day buckets), a preset/exercise switch resets it.
        val activeIndex: Int?,
        val readout: ChartReadoutUiModel?,
        val isPickerOpen: Boolean,
        val emptyReason: EmptyReason?,
    ) : Store.State {

        val showMetricToggle: Boolean
            get() = selectedExercise?.type == ExerciseTypeUiModel.WEIGHTED

        /** Picker is hidden only when there is literally nothing to pick from. */
        val isPickerAccessible: Boolean
            get() = recentExercises.isNotEmpty()

        companion object {

            fun create(initialUuid: String?): State = State(
                isLoading = true,
                initialUuid = initialUuid,
                selectedExercise = null,
                recentExercises = persistentListOf(),
                // The chart is an exploration surface — show the full picture by default
                // and let the user narrow with a preset chip when focusing. A 3M default
                // hid older history and produced a "no data" branch on long-dormant
                // exercises that actually had data.
                preset = ChartPresetUiModel.ALL,
                metric = ChartMetricUiModel.HEAVIEST_WEIGHT,
                points = persistentListOf(),
                footerStats = null,
                activeIndex = null,
                readout = null,
                isPickerOpen = false,
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

            /**
             * The §4.6 scrub gesture: the canvas mapped a pointer x to the nearest point
             * index. The handler dedups (a repeat of the current index is a no-op) and owns
             * the per-crossing haptic tick.
             */
            data class OnScrub(val index: Int) : Click
            data object OnEmptyCtaClick : Click
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
