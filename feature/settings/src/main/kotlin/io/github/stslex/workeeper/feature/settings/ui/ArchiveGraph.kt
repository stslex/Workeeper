// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.di.ArchiveFeature
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Event

fun NavGraphBuilder.archiveGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreen(ArchiveFeature) { processor ->
        val haptic = LocalHapticFeedback.current
        val restoredTemplate = stringResource(R.string.feature_archive_snackbar_restored_format)
        val deletedTemplate = stringResource(R.string.feature_archive_snackbar_deleted_format)
        val undoLabel = stringResource(R.string.feature_archive_snackbar_undo)

        processor.Handle { event ->
            when (event) {
                is Event.Haptic -> haptic.performHapticFeedback(event.type)
                is Event.ShowRestoredSnackbar -> {
                    SnackbarManager.showSnackbar(
                        AppSnackbarModel(
                            message = restoredTemplate.format(event.item.name),
                            actionLabel = undoLabel,
                            withDismissAction = true,
                            action = { processor.consume(Action.Click.OnUndoRestore(event.item)) },
                        ),
                    )
                }

                is Event.ShowPermanentlyDeletedSnackbar -> {
                    SnackbarManager.showSnackbar(message = deletedTemplate.format(event.name))
                }
            }
        }

        ArchiveScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
