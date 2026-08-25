// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_trainings.R
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsScope
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractor
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.PendingBulkDelete
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State.SelectionMode
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet

@Suppress("TooManyFunctions")
@SingleIn(AllTrainingsScope::class)
internal class ClickHandler @Inject constructor(
    private val interactor: AllTrainingsInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: AllTrainingsHandlerStore,
) : Handler<Action.Click>, AllTrainingsHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            is Action.Click.OnTrainingClick -> processTrainingClick(action)
            is Action.Click.OnTrainingLongPress -> processTrainingLongPress(action)
            Action.Click.OnFabClick -> processFabClick()
            Action.Click.OnEmptyCreate -> consume(Action.Navigation.OpenCreate)
            Action.Click.OnEmptyStartBlank -> consume(Action.Navigation.OpenBlankSession)
            is Action.Click.OnTagFilterToggle -> processTagFilterToggle(action)
            Action.Click.OnClearTagFilter -> processClearTagFilter()
            is Action.Click.OnSelectionToggle -> processSelectionToggle(action)
            Action.Click.OnSelectionExit -> processSelectionExit()
            Action.Click.OnBulkDeleteConfirm -> processBulkDeleteConfirm()
            Action.Click.OnBulkDeleteDismiss -> processBulkDeleteDismiss()
        }
    }

    private fun processTrainingClick(action: Action.Click.OnTrainingClick) {
        val current = state.value
        if (current.isSelecting) {
            processSelectionToggle(Action.Click.OnSelectionToggle(action.uuid))
            return
        }
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenDetail(action.uuid))
    }

    private fun processTrainingLongPress(action: Action.Click.OnTrainingLongPress) {
        // §26 "Haptics": LongPress is for ENTERING selection; a toggle gets ContextClick instead,
        // and two haptics in a row read as a fault.
        if (state.value.selectionMode is SelectionMode.On) {
            processSelectionToggle(Action.Click.OnSelectionToggle(action.uuid))
            return
        }
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        updateState { current ->
            current.copy(
                selectionMode = SelectionMode.On(selectedUuids = persistentSetOf(action.uuid)),
            )
        }
    }

    private fun processFabClick() {
        val selectedUuid: Set<String> = (state.value.selectionMode as? SelectionMode.On)
            ?.selectedUuids
            .orEmpty()

        if (selectedUuid.isEmpty()) {
            sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
            consume(Action.Navigation.OpenCreate)
        } else {
            // §26 "Haptics": the FAB morph fires nothing; the long press already did.
            updateState { current ->
                current.copy(pendingBulkDelete = PendingBulkDelete(count = selectedUuid.size))
            }
        }
    }

    private fun processTagFilterToggle(action: Action.Click.OnTagFilterToggle) {
        // No haptic: §26's vocabulary is four constants and none of them is "a filter changed".
        updateState { current ->
            val next = if (action.tagUuid in current.activeTagFilter) {
                current.activeTagFilter - action.tagUuid
            } else {
                current.activeTagFilter + action.tagUuid
            }
            current.copy(activeTagFilter = next.toPersistentSet())
        }
    }

    /**
     * Clears the tag filter whole. No haptic, as with [processTagFilterToggle]. The early return
     * keeps a redundant emit from restarting the paging flow the filter feeds.
     */
    private fun processClearTagFilter() {
        if (state.value.activeTagFilter.isEmpty()) return
        updateState { current -> current.copy(activeTagFilter = persistentSetOf()) }
    }

    private fun processSelectionToggle(action: Action.Click.OnSelectionToggle) {
        val mode = state.value.selectionMode as? SelectionMode.On ?: return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val next = if (action.uuid in mode.selectedUuids) {
            mode.selectedUuids - action.uuid
        } else {
            mode.selectedUuids + action.uuid
        }
        updateState { current ->
            if (next.isEmpty()) {
                current.copy(selectionMode = SelectionMode.Off)
            } else {
                current.copy(
                    selectionMode = SelectionMode.On(selectedUuids = next.toPersistentSet()),
                )
            }
        }
    }

    private fun processSelectionExit() {
        if (!state.value.isSelecting) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(selectionMode = SelectionMode.Off) }
    }

    private fun processBulkDeleteConfirm() {
        val mode = state.value.selectionMode as? SelectionMode.On ?: return
        // §26 "Haptics": Confirm fires after the dialog, not on the button that opens it.
        sendEvent(Event.HapticClick(HapticFeedbackType.Confirm))
        val targets = mode.selectedUuids.toSet()
        launch(
            onSuccess = { result ->
                updateStateImmediate { current ->
                    current.copy(
                        selectionMode = SelectionMode.Off,
                        pendingBulkDelete = null,
                    )
                }
                val message = if (result.blockedNames.isEmpty()) {
                    resourceWrapper.getQuantityString(
                        R.plurals.feature_all_trainings_bulk_archive_success,
                        result.archivedCount,
                        result.archivedCount,
                    )
                } else {
                    resourceWrapper.getString(
                        R.string.feature_all_trainings_bulk_archive_partial_format,
                        result.archivedCount,
                        result.blockedNames.joinToString(", "),
                    )
                }
                sendEvent(Event.ShowBulkDeleteSuccess(message = message))
            },
        ) {
            interactor.archiveTrainings(targets)
        }
    }

    private fun processBulkDeleteDismiss() {
        updateState { current -> current.copy(pendingBulkDelete = null) }
    }
}
