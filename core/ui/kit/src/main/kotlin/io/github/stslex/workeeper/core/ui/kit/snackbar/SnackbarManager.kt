package io.github.stslex.workeeper.core.ui.kit.snackbar

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SnackbarManager {

    /**
     * Buffered so [showSnackbar] never silently drops feedback. The single collector
     * (`App.kt`) suspends inside `SnackbarHostState.showSnackbar` for the whole time a
     * snackbar is visible; a zero-buffer `MutableSharedFlow` would make [tryEmit] return
     * `false` and discard any event emitted during that window. The buffer holds pending
     * messages until the collector is free again; [BufferOverflow.DROP_OLDEST] keeps the
     * newest feedback if a burst ever exceeds [BUFFER_CAPACITY].
     */
    private val _snackbar: MutableSharedFlow<AppSnackbarModel> = MutableSharedFlow(
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
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

    private const val BUFFER_CAPACITY = 16
}
