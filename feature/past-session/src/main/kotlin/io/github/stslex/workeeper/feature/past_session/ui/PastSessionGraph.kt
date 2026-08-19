// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadedContent
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.di.PastSessionFeature
import io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event

fun NavGraphScope.pastSessionGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreen(PastSessionFeature) { processor ->
        val haptic = LocalHapticFeedback.current
        val errorNotFound = stringResource(R.string.feature_past_session_error_not_found)
        val errorLoadFailed = stringResource(R.string.feature_past_session_error_load_failed)
        val saveFailed = stringResource(R.string.feature_past_session_save_failed_snackbar)
        val deletedMessage = stringResource(R.string.feature_past_session_deleted_snackbar)

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.ShowError -> SnackbarManager.showSnackbar(
                    // TODO(tech-debt): UI mapping boundary — see documentation/tech-debt.md
                    message = when (event.errorType) {
                        ErrorType.SessionNotFound -> errorNotFound
                        ErrorType.LoadFailed -> errorLoadFailed
                        ErrorType.SaveFailed -> saveFailed
                    },
                )

                Event.DeletedSnackbar -> SnackbarManager.showSnackbar(message = deletedMessage)
                Event.SaveFailedSnackbar -> SnackbarManager.showSnackbar(message = saveFailed)
            }
        }

        // The route gate (§26), and this screen wanted it most of the four. Composed while
        // loading it drew TWO things it did not know yet: a centred spinner, and a top bar
        // carrying the fallback title — so a fast load flashed a circle and then rewrote the
        // heading under the user's eye. A spinner shown for 150ms is not information, it is a
        // flicker; the loading phase now draws nothing and the content arrives with a fade.
        //
        // Gated on Loading alone: the Error phase MUST still compose, or a failed load is the
        // permanently empty frame this gate is otherwise careful to avoid.
        AppLoadedContent(
            isLoaded = processor.state.value.phase !is PastSessionStore.State.Phase.Loading,
        ) {
            PastSessionScreen(
                modifier = modifier,
                state = processor.state.value,
                consume = processor::consume,
            )
        }
    }
}
