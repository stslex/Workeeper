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
     * Why the chart canvas is not currently rendered. Four distinct cases drive four
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

        /**
         * A read threw, so there is no answer — as opposed to an answer of "nothing". This is
         * the only reason whose recovery is to ask again, and it is the reason [Content] can
         * resolve at all after a failure: without it the load leaves `emptyReason` null and no
         * points, which is [Content.Loading] forever, and the route draws nothing.
         */
        LOAD_FAILED,
    }

    /**
     * What the screen may draw right now — **the single decision**, derived once on
     * [State] rather than inferred at the call site from three fields.
     *
     * The canvas exists only under [Plot], and [Plot] is unreachable unless the dataset is
     * actually plottable. That is the invariant: no state can be emitted in which an
     * unplottable dataset reaches the draw phase. The old screen inferred the branch from
     * `isLoading` / `points.isEmpty()` / `emptyReason` independently, and a sub-threshold
     * one-point dataset satisfied none of the guards — it composed the canvas, which drew
     * its four gridlines and bailed, so a metric tap on a one-session exercise showed a
     * bare grid with stale footer numbers for the whole DB round-trip.
     */
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
        // The scrubbed point (§4.5/§4.6): drives the readout and the canvas's scrub line +
        // enlarged point. Defaults to the last (most recent) point on load; a metric switch
        // preserves the same session, while a preset/exercise switch resets it.
        val activeIndex: Int?,
        val readout: ChartReadoutUiModel?,
        val isPickerOpen: Boolean,
        /**
         * The picker's filter-as-you-type text. Only the query is state: the filtered list
         * is a pure function of it and [recentExercises], derived where it is drawn, so the
         * two can never disagree. Reset whenever the sheet opens or closes.
         */
        val pickerQuery: String,
        val emptyReason: EmptyReason?,
    ) : Store.State {

        val showMetricToggle: Boolean
            get() = selectedExercise?.type == ExerciseTypeUiModel.WEIGHTED

        /**
         * See [Content]. A resolved reason wins over a stale dataset, and a dataset that
         * cannot be drawn never reaches [Content.Plot].
         *
         * `isLoading` deliberately does not participate: while a reload is in flight the
         * previous **resolved** content stays on screen — which is what lets the canvas
         * retarget its animations from where the line already is instead of tearing the
         * chart down and rebuilding it. A reload that resolves to nothing lands on
         * [Content.Empty] without ever passing through a blank frame.
         */
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

            /**
             * The §4.6 scrub gesture: the canvas mapped a pointer x to the nearest point
             * index. The handler dedups (a repeat of the current index is a no-op) and owns
             * the per-crossing haptic tick.
             */
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
