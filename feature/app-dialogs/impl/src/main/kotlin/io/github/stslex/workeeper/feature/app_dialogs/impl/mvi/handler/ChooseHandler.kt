// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStore
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import javax.inject.Inject

/**
 * Receives `Action.Choose(dialog, action)` from the Host when the user taps
 * any button on the currently-rendered dialog.
 *
 * **Placeholder body.** The contract that determines who clears the
 * persisted dialog flag (the Store here? a consumer-side `@Singleton` after
 * its side-effect succeeds?), whether the user choice is persisted in
 * DataStore or carried as a transient signal, and how `performUndoRestore`
 * achieves idempotency under crash-mid-reaction is open as of this commit —
 * see the Phase B blocker discussion. The handler is wired into the Store's
 * handler graph so the action surface compiles, and the routing logs the
 * choice for traceability; the reaction body lands once the contract is
 * locked.
 */
@ViewModelScoped
internal class ChooseHandler @Inject constructor(
    store: AppDialogHandlerStore,
) : Handler<Action.Choose>, AppDialogHandlerStore by store {

    override fun invoke(action: Action.Choose) {
        logger.i { "Choose received: ${action.dialog.id} → ${action.action} (no-op pending contract)" }
    }
}
