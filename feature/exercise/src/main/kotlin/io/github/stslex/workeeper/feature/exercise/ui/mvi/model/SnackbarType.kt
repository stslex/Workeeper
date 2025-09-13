package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.material3.SnackbarHostState

enum class SnackbarType(
    val value: String
) {

    DELETE("delete"),
    DISMISS("dissmiss");

    companion object {

        fun SnackbarHostState.getAction(): SnackbarType? =
            currentSnackbarData?.visuals?.actionLabel?.let {
                SnackbarType.getAction(it)
            }

        fun getAction(
            value: String
        ): SnackbarType? = SnackbarType.entries.firstOrNull { it.value == value }
    }
}