// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStore
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import javax.inject.Inject

/**
 * Clears the variant-specific flag set for `Action.Dismiss(dialog)` via the
 * repository. Triggered by an implicit dismiss (e.g. back-press on a
 * variant whose dismiss policy allows it). User-tap dismiss flows through
 * `UserActionHandler` instead, since the dismiss-timing is bound to the
 * consumer's reaction (see that handler's KDoc for the contract).
 */
@ViewModelScoped
internal class DismissHandler @Inject constructor(
    private val repository: AppDialogRepository,
    store: AppDialogHandlerStore,
) : Handler<Action.Dismiss>, AppDialogHandlerStore by store {

    override fun invoke(action: Action.Dismiss) {
        launchDefault(
            onError = { e -> logger.e(e, "Failed to dismiss ${action.dialog.id}") },
            onSuccess = { },
        ) {
            repository.dismiss(action.dialog)
        }
    }
}
