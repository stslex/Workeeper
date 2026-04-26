// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.settings.di.ArchiveHandlerStore
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Event
import javax.inject.Inject

@ViewModelScoped
internal class ArchiveClickHandler @Inject constructor(
    private val interactor: SettingsInteractor,
    store: ArchiveHandlerStore,
) : Handler<Action.Click>, ArchiveHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            is Action.Click.OnSegmentChange -> processSegmentChange(action)
            is Action.Click.OnRestoreClick -> processRestore(action.item)
            is Action.Click.OnPermanentDeleteClick -> processDeleteRequest(action.item)
            Action.Click.OnDeleteConfirm -> processDeleteConfirm()
            Action.Click.OnDeleteDismiss -> processDeleteDismiss()
            is Action.Click.OnUndoRestore -> processUndoRestore(action.item)
        }
    }

    private fun processSegmentChange(action: Action.Click.OnSegmentChange) {
        if (state.value.selectedSegment == action.segment) return
        sendEvent(Event.Haptic(HapticFeedbackType.SegmentTick))
        updateState { it.copy(selectedSegment = action.segment) }
    }

    private fun processRestore(item: ArchivedItem) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        launch {
            when (item) {
                is ArchivedItem.Exercise -> interactor.restoreExercise(item.uuid)
                is ArchivedItem.Training -> interactor.restoreTraining(item.uuid)
            }
            sendEvent(Event.ShowRestoredSnackbar(item))
        }
    }

    private fun processDeleteRequest(item: ArchivedItem) {
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        updateState {
            it.copy(
                pendingDeleteTarget = item,
                pendingDeleteImpact = null,
                deleteImpactLoading = true,
            )
        }
        launch {
            val impact = when (item) {
                is ArchivedItem.Exercise -> interactor.countExerciseSessions(item.uuid)
                is ArchivedItem.Training -> interactor.countTrainingSessions(item.uuid)
            }
            updateStateImmediate {
                it.copy(pendingDeleteImpact = impact, deleteImpactLoading = false)
            }
        }
    }

    private fun processDeleteConfirm() {
        val target = state.value.pendingDeleteTarget ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        updateState {
            it.copy(
                pendingDeleteTarget = null,
                pendingDeleteImpact = null,
                deleteImpactLoading = false,
            )
        }
        launch {
            when (target) {
                is ArchivedItem.Exercise -> interactor.permanentlyDeleteExercise(target.uuid)
                is ArchivedItem.Training -> interactor.permanentlyDeleteTraining(target.uuid)
            }
            sendEvent(Event.ShowPermanentlyDeletedSnackbar)
        }
    }

    private fun processDeleteDismiss() {
        updateState {
            it.copy(
                pendingDeleteTarget = null,
                pendingDeleteImpact = null,
                deleteImpactLoading = false,
            )
        }
    }

    private fun processUndoRestore(item: ArchivedItem) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        launch {
            when (item) {
                is ArchivedItem.Exercise -> interactor.reArchiveExercise(item.uuid)
                is ArchivedItem.Training -> interactor.reArchiveTraining(item.uuid)
            }
        }
    }
}
