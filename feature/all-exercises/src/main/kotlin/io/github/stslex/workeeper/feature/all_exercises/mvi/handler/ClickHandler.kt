// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor.ArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.PendingDelete
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentSet
import javax.inject.Inject

@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: AllExercisesInteractor,
    store: AllExercisesHandlerStore,
) : Handler<Action.Click>, AllExercisesHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            is Action.Click.OnExerciseClick -> processExerciseClick(action)
            Action.Click.OnFabClick -> processFabClick()
            is Action.Click.OnTagFilterToggle -> processTagFilterToggle(action)
            is Action.Click.OnArchiveSwipe -> processArchiveSwipe(action)
            is Action.Click.OnUndoArchive -> processUndoArchive(action)
            Action.Click.OnConfirmPermanentDelete -> processConfirmPermanentDelete()
            Action.Click.OnCancelPermanentDelete -> processCancelPermanentDelete()
        }
    }

    private fun processExerciseClick(action: Action.Click.OnExerciseClick) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenDetail(action.uuid))
    }

    private fun processFabClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenCreate)
    }

    private fun processTagFilterToggle(action: Action.Click.OnTagFilterToggle) {
        sendEvent(Event.Haptic(HapticFeedbackType.SegmentTick))
        updateState { current ->
            val next = if (action.tagUuid in current.activeTagFilter) {
                current.activeTagFilter - action.tagUuid
            } else {
                current.activeTagFilter + action.tagUuid
            }
            current.copy(activeTagFilter = next.toPersistentSet())
        }
    }

    private fun processArchiveSwipe(action: Action.Click.OnArchiveSwipe) {
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        launch {
            // Unused exercises (no history, not in any active template) get a permanent
            // delete prompt so the archive doesn't fill up with throwaway entries.
            if (interactor.canPermanentlyDelete(action.uuid)) {
                updateStateImmediate { current ->
                    current.copy(
                        pendingPermanentDelete = PendingDelete(
                            uuid = action.uuid,
                            name = action.name,
                        ),
                    )
                }
                return@launch
            }
            when (val result = interactor.archiveExercise(action.uuid)) {
                ArchiveResult.Success ->
                    sendEvent(Event.ShowArchiveSuccess(action.name, action.uuid))

                is ArchiveResult.Blocked ->
                    sendEvent(Event.ShowArchiveBlocked(result.activeTrainings))
            }
        }
    }

    private fun processUndoArchive(action: Action.Click.OnUndoArchive) {
        launch {
            interactor.restoreExercise(action.uuid)
        }
    }

    private fun processConfirmPermanentDelete() {
        val pending = state.value.pendingPermanentDelete ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        updateState { it.copy(pendingPermanentDelete = null) }
        launch {
            interactor.permanentlyDelete(pending.uuid)
            sendEvent(Event.ShowPermanentDeleteSuccess(pending.name))
        }
    }

    private fun processCancelPermanentDelete() {
        updateState { it.copy(pendingPermanentDelete = null) }
    }
}
