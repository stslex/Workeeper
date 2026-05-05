// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui

import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action

internal sealed interface ExerciseEditBodyAction {

    data object CancelClick : ExerciseEditBodyAction

    data object SaveClick : ExerciseEditBodyAction

    data object EditImageClick : ExerciseEditBodyAction

    data object RemoveImageClick : ExerciseEditBodyAction

    data object ImageThumbnailClick : ExerciseEditBodyAction

    data object EditPlanClick : ExerciseEditBodyAction

    data class NameInput(val value: String) : ExerciseEditBodyAction

    data class DescriptionInput(val value: String) : ExerciseEditBodyAction

    data class TagSearchInput(val value: String) : ExerciseEditBodyAction

    data class TypeSelect(val type: ExerciseTypeUiModel) : ExerciseEditBodyAction

    data class TagToggle(val tagUuid: String) : ExerciseEditBodyAction

    data class TagRemove(val tagUuid: String) : ExerciseEditBodyAction

    data class TagCreate(val name: String) : ExerciseEditBodyAction
}

internal fun ExerciseEditBodyAction.toAction(): Action = when (this) {
    ExerciseEditBodyAction.CancelClick -> Action.Click.OnCancelClick
    ExerciseEditBodyAction.SaveClick -> Action.Click.OnSaveClick
    ExerciseEditBodyAction.EditImageClick -> Action.Click.OnEditImageClick
    ExerciseEditBodyAction.RemoveImageClick -> Action.Click.OnRemoveImageClick
    ExerciseEditBodyAction.ImageThumbnailClick -> Action.Click.OnImageThumbnailClick
    ExerciseEditBodyAction.EditPlanClick -> Action.Click.OnEditPlanClick
    is ExerciseEditBodyAction.NameInput -> Action.Input.OnNameChange(value)
    is ExerciseEditBodyAction.DescriptionInput -> Action.Input.OnDescriptionChange(value)
    is ExerciseEditBodyAction.TagSearchInput -> Action.Input.OnTagSearchChange(value)
    is ExerciseEditBodyAction.TypeSelect -> Action.Click.OnTypeSelect(type)
    is ExerciseEditBodyAction.TagToggle -> Action.Click.OnTagToggle(tagUuid)
    is ExerciseEditBodyAction.TagRemove -> Action.Click.OnTagRemove(tagUuid)
    is ExerciseEditBodyAction.TagCreate -> Action.Click.OnTagCreate(name)
}
