package io.github.stslex.workeeper.feature.single_training.ui.model

import androidx.compose.runtime.Stable

@Stable
internal sealed interface DialogState {

    data object Closed : DialogState

    data object Calendar : DialogState
}
