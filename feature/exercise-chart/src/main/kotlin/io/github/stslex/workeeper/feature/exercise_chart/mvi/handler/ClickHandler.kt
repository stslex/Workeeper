// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartScope
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ChartReadoutMapper
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event

@SingleIn(ExerciseChartScope::class)
internal class ClickHandler @Inject constructor(
    private val commonHandler: CommonHandler,
    private val resourceWrapper: ResourceWrapper,
    store: ExerciseChartHandlerStore,
) : Handler<Action.Click>, ExerciseChartHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            is Action.Click.OnPresetSelect -> processPresetSelect(action)
            is Action.Click.OnMetricSelect -> processMetricSelect(action)
            Action.Click.OnPickerOpen -> updateState { it.copy(isPickerOpen = true) }
            Action.Click.OnPickerDismiss -> updateState { it.copy(isPickerOpen = false) }
            is Action.Click.OnPickerItemSelect -> processPickerItemSelect(action)
            is Action.Click.OnScrub -> processScrub(action)
            Action.Click.OnEmptyCtaClick -> consume(Action.Navigation.OpenHome)
            Action.Click.OnBack -> consume(Action.Navigation.PopBack)
        }
    }

    private fun processPresetSelect(action: Action.Click.OnPresetSelect) {
        val current = state.value
        if (current.preset == action.preset) return
        val selected = current.selectedExercise ?: return
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        // `emptyReason` is NOT cleared here: it describes this exercise, and the exercise
        // has not changed. Clearing it eagerly is what used to drop the screen out of its
        // resolved empty state mid-reload — taking the recovery chips with it. loadChart
        // owns the transition in both directions.
        updateState {
            it.copy(
                preset = action.preset,
                isLoading = true,
            )
        }
        commonHandler.loadChart(selected)
    }

    private fun processMetricSelect(action: Action.Click.OnMetricSelect) {
        val current = state.value
        if (current.metric == action.metric) return
        val selected = current.selectedExercise ?: return
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        // See [processPresetSelect]: same exercise, so the resolved `emptyReason` stands
        // until loadChart resolves the new metric.
        updateState {
            it.copy(
                metric = action.metric,
                isLoading = true,
            )
        }
        commonHandler.loadChart(selected)
    }

    private fun processPickerItemSelect(action: Action.Click.OnPickerItemSelect) {
        val current = state.value
        val item = current.recentExercises.firstOrNull { it.uuid == action.uuid } ?: return
        if (current.selectedExercise?.uuid == item.uuid) {
            updateState { it.copy(isPickerOpen = false) }
            return
        }
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState {
            it.copy(
                selectedExercise = item,
                isPickerOpen = false,
                // Clear EXERCISE_NOT_FOUND immediately on selection — the new selection
                // is what's loading; loadChart will set NO_DATA_FOR_EXERCISE if the result
                // is empty.
                emptyReason = null,
                isLoading = true,
            )
        }
        commonHandler.loadChart(item)
    }

    /**
     * The scrub (§4.6): a repeated index is a no-op — which is what makes the haptic a tick
     * *per crossed point* (`navigator.vibrate(4)` fires in the mockup only when the snapped
     * index changes). SegmentTick is the same vocabulary the preset/metric segments use.
     */
    private fun processScrub(action: Action.Click.OnScrub) {
        val current = state.value
        if (action.index == current.activeIndex) return
        if (action.index !in current.points.indices) return
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        // Rule 1 (compose-state-discipline): the readout is mapped BEFORE the lambda —
        // resource lookups have no place inside a CAS body on a per-crossed-point path.
        val readout = ChartReadoutMapper.toReadout(
            points = current.points,
            activeIndex = action.index,
            metric = current.metric,
            type = current.selectedExercise?.type ?: ExerciseTypeUiModel.WEIGHTED,
            resourceWrapper = resourceWrapper,
        )
        updateState {
            it.copy(
                activeIndex = action.index,
                readout = readout,
            )
        }
    }
}
