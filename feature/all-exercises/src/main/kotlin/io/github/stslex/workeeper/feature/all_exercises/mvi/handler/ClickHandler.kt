// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.PendingBulkDelete
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.PendingDelete
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.SelectionMode
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import javax.inject.Inject

private const val MAX_BLOCKED_TRAINING_NAMES = 2

@Suppress("TooManyFunctions")
@ViewModelScoped
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
            is Action.Click.OnArchiveSwipe -> processArchiveSwipe(action)
            is Action.Click.OnUndoArchive -> processUndoArchive(action)
            Action.Click.OnConfirmPermanentDelete -> processConfirmPermanentDelete()
            Action.Click.OnCancelPermanentDelete -> processCancelPermanentDelete()
            is Action.Click.OnSelectionToggle -> processSelectionToggle(action)
            Action.Click.OnSelectionExit -> processSelectionExit()
            Action.Click.OnBulkArchive -> processBulkArchive()
            Action.Click.OnBulkDelete -> processBulkDelete()
            Action.Click.OnBulkDeleteConfirm -> processBulkDeleteConfirm()
            Action.Click.OnBulkDeleteDismiss -> processBulkDeleteDismiss()
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
        launchSelectionEnter(action.uuid)
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
                    sendEvent(
                        Event.ShowArchiveSuccess(
                            uuid = action.uuid,
                            message = resourceWrapper.getString(
                                R.string.feature_all_exercises_archive_success_format,
                                action.name,
                            ),
                        ),
                    )

                is ArchiveResult.Blocked ->
                    sendEvent(
                        Event.ShowArchiveBlocked(
                            message = resourceWrapper.getString(
                                R.string.feature_all_exercises_archive_blocked_format,
                                result.activeTrainings.toOverflowPreview(),
                            ),
                        ),
                    )
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
        if (next.isEmpty()) {
            updateState { it.copy(selectionMode = SelectionMode.Off) }
            return
        }
        launchSelectionUpdate(next.toSet())
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
            interactor.canBulkPermanentDelete(nextSelection)
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
            interactor.canBulkPermanentDelete(seed)
        }
    }

    private fun processSelectionExit() {
        if (!state.value.isSelecting) return
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { it.copy(selectionMode = SelectionMode.Off) }
    }

    private fun processBulkArchive() {
        val mode = state.value.selectionMode as? SelectionMode.On ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        val targets = mode.selectedUuids.toSet()
        launch(
            onSuccess = { outcome ->
                updateStateImmediate { current ->
                    current.copy(selectionMode = SelectionMode.Off)
                }
                if (outcome.blockedNames.isEmpty()) {
                    sendEvent(
                        Event.ShowBulkArchiveSuccess(
                            message = resourceWrapper.getQuantityString(
                                R.plurals.feature_all_exercises_bulk_archive_success,
                                outcome.archivedCount,
                                outcome.archivedCount,
                            ),
                        ),
                    )
                } else {
                    sendEvent(
                        Event.ShowBulkArchiveBlocked(
                            message = resourceWrapper.getString(
                                R.string.feature_all_exercises_bulk_archive_partial_format,
                                outcome.archivedCount,
                                outcome.blockedNames.toOverflowPreview(),
                            ),
                        ),
                    )
                }
            },
        ) {
            interactor.bulkArchive(targets)
        }
    }

    private fun processBulkDelete() {
        val mode = state.value.selectionMode as? SelectionMode.On ?: return
        if (!mode.canDeleteAll) return
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
            onSuccess = { count ->
                updateStateImmediate { current ->
                    current.copy(
                        selectionMode = SelectionMode.Off,
                        pendingBulkDelete = null,
                    )
                }
                sendEvent(
                    Event.ShowBulkDeleteSuccess(
                        message = resourceWrapper.getQuantityString(
                            R.plurals.feature_all_exercises_bulk_delete_success,
                            count,
                            count,
                        ),
                    ),
                )
            },
        ) {
            interactor.bulkPermanentDelete(targets)
        }
    }

    private fun processBulkDeleteDismiss() {
        updateState { current -> current.copy(pendingBulkDelete = null) }
    }

    private fun List<String>.toOverflowPreview(): String {
        val visible = take(MAX_BLOCKED_TRAINING_NAMES).joinToString(separator = ", ")
        val overflow = size - MAX_BLOCKED_TRAINING_NAMES
        if (overflow <= 0) return visible
        val overflowLabel = resourceWrapper.getString(R.string.feature_all_exercises_overflow_format, overflow)
        return "$visible, $overflowLabel"
    }
}
