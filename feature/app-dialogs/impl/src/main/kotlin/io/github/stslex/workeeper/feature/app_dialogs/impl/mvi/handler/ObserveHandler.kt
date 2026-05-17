// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStore
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import javax.inject.Inject

/**
 * Wires `AppDialogRepository.currentDialog` into `State.current`. Dispatched
 * once at Store init via `initialActions = listOf(Action.Observe)`. The
 * subscription lives for the lifetime of the Activity-scoped Store; the
 * scope is cancelled in `BaseStore.dispose` when the Activity goes away.
 */
@ViewModelScoped
internal class ObserveHandler @Inject constructor(
    private val repository: AppDialogRepository,
    store: AppDialogHandlerStore,
) : Handler<Action.Observe>, AppDialogHandlerStore by store {

    override fun invoke(action: Action.Observe) {
        repository.currentDialog.launch { dialog ->
            updateState { it.copy(current = dialog) }
        }
    }
}
