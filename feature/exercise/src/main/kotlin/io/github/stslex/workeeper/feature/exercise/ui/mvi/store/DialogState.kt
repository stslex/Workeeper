package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetsUiModel

sealed interface DialogState {

    data object Closed : DialogState

    data object Calendar : DialogState

    data class Sets(
        val set: SetsUiModel
    ) : DialogState
}