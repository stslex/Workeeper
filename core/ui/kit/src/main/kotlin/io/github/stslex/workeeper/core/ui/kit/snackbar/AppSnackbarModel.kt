package io.github.stslex.workeeper.core.ui.kit.snackbar

import androidx.compose.runtime.Stable

@Stable
data class AppSnackbarModel(
    val message: String,
    val actionLabel: String? = null,
    val withDismissAction: Boolean = false,
    val action: () -> Unit = { },
)
