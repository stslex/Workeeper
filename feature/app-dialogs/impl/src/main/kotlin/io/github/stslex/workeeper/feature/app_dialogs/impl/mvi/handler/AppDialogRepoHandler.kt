// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStore
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogsScope
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action

@SingleIn(AppDialogsScope::class)
internal class AppDialogRepoHandler @Inject constructor(
    private val repository: AppDialogRepository,
    store: AppDialogHandlerStore,
) : Handler<Action.RepoAction>, AppDialogHandlerStore by store {

    override fun invoke(action: Action.RepoAction) = when (action) {
        Action.RepoAction.Observe -> observeCurrentDialog()
        is Action.RepoAction.Publish -> publishDialog(action)
        is Action.RepoAction.Dismiss -> dismissDialog(action)
    }

    private fun observeCurrentDialog() {
        repository.currentDialog.launch { dialog ->
            updateState { it.copy(current = dialog) }
        }
    }

    private fun publishDialog(action: Action.RepoAction.Publish) {
        launchDefault(
            onError = { e -> logger.e(e, "Failed to publish ${action.dialog.id}") },
            onSuccess = { },
        ) {
            repository.publish(action.dialog)
        }
    }

    private fun dismissDialog(action: Action.RepoAction.Dismiss) {
        launchDefault(
            onError = { e -> logger.e(e, "Failed to dismiss ${action.dialog.id}") },
            onSuccess = { },
        ) {
            repository.dismiss(action.dialog)
        }
    }
}
