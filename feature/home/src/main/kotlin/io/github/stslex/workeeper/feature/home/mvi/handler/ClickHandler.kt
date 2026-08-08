// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapper.toPickerItems
import io.github.stslex.workeeper.feature.home.mvi.mapper.StartCardModeMapper.toDomain
import io.github.stslex.workeeper.feature.home.mvi.model.StartCardBodyUi
import io.github.stslex.workeeper.feature.home.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import kotlinx.collections.immutable.persistentListOf

private const val PICKER_LIMIT = 5

@Suppress("TooManyFunctions")
@SingleIn(HomeScope::class)
internal class ClickHandler @Inject constructor(
    private val interactor: HomeInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: HomeHandlerStore,
) : Handler<Action.Click>, HomeHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnModeLabelClick -> processModeLabelClick()
            is Action.Click.OnModeSelected -> processModeSelected(action.mode)
            Action.Click.OnModeSheetDismiss -> processModeSheetDismiss()
            Action.Click.OnActiveSessionClick -> processSessionClick()
            Action.Click.OnChartsClick -> processChartsClick()
            Action.Click.OnSettingsClick -> processSettingsClick()
            is Action.Click.OnRecentSessionClick -> processRecentSessionClick(action.sessionUuid)
            Action.Click.OnStartTrainingClick -> processStartTrainingClick()
            Action.Click.OnStartActionClick -> processStartActionClick()
            is Action.Click.OnPickerTrainingSelected -> processPickerSelected(action.trainingUuid)
            Action.Click.OnStartBlankClick -> processStartBlankClick()
            Action.Click.OnPickerSeeAllClick -> processPickerSeeAll()
            Action.Click.OnPickerDismiss -> processPickerDismiss()
            Action.Click.OnConflictResume -> processConflictResume()
            Action.Click.OnConflictDeleteAndStart -> processConflictDeleteAndStart()
            Action.Click.OnConflictDismiss -> processConflictDismiss()
        }
    }

    private fun processModeLabelClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(bottomSheet = BottomSheetState.StartModePicker) }
    }

    /**
     * Persist-only on purpose: `State.startCardMode` and the body follow through
     * `CommonHandler`'s DataStore-driven pipeline, so head and readout swap together —
     * a mode label over a sibling mode's data never renders.
     */
    private fun processModeSelected(mode: StartCardModeUi) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(bottomSheet = BottomSheetState.Hidden) }
        launch { interactor.setStartCardMode(mode.toDomain()) }
    }

    private fun processModeSheetDismiss() {
        updateState { it.copy(bottomSheet = BottomSheetState.Hidden) }
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

    private fun processChartsClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenCharts)
    }

    private fun processRecentSessionClick(sessionUuid: String) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenPastSession(sessionUuid))
    }

    private fun processStartTrainingClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                bottomSheet = BottomSheetState.TrainingPicker(
                    templates = persistentListOf(),
                    isLoading = true,
                ),
            )
        }
        interactor.observeRecentTrainings(PICKER_LIMIT).launch { trainings ->
            val now = state.value.nowMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
            updateStateImmediate { current ->
                if (current.bottomSheet is BottomSheetState.TrainingPicker) {
                    current.copy(
                        bottomSheet = BottomSheetState.TrainingPicker(
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
        updateState { it.copy(bottomSheet = BottomSheetState.Hidden) }
        resolveConflictAndStart(trainingUuid)
    }

    /**
     * The card's primary button, for every mode — §3.4: on «Забытая тренировка» it starts
     * THAT training directly, with no picker in between; every other body opens the picker.
     *
     * The rule is executed here, so this is where it is written down. The card dispatches
     * one action and names no mode, which means a fifth mode adds an arm to this `when` and
     * to nothing else — with the branch up in the composable it would have needed one there
     * too, and the compiler would not have said so.
     *
     * **The discriminator is the BODY, not the mode**, and the `when` is exhaustive rather
     * than an `as?` so the sealed type carries that. `Forgotten` occurs only under
     * FORGOTTEN_TRAINING, and that mode degrades to `Empty` when there is no template left
     * to start (HD2) — which must open the picker exactly as an `Empty` under any other mode
     * does. Reading it from `state.value` at click time is also what makes head and action
     * atomic: `CommonHandler` swaps mode and body in one `copy`, so the uuid started is
     * always the one the card was showing when it was tapped.
     */
    private fun processStartActionClick() {
        when (val body = state.value.startCardBody) {
            is StartCardBodyUi.Forgotten -> processStartForgotten(body.trainingUuid)

            is StartCardBodyUi.Week,
            is StartCardBodyUi.DaysSince,
            is StartCardBodyUi.TagIdle,
            is StartCardBodyUi.Empty,
            null,
            -> processStartTrainingClick()
        }
    }

    /** «Забытая тренировка»'s primary action — no picker, same conflict resolution. */
    private fun processStartForgotten(trainingUuid: String) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        resolveConflictAndStart(trainingUuid)
    }

    private fun resolveConflictAndStart(trainingUuid: String) {
        launch {
            when (val resolution = interactor.resolveStartConflict(trainingUuid)) {
                StartSessionConflict.ProceedFresh -> consumeOnMain(
                    Action.Navigation.OpenLiveWorkoutFresh(trainingUuid),
                )

                is StartSessionConflict.SilentResume -> consumeOnMain(
                    Action.Navigation.OpenLiveWorkoutResume(resolution.sessionUuid),
                )

                is StartSessionConflict.NeedsUserChoice -> {
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
        updateState { it.copy(bottomSheet = BottomSheetState.Hidden) }
        consume(Action.Navigation.OpenAllTrainings)
    }

    private fun processStartBlankClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(bottomSheet = BottomSheetState.Hidden) }
        consume(Action.Navigation.OpenLiveWorkoutBlank)
    }

    private fun processPickerDismiss() {
        updateState { it.copy(bottomSheet = BottomSheetState.Hidden) }
    }
}
