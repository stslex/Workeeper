// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.mvi.mapper.toPickerItems
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import kotlinx.collections.immutable.persistentListOf
import javax.inject.Inject

private const val PICKER_LIMIT = 5

@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: HomeInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: HomeHandlerStore,
) : Handler<Action.Click>, HomeHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnActiveSessionClick -> processSessionClick()
            Action.Click.OnSettingsClick -> processSettingsClick()
            is Action.Click.OnRecentSessionClick -> processRecentSessionClick(action.sessionUuid)
            Action.Click.OnStartTrainingClick -> processStartTrainingClick()
            is Action.Click.OnPickerTrainingSelected -> processPickerSelected(action.trainingUuid)
            Action.Click.OnPickerSeeAllClick -> processPickerSeeAll()
            Action.Click.OnPickerDismiss -> processPickerDismiss()
        }
    }

    private fun processSessionClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val session = state.value.activeSession ?: return
        consume(Action.Navigation.OpenLiveWorkoutResume(session.sessionUuid))
    }

    private fun processSettingsClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenSettings)
    }

    private fun processRecentSessionClick(sessionUuid: String) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenPastSession(sessionUuid))
    }

    private fun processStartTrainingClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                picker = State.PickerState.Visible(
                    templates = persistentListOf(),
                    isLoading = true,
                ),
            )
        }
        scope.launch(interactor.observeRecentTrainings(PICKER_LIMIT)) { trainings ->
            val now = state.value.nowMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
            updateStateImmediate { current ->
                if (current.picker is State.PickerState.Visible) {
                    current.copy(
                        picker = State.PickerState.Visible(
                            templates = trainings.toPickerItems(now, resourceWrapper),
                            isLoading = false,
                        ),
                    )
                } else {
                    current
                }
            }
        }
    }

    private fun processPickerSelected(trainingUuid: String) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(picker = State.PickerState.Hidden) }
        consume(Action.Navigation.OpenLiveWorkoutFresh(trainingUuid))
    }

    private fun processPickerSeeAll() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(picker = State.PickerState.Hidden) }
        consume(Action.Navigation.OpenAllTrainings)
    }

    private fun processPickerDismiss() {
        updateState { it.copy(picker = State.PickerState.Hidden) }
    }
}
