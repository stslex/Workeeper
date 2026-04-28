// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.di.PastSessionFeature
import io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event

fun NavGraphBuilder.pastSessionGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreen(PastSessionFeature) { processor ->
        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.ShowError ->
                    SnackbarManager.showSnackbar(message = context.getString(event.errorType.toMessageRes()))

                Event.DeletedSnackbar ->
                    SnackbarManager.showSnackbar(
                        message = context.getString(R.string.feature_past_session_deleted_snackbar),
                    )

                Event.SaveFailedSnackbar ->
                    SnackbarManager.showSnackbar(
                        message = context.getString(R.string.feature_past_session_save_failed_snackbar),
                    )
            }
        }

        PastSessionScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}

private fun ErrorType.toMessageRes(): Int = when (this) {
    ErrorType.SessionNotFound -> R.string.feature_past_session_error_not_found
    ErrorType.LoadFailed -> R.string.feature_past_session_error_load_failed
    ErrorType.SaveFailed -> R.string.feature_past_session_save_failed_snackbar
}
