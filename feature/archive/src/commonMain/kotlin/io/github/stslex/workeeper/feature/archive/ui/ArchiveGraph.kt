// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.feature.archive.di.ArchiveFeature
import io.github.stslex.workeeper.feature.archive.di.ArchiveGraph
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Event
import io.github.stslex.workeeper.feature.archive.resources.Res
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_snackbar_deleted_format
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_snackbar_restored_format
import io.github.stslex.workeeper.feature.archive.resources.feature_archive_snackbar_undo
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

fun NavGraphScope.archiveGraph(
    factory: ArchiveGraph.Factory,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(ArchiveFeature(factory)) { processor ->
        val haptic = LocalHapticFeedback.current
        val undoLabel = stringResource(Res.string.feature_archive_snackbar_undo)

        processor.Handle { event ->
            when (event) {
                is Event.Haptic -> haptic.performHapticFeedback(event.type)
                is Event.ShowRestoredSnackbar -> {
                    SnackbarManager.showSnackbar(
                        AppSnackbarModel(
                            // TODO(tech-debt): UI mapping boundary — see documentation/tech-debt.md
                            message = getString(
                                Res.string.feature_archive_snackbar_restored_format,
                                event.item.name,
                            ),
                            actionLabel = undoLabel,
                            action = { processor.consume(Action.Click.OnUndoRestore(event.item)) },
                        ),
                    )
                }

                is Event.ShowPermanentlyDeletedSnackbar -> {
                    SnackbarManager.showSnackbar(
                        message = getString(
                            Res.string.feature_archive_snackbar_deleted_format,
                            event.name,
                        ),
                    )
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
