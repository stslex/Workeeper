// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Event
import javax.inject.Inject

@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: ExerciseChartInteractor,
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
            is Action.Click.OnPointTap -> processPointTap(action)
            Action.Click.OnTooltipDismiss -> updateState { it.copy(activeTooltip = null) }
            Action.Click.OnTooltipTap -> processTooltipTap()
            Action.Click.OnEmptyCtaClick -> consume(Action.Navigation.OpenHome)
            Action.Click.OnBack -> consume(Action.Navigation.PopBack)
        }
    }

    private fun processPresetSelect(action: Action.Click.OnPresetSelect) {
        val current = state.value
        if (current.preset == action.preset) return
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        updateState { it.copy(preset = action.preset, activeTooltip = null, isLoading = true) }
        reloadFor(current.selectedExercise?.uuid)
    }

    private fun processMetricSelect(action: Action.Click.OnMetricSelect) {
        val current = state.value
        if (current.metric == action.metric) return
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        updateState { it.copy(metric = action.metric, activeTooltip = null, isLoading = true) }
        reloadFor(current.selectedExercise?.uuid)
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
                activeTooltip = null,
                isLoading = true,
            )
        }
        reloadFor(item.uuid)
    }

    private fun processPointTap(action: Action.Click.OnPointTap) {
        val current = state.value
        val tooltip = ExerciseChartUiMapper.toTooltip(
            point = action.point,
            exercise = current.selectedExercise,
            metric = current.metric,
            resourceWrapper = resourceWrapper,
        )
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        updateState { it.copy(activeTooltip = tooltip) }
    }

    private fun processTooltipTap() {
        val tooltip = state.value.activeTooltip ?: return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenPastSession(tooltip.sessionUuid))
    }

    private fun reloadFor(exerciseUuid: String?) {
        val current = state.value
        val target = current.selectedExercise ?: return
        if (exerciseUuid == null || target.uuid != exerciseUuid) return
        launch(
            onSuccess = { result ->
                updateStateImmediate {
                    it.copy(
                        points = result.points,
                        footerStats = result.footer,
                        isEmpty = result.points.isEmpty(),
                        isLoading = false,
                    )
                }
            },
        ) {
            interactor.loadChartData(
                exerciseUuid = target.uuid,
                preset = state.value.preset,
                metric = state.value.metric,
                type = target.type,
                now = System.currentTimeMillis(),
            )
        }
    }
}
