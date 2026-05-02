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
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartTooltipUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.LocalDate

internal interface ExerciseChartStore : Store<State, Action, Event> {

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
         * An exercise is selected but produced zero points for the active preset window.
         * Picker stays accessible; preset chips stay accessible — a wider window may show
         * data.
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
        val activeTooltip: ChartTooltipUiModel?,
        // Effective canvas window — populated from FoldResult so the canvas reflects what
        // the mapper actually decided to render (including the ±14d sparse-data tightening).
        // Null until the first chart load completes; null also when the result is empty.
        val windowStartDay: LocalDate?,
        val windowEndDay: LocalDate?,
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
                activeTooltip = null,
                windowStartDay = null,
                windowEndDay = null,
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
