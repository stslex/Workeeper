package io.github.stslex.workeeper.feature.single_training.ui.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.feature.single_training.R

@Stable
internal sealed interface DialogState {

    data object Closed : DialogState

    data object Calendar : DialogState

    @Stable
    sealed class ConfirmDialog(
        @StringRes val titleRes: Int,
    ) : DialogState {

        data object Delete : ConfirmDialog(
            titleRes = R.string.feature_single_training_dialog_delete,
        )

        data object ExitWithoutSaving : ConfirmDialog(
            titleRes = R.string.feature_single_training_dialog_exit,
        )
    }
}
