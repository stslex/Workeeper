// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui

import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.Action

internal sealed interface AllTrainingsBodyAction {

    /**
     * Single FAB tap. The handler decides bulk-delete vs create based on
     * `state.isSelecting`; the screen does not branch on selection mode.
     */
    data object FabClick : AllTrainingsBodyAction

    data object SelectionExit : AllTrainingsBodyAction

    data class TagFilterToggle(val tagUuid: String) : AllTrainingsBodyAction

    data class TrainingClick(val uuid: String) : AllTrainingsBodyAction

    data class TrainingLongPress(val uuid: String) : AllTrainingsBodyAction

    data object BulkDeleteConfirm : AllTrainingsBodyAction

    data object BulkDeleteDismiss : AllTrainingsBodyAction
}

internal fun AllTrainingsBodyAction.toAction(): Action = when (this) {
    AllTrainingsBodyAction.FabClick -> Action.Click.OnFabClick
    AllTrainingsBodyAction.SelectionExit -> Action.Click.OnSelectionExit
    is AllTrainingsBodyAction.TagFilterToggle -> Action.Click.OnTagFilterToggle(tagUuid)
    is AllTrainingsBodyAction.TrainingClick -> Action.Click.OnTrainingClick(uuid)
    is AllTrainingsBodyAction.TrainingLongPress -> Action.Click.OnTrainingLongPress(uuid)
    AllTrainingsBodyAction.BulkDeleteConfirm -> Action.Click.OnBulkDeleteConfirm
    AllTrainingsBodyAction.BulkDeleteDismiss -> Action.Click.OnBulkDeleteDismiss
}
