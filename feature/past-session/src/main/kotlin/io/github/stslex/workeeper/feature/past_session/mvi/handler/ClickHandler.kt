// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.past_session.di.PastSessionHandlerStore
import io.github.stslex.workeeper.feature.past_session.di.PastSessionScope
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor
import io.github.stslex.workeeper.feature.past_session.domain.model.SetDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.past_session.mvi.mapper.PastSessionUiMapper.toDomain
import io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.past_session.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.past_session.mvi.store.DialogState
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job

@SingleIn(PastSessionScope::class)
internal class ClickHandler @Inject constructor(
    private val interactor: PastSessionInteractor,
    store: PastSessionHandlerStore,
) : Handler<Action.Click>, PastSessionHandlerStore by store {

    private var processReorderJob: Job? = null

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnBackClick -> processBack()
            Action.Click.OnSessionMenuClick -> processSessionMenuClick()
            Action.Click.OnSheetDismiss -> processSheetDismiss()
            Action.Click.OnDeleteClick -> processDeleteClick()
            Action.Click.OnDeleteConfirm -> processDeleteConfirm()
            Action.Click.OnDeleteDismiss -> processDeleteDismiss()
            Action.Click.OnPrTagClick -> processPrTagClick()
            Action.Click.OnPrExplainerDismiss -> processPrExplainerDismiss()
            Action.Click.OnRetryLoad -> consume(Action.Common.Init)
            is Action.Click.OnSetTypeChange -> processSetTypeChange(action)
            is Action.Click.OnSetReorder -> processSetReorder(action)
            Action.Click.OnDragStarted -> processOnDragStarted()
            is Action.Click.OnExerciseHeaderClick -> processExerciseHeaderClick(action)
        }
    }

    /** A header tap flips this card's membership in the open set and nothing else (§7). */
    private fun processExerciseHeaderClick(action: Action.Click.OnExerciseHeaderClick) {
        updateState { current ->
            val loaded = current.phase as? State.Phase.Loaded ?: return@updateState current
            val uuid = action.performedExerciseUuid
            if (loaded.detail.exercises.none { it.performedExerciseUuid == uuid }) {
                return@updateState current
            }
            current.copy(
                expandedExerciseUuids = if (uuid in current.expandedExerciseUuids) {
                    current.expandedExerciseUuids - uuid
                } else {
                    current.expandedExerciseUuids + uuid
                }.toImmutableSet(),
            )
        }
    }

    private fun processOnDragStarted() {
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
    }

    private fun processBack() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.Back)
    }

    private fun processSessionMenuClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(bottomSheetState = BottomSheetState.SessionMenu) }
    }

    private fun processSheetDismiss() {
        updateState { it.copy(bottomSheetState = BottomSheetState.Hidden) }
    }

    /** Opening from the sheet: the sheet closes and the confirmation replaces it. */
    private fun processDeleteClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState {
            it.copy(
                bottomSheetState = BottomSheetState.Hidden,
                dialogState = DialogState.DeleteConfirm,
            )
        }
    }

    private fun processDeleteDismiss() {
        updateState { it.copy(dialogState = DialogState.Hidden) }
    }

    private fun processPrTagClick() {
        updateState { it.copy(dialogState = DialogState.PrExplainer) }
    }

    private fun processPrExplainerDismiss() {
        updateState { it.copy(dialogState = DialogState.Hidden) }
    }

    private fun processDeleteConfirm() {
        sendEvent(Event.HapticClick(HapticFeedbackType.Confirm))
        val sessionUuid = state.value.sessionUuid
        updateState { it.copy(dialogState = DialogState.Hidden) }
        launch(
            onSuccess = {
                sendEvent(Event.DeletedSnackbar)
                consumeOnMain(Action.Navigation.Back)
            },
            onError = { _ -> sendEvent(Event.ShowError(ErrorType.LoadFailed)) },
        ) {
            interactor.deleteSession(sessionUuid)
        }
    }

    private fun processSetTypeChange(action: Action.Click.OnSetTypeChange) {
        val current = state.value.phase as? State.Phase.Loaded ?: return
        val updatedDetail = current.detail.copy(
            exercises = current.detail.exercises.map { exercise ->
                if (exercise.sets.none { it.setUuid == action.setUuid }) {
                    exercise
                } else {
                    exercise.copy(
                        sets = exercise.sets.map { set ->
                            if (set.setUuid == action.setUuid) {
                                set.copy(type = action.type)
                            } else {
                                set
                            }
                        }.toImmutableList(),
                    )
                }
            }.toImmutableList(),
        )
        updateState {
            it.copy(phase = State.Phase.Loaded(detail = updatedDetail))
        }
        val targetSet = updatedDetail
            .exercises
            .asSequence()
            .flatMap { it.sets.asSequence() }
            .firstOrNull { it.setUuid == action.setUuid } ?: return
        val weight = targetSet.weightInput.toDoubleOrNull()
        val reps = targetSet.repsInput.toIntOrNull() ?: return
        persistSet(
            performedExerciseUuid = targetSet.performedExerciseUuid,
            position = targetSet.position,
            setUuid = targetSet.setUuid,
            weight = weight,
            reps = reps,
            type = action.type.toDomain(),
        )
    }

    private fun persistSet(
        performedExerciseUuid: String,
        position: Int,
        setUuid: String,
        weight: Double?,
        reps: Int,
        type: SetTypeDomain,
    ) {
        launch(
            onError = { _ -> sendEvent(Event.SaveFailedSnackbar) },
        ) {
            interactor.updateSet(
                performedExerciseUuid = performedExerciseUuid,
                set = SetDomain(
                    uuid = setUuid,
                    reps = reps,
                    position = position,
                    weight = weight,
                    type = type,
                ),
            )
        }
    }

    private fun processSetReorder(action: Action.Click.OnSetReorder) {
        val current = state.value.phase as? State.Phase.Loaded ?: return
        val targetExercise = current.detail.exercises
            .firstOrNull { it.performedExerciseUuid == action.performedExerciseUuid }
            ?: return
        if (action.from == action.to) return
        if (action.from !in targetExercise.sets.indices) return
        if (action.to !in targetExercise.sets.indices) return

        // Permute in memory first; each set's `position` is rewritten from its new index.
        val reorderedSets = targetExercise.sets
            .toMutableList()
            .apply { add(action.to, removeAt(action.from)) }
            .mapIndexed { index, set -> set.copy(position = index) }
            .toImmutableList()
        val updatedDetail = current.detail.copy(
            exercises = current.detail.exercises.map { exercise ->
                if (exercise.performedExerciseUuid == action.performedExerciseUuid) {
                    exercise.copy(sets = reorderedSets)
                } else {
                    exercise
                }
            }.toImmutableList(),
        )
        sendEvent(Event.HapticClick(HapticFeedbackType.Confirm))
        updateState {
            it.copy(phase = State.Phase.Loaded(detail = updatedDetail))
        }
        val orderedSetUuids = reorderedSets.map { it.setUuid }

        processReorderJob?.cancel()
        processReorderJob = launch(
            onError = { _ -> sendEvent(Event.SaveFailedSnackbar) },
        ) {
            interactor.reorderSets(
                performedExerciseUuid = action.performedExerciseUuid,
                orderedSetUuids = orderedSetUuids,
            )
        }
    }
}
