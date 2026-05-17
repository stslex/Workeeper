// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStore
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import javax.inject.Inject

/**
 * Persists `Action.Publish(dialog)` through the repository's atomic
 * `dataStore.edit { }` block. Per-variant dedup is enforced in
 * `AppDialogRepository.publish`; this handler is a thin pass-through so
 * Store-level instrumentation (analytics, logging) wraps the write.
 *
 * The dispatched repository call runs on the default dispatcher; the Store
 * scope absorbs cancellation when the Activity goes away.
 */
@ViewModelScoped
internal class PublishHandler @Inject constructor(
    private val repository: AppDialogRepository,
    store: AppDialogHandlerStore,
) : Handler<Action.Publish>, AppDialogHandlerStore by store {

    override fun invoke(action: Action.Publish) {
        launchDefault(
            onError = { e -> logger.e(e, "Failed to publish ${action.dialog.id}") },
            onSuccess = { },
        ) {
            repository.publish(action.dialog)
        }
    }
}
