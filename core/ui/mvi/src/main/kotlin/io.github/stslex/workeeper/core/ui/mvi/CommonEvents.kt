package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.kit.components.snackbar.SnackbarType

interface CommonEvents {

    @Stable
    sealed class Snackbar(
        open val message: String,
        open val duration: SnackbarDuration,
        open val withDismissAction: Boolean,
        val action: String,
    ) : CommonEvents {

        @Stable
        data class Error(
            override val message: String,
            override val duration: SnackbarDuration = SnackbarDuration.Short,
            override val withDismissAction: Boolean = false,
        ) : Snackbar(
            message = message,
            action = SnackbarType.ERROR.label,
            duration = duration,
            withDismissAction = withDismissAction
        )

        @Stable
        data class Success(
            override val message: String,
            override val duration: SnackbarDuration = SnackbarDuration.Short,
            override val withDismissAction: Boolean = false,
        ) : Snackbar(
            message = message,
            action = SnackbarType.SUCCESS.label,
            duration = duration,
            withDismissAction = withDismissAction
        )

        @Stable
        data class Info(
            override val message: String,
            override val duration: SnackbarDuration = SnackbarDuration.Short,
            override val withDismissAction: Boolean = false,
        ) : Snackbar(
            message = message,
            action = SnackbarType.INFO.label,
            duration = duration,
            withDismissAction = withDismissAction
        )
    }
}