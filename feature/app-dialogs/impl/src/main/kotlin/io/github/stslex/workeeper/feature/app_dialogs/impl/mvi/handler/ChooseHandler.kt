// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStore
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import javax.inject.Inject

/**
 * Receives `Action.Choose(dialog, action)` from the Host and emits the
 * `AppDialogUserChoice` through [AppDialogObserverImpl].
 *
 * **Does NOT clear the dialog flag.** The dismiss-after contract puts that
 * responsibility on the consumer-side handler (`@Singleton` reactor in
 * `app/app`), which calls `AppDialogObserver.acknowledgeReaction(dialog)`
 * after its side-effect runs. If the process dies mid-reaction, the dialog
 * flag survives in DataStore, the dialog re-shows on next launch, and the
 * user re-taps — idempotent by construction. See
 * `documentation/feature-specs/app-dialogs.md` → "Cross-feature observation"
 * for the rationale.
 *
 * Injects the concrete impl (not the [io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver]
 * interface) because the producer-side `emit` is intentionally internal —
 * the api surface only exposes the consumer side.
 */
@ViewModelScoped
internal class ChooseHandler @Inject constructor(
    private val observer: AppDialogObserverImpl,
    store: AppDialogHandlerStore,
) : Handler<Action.Choose>, AppDialogHandlerStore by store {

    override fun invoke(action: Action.Choose) {
        launchDefault(
            onError = { e ->
                logger.e(e, "Failed to emit choice ${action.dialog.id} → ${action.action}")
            },
            onSuccess = { },
        ) {
            observer.emit(AppDialogUserChoice(action.dialog, action.action))
        }
    }
}
