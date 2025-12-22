package io.github.stslex.workeeper.core.ui.kit.snackbar

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SnackbarManager {

    private val _snackbar: MutableSharedFlow<AppSnackbarModel> = MutableSharedFlow()
    val snackbar: SharedFlow<AppSnackbarModel> = _snackbar.asSharedFlow()

    fun showSnackbar(model: AppSnackbarModel) {
        _snackbar.tryEmit(model)
    }

    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        action: () -> Unit = {},
    ): Unit = showSnackbar(
        AppSnackbarModel(
            message = message,
            actionLabel = actionLabel,
            withDismissAction = withDismissAction,
            action = action,
        ),
    )
}
