// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.mvi.mapper.toPickerItems
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import kotlinx.collections.immutable.persistentListOf
import javax.inject.Inject

private const val PICKER_LIMIT = 5

@Suppress("TooManyFunctions")
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
            Action.Click.OnConflictResume -> processConflictResume()
            Action.Click.OnConflictDeleteAndStart -> processConflictDeleteAndStart()
            Action.Click.OnConflictDismiss -> processConflictDismiss()
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
        interactor.observeRecentTrainings(PICKER_LIMIT).launch { trainings ->
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
        launch {
            when (val resolution = interactor.resolveStartConflict(trainingUuid)) {
                SessionConflictResolver.Resolution.ProceedFresh -> consumeOnMain(
                    Action.Navigation.OpenLiveWorkoutFresh(trainingUuid),
                )

                is SessionConflictResolver.Resolution.SilentResume -> consumeOnMain(
                    Action.Navigation.OpenLiveWorkoutResume(resolution.sessionUuid),
                )

                is SessionConflictResolver.Resolution.NeedsUserChoice -> {
                    val activeName = interactor.getTrainingName(resolution.active.trainingUuid)
                        ?.takeIf { it.isNotBlank() }
                        ?: resourceWrapper.getString(R.string.feature_home_conflict_unnamed)
                    val info = State.ConflictInfo(
                        activeSessionUuid = resolution.active.sessionUuid,
                        requestedTrainingUuid = trainingUuid,
                        activeSessionName = activeName,
                        progressLabel = resourceWrapper.getString(
                            R.string.feature_home_conflict_progress_format,
                            0,
                            0,
                        ),
                    )
                    updateStateImmediate { it.copy(pendingConflict = info) }
                    sendEvent(
                        Event.ShowActiveSessionConflict(
                            activeSessionName = info.activeSessionName,
                            progressLabel = info.progressLabel,
                        ),
                    )
                }
            }
        }
    }

    private fun processConflictResume() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val info = state.value.pendingConflict ?: return
        updateState { it.copy(pendingConflict = null) }
        consume(Action.Navigation.OpenLiveWorkoutResume(info.activeSessionUuid))
    }

    private fun processConflictDeleteAndStart() {
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        val info = state.value.pendingConflict ?: return
        updateState { it.copy(pendingConflict = null) }
        launch {
            interactor.deleteSession(info.activeSessionUuid)
            consumeOnMain(Action.Navigation.OpenLiveWorkoutFresh(info.requestedTrainingUuid))
        }
    }

    private fun processConflictDismiss() {
        updateState { it.copy(pendingConflict = null) }
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
