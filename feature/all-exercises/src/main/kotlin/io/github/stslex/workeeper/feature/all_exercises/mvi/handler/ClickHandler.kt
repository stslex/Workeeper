// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesScope
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor
import io.github.stslex.workeeper.feature.all_exercises.mvi.mapper.AllExercisesUiMapper
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.PendingBulkDelete
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.SelectionMode
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet

@Suppress("TooManyFunctions")
@SingleIn(AllExercisesScope::class)
internal class ClickHandler @Inject constructor(
    private val interactor: AllExercisesInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: AllExercisesHandlerStore,
) : Handler<Action.Click>, AllExercisesHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            is Action.Click.OnExerciseClick -> processExerciseClick(action)
            is Action.Click.OnExerciseLongPress -> processExerciseLongPress(action)
            Action.Click.OnFabClick -> processFabClick()
            is Action.Click.OnTagFilterToggle -> processTagFilterToggle(action)
            Action.Click.OnConfirmPermanentDelete -> processConfirmPermanentDelete()
            Action.Click.OnCancelPermanentDelete -> processCancelPermanentDelete()
            is Action.Click.OnSelectionToggle -> processSelectionToggle(action)
            Action.Click.OnSelectionExit -> processSelectionExit()
            Action.Click.OnBulkDelete -> processBulkDelete()
            Action.Click.OnBulkDeleteConfirm -> processBulkDeleteConfirm()
            Action.Click.OnBulkDeleteDismiss -> processBulkDeleteDismiss()
            Action.Click.OnBlockedArchiveDismiss -> processBlockedArchiveDismiss()
        }
    }

    private fun processExerciseClick(action: Action.Click.OnExerciseClick) {
        val current = state.value
        if (current.isSelecting) {
            processSelectionToggle(Action.Click.OnSelectionToggle(action.uuid))
            return
        }
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenDetail(action.uuid))
    }

    private fun processExerciseLongPress(action: Action.Click.OnExerciseLongPress) {
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        if (state.value.selectionMode is SelectionMode.On) {
            processSelectionToggle(Action.Click.OnSelectionToggle(action.uuid))
            return
        }
        updateState { current ->
            current.copy(
                selectionMode = SelectionMode.On(selectedUuids = persistentSetOf(action.uuid)),
            )
        }
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

    private fun processConfirmPermanentDelete() {
        val pending = state.value.pendingPermanentDelete ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        updateState { it.copy(pendingPermanentDelete = null) }
        launch {
            interactor.permanentlyDelete(pending.uuid)
            sendEvent(
                Event.ShowPermanentDeleteSuccess(
                    message = resourceWrapper.getString(R.string.feature_all_exercises_permanent_delete_success),
                ),
            )
        }
    }

    private fun processCancelPermanentDelete() {
        updateState { it.copy(pendingPermanentDelete = null) }
    }

    private fun processSelectionToggle(action: Action.Click.OnSelectionToggle) {
        val mode = state.value.selectionMode as? SelectionMode.On ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
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
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { it.copy(selectionMode = SelectionMode.Off) }
    }

    /**
     * Bulk-delete FAB → confirm dialog whenever at least one row is selected. The
     * underlying call is `bulkArchive` (soft-delete), not `bulkPermanentDelete` — permanent
     * delete requires zero session history per row, which the v2.4 selection-mode entry
     * cannot guarantee. Archived exercises are restorable from Settings → Archive.
     */
    private fun processBulkDelete() {
        val mode = state.value.selectionMode as? SelectionMode.On ?: return
        if (mode.selectedUuids.isEmpty()) return
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        updateState { current ->
            current.copy(pendingBulkDelete = PendingBulkDelete(count = mode.selectedUuids.size))
        }
    }

    private fun processBulkDeleteConfirm() {
        val mode = state.value.selectionMode as? SelectionMode.On ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        val targets = mode.selectedUuids.toSet()
        launch(
            onSuccess = { result ->
                if (result.blocked.isEmpty()) {
                    updateStateImmediate { current ->
                        current.copy(
                            selectionMode = SelectionMode.Off,
                            pendingBulkDelete = null,
                        )
                    }
                    sendEvent(
                        Event.ShowBulkDeleteSuccess(
                            message = resourceWrapper.getQuantityString(
                                R.plurals.feature_all_exercises_bulk_archive_success,
                                result.archivedCount,
                                result.archivedCount,
                            ),
                        ),
                    )
                } else {
                    // Any blocked exercise → one consistent, persistent mechanism: a dialog
                    // naming the blocking trainings, never a droppable snackbar.
                    val dialog = with(AllExercisesUiMapper) {
                        result.toBlockedArchiveDialog(resourceWrapper)
                    }
                    updateStateImmediate { current ->
                        current.copy(
                            selectionMode = SelectionMode.Off,
                            pendingBulkDelete = null,
                            blockedArchiveDialog = dialog,
                        )
                    }
                }
            },
        ) {
            interactor.bulkArchive(targets)
        }
    }

    private fun processBulkDeleteDismiss() {
        updateState { current -> current.copy(pendingBulkDelete = null) }
    }

    private fun processBlockedArchiveDismiss() {
        updateState { current -> current.copy(blockedArchiveDialog = null) }
    }
}
