// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.feature.archive.R
import io.github.stslex.workeeper.feature.archive.di.ArchiveFeature
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Event

fun NavGraphScope.archiveGraph(
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
                            // TODO(tech-debt): UI mapping boundary — see documentation/tech-debt.md
                            message = restoredTemplate.format(event.item.name),
                            actionLabel = undoLabel,
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
