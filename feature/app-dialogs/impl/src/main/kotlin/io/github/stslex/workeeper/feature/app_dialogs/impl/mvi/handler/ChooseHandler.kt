// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStore
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogsScope
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl

/**
 * Emits `Action.Choose` as an `AppDialogUserChoice` through [AppDialogObserverImpl]. Never clears
 * the dialog flag: the consumer acknowledges after its side-effect. See the app-dialogs spec.
 */
@SingleIn(AppDialogsScope::class)
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
