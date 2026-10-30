// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogHandlerStore
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogsScope
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.mapper.toDateLabel
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import kotlinx.coroutines.flow.map

@SingleIn(AppDialogsScope::class)
internal class AppDialogRepoHandler @Inject constructor(
    private val repository: AppDialogRepository,
    private val resourceWrapper: ResourceWrapper,
    store: AppDialogHandlerStore,
) : Handler<Action.RepoAction>, AppDialogHandlerStore by store {

    override fun invoke(action: Action.RepoAction) = when (action) {
        Action.RepoAction.Observe -> observeCurrentDialog()
        is Action.RepoAction.Publish -> publishDialog(action)
        is Action.RepoAction.Dismiss -> dismissDialog(action)
    }

    private fun observeCurrentDialog() {
        // D3: the date is formatted here, before the write; the State lambda only copies.
        repository.currentDialog
            .map { dialog -> dialog to dialog.toDateLabel(resourceWrapper) }
            .launch { (dialog, dateLabel) ->
                updateState { it.copy(current = dialog, dateLabel = dateLabel) }
            }
    }

    private fun publishDialog(action: Action.RepoAction.Publish) {
        launchDefault {
            repository.publish(action.dialog)
        }
    }

    private fun dismissDialog(action: Action.RepoAction.Dismiss) {
        launchDefault {
            repository.dismiss(action.dialog)
        }
    }
}
