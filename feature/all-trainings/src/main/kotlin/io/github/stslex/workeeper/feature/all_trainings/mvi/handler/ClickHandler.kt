// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractor
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.PendingBulkDelete
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.SelectionMode
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentSet
import javax.inject.Inject

@Suppress("TooManyFunctions")
@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: AllTrainingsInteractor,
    store: AllTrainingsHandlerStore,
) : Handler<Action.Click>, AllTrainingsHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            is Action.Click.OnTrainingClick -> processTrainingClick(action)
            is Action.Click.OnTrainingLongPress -> processTrainingLongPress(action)
            Action.Click.OnFabClick -> processFabClick()
            is Action.Click.OnTagFilterToggle -> processTagFilterToggle(action)
            is Action.Click.OnSelectionToggle -> processSelectionToggle(action)
            Action.Click.OnSelectionExit -> processSelectionExit()
            Action.Click.OnBulkArchive -> processBulkArchive()
            Action.Click.OnBulkDelete -> processBulkDelete()
            Action.Click.OnBulkDeleteConfirm -> processBulkDeleteConfirm()
            Action.Click.OnBulkDeleteDismiss -> processBulkDeleteDismiss()
        }
    }

    private fun processTrainingClick(action: Action.Click.OnTrainingClick) {
        val current = state.value
        if (current.isSelecting) {
            // Tap toggles selection while selection mode is on. Long-press is the entry,
            // single-tap then becomes a fast multi-select.
            processSelectionToggle(Action.Click.OnSelectionToggle(action.uuid))
            return
        }
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenDetail(action.uuid))
    }

    private fun processTrainingLongPress(action: Action.Click.OnTrainingLongPress) {
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        val current = state.value
        val mode = current.selectionMode
        if (mode is SelectionMode.On) {
            // Long-press on an already-selected row toggles it; this matches the spec note
            // that single-tap toggles after long-press has flipped the screen.
            processSelectionToggle(Action.Click.OnSelectionToggle(action.uuid))
            return
        }
        launchSelectionEnter(action.uuid)
    }

    private fun processFabClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenCreate)
    }

    private fun processTagFilterToggle(action: Action.Click.OnTagFilterToggle) {
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        updateState { current ->
            val next = if (action.tagUuid in current.activeTagFilter) {
                current.activeTagFilter - action.tagUuid
            } else {
                current.activeTagFilter + action.tagUuid
            }
            current.copy(activeTagFilter = next.toPersistentSet())
        }
    }

    private fun processSelectionToggle(action: Action.Click.OnSelectionToggle) {
        val current = state.value
        val mode = current.selectionMode
        if (mode !is SelectionMode.On) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val nextSelection = if (action.uuid in mode.selectedUuids) {
            mode.selectedUuids - action.uuid
        } else {
            mode.selectedUuids + action.uuid
        }
        if (nextSelection.isEmpty()) {
            updateState { it.copy(selectionMode = SelectionMode.Off) }
            return
        }
        launchSelectionUpdate(nextSelection.toPersistentSet().toSet().toPersistentSet())
    }

    private fun launchSelectionUpdate(nextSelection: Set<String>) {
        launch(
            onSuccess = { canDeleteAll ->
                updateStateImmediate { current ->
                    current.copy(
                        selectionMode = SelectionMode.On(
                            selectedUuids = nextSelection.toPersistentSet(),
                            canDeleteAll = canDeleteAll,
                        ),
                    )
                }
            },
        ) {
            interactor.canPermanentlyDelete(nextSelection)
        }
    }

    private fun launchSelectionEnter(seedUuid: String) {
        val seed = persistentSetOf(seedUuid)
        launch(
            onSuccess = { canDeleteAll ->
                updateStateImmediate { current ->
                    current.copy(
                        selectionMode = SelectionMode.On(
                            selectedUuids = seed,
                            canDeleteAll = canDeleteAll,
                        ),
                    )
                }
            },
        ) {
            interactor.canPermanentlyDelete(seed)
        }
    }

    private fun processSelectionExit() {
        if (!state.value.isSelecting) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(selectionMode = SelectionMode.Off) }
    }

    private fun processBulkArchive() {
        val mode = state.value.selectionMode as? SelectionMode.On ?: return
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        val targets = mode.selectedUuids.toSet()
        launch(
            onSuccess = { outcome ->
                updateStateImmediate { current ->
                    current.copy(selectionMode = SelectionMode.Off)
                }
                if (outcome.blockedNames.isEmpty()) {
                    sendEvent(Event.ShowBulkArchiveSuccess(outcome.archivedCount))
                } else {
                    sendEvent(
                        Event.ShowBulkArchiveBlocked(
                            archivedCount = outcome.archivedCount,
                            blockedNames = outcome.blockedNames,
                        ),
                    )
                }
            },
        ) {
            interactor.archiveTrainings(targets)
        }
    }

    private fun processBulkDelete() {
        val mode = state.value.selectionMode as? SelectionMode.On ?: return
        if (!mode.canDeleteAll) return
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        updateState { current ->
            current.copy(pendingBulkDelete = PendingBulkDelete(count = mode.selectedUuids.size))
        }
    }

    private fun processBulkDeleteConfirm() {
        val mode = state.value.selectionMode as? SelectionMode.On ?: return
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        val targets = mode.selectedUuids.toSet()
        launch(
            onSuccess = { count ->
                updateStateImmediate { current ->
                    current.copy(
                        selectionMode = SelectionMode.Off,
                        pendingBulkDelete = null,
                    )
                }
                sendEvent(Event.ShowBulkDeleteSuccess(count))
            },
        ) {
            interactor.deleteTrainings(targets)
        }
    }

    private fun processBulkDeleteDismiss() {
        updateState { current -> current.copy(pendingBulkDelete = null) }
    }

    @Suppress("unused")
    private fun State.placeholder(): State = this
}
