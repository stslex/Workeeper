// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesFeature
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event

private const val MAX_BLOCKED_TRAINING_NAMES = 2

@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("UnusedParameter", "LongMethod")
fun NavGraphBuilder.allExercisesGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(AllExercisesFeature) { processor ->
        val haptic = LocalHapticFeedback.current
        // Compose-ui in this project predates LocalResources; LocalContext.resources is
        // the documented v1 fallback. Plurals reload via Configuration changes through
        // recomposition, so the lint warning is benign here.
        @Suppress("LocalContextResourcesRead")
        val resources = LocalContext.current.resources
        val archiveSuccessFormat =
            stringResource(R.string.feature_all_exercises_archive_success_format)
        val undoLabel = stringResource(R.string.feature_all_exercises_archive_undo)
        val archiveBlockedFormat =
            stringResource(R.string.feature_all_exercises_archive_blocked_format)
        val moreFormat = stringResource(R.string.feature_all_exercises_overflow_format)
        val permanentDeleteSuccess =
            stringResource(R.string.feature_all_exercises_permanent_delete_success)
        val bulkArchivePartialFormat =
            stringResource(R.string.feature_all_exercises_bulk_archive_partial_format)

        processor.Handle { event ->
            when (event) {
                is Event.Haptic -> haptic.performHapticFeedback(event.type)
                is Event.ShowArchiveSuccess -> SnackbarManager.showSnackbar(
                    AppSnackbarModel(
                        message = archiveSuccessFormat.format(event.name),
                        actionLabel = undoLabel,
                        withDismissAction = true,
                        action = { processor.consume(Action.Click.OnUndoArchive(event.uuid)) },
                    ),
                )

                is Event.ShowArchiveBlocked -> {
                    // TODO(tech-debt): Move blocked-name list shaping into handler/state and
                    // emit a ready-to-render localized message payload for UI.
                    val visible = event.trainings.take(MAX_BLOCKED_TRAINING_NAMES)
                    val joined = buildString {
                        append(visible.joinToString(", "))
                        val overflow = event.trainings.size - visible.size
                        if (overflow > 0) {
                            append(", ")
                            append(moreFormat.format(overflow))
                        }
                    }
                    SnackbarManager.showSnackbar(
                        message = archiveBlockedFormat.format(joined),
                    )
                }

                is Event.ShowPermanentDeleteSuccess ->
                    SnackbarManager.showSnackbar(message = permanentDeleteSuccess)

                is Event.ShowBulkArchiveSuccess ->
                    SnackbarManager.showSnackbar(
                        message = resources.getQuantityString(
                            R.plurals.feature_all_exercises_bulk_archive_success,
                            event.count,
                            event.count,
                        ),
                    )

                is Event.ShowBulkArchiveBlocked -> {
                    // TODO(tech-debt): Keep this text shaping out of UI; handler/state should
                    // provide a final localized message payload.
                    val visible = event.blockedNames.take(MAX_BLOCKED_TRAINING_NAMES)
                    val joined = buildString {
                        append(visible.joinToString(", "))
                        val overflow = event.blockedNames.size - visible.size
                        if (overflow > 0) {
                            append(", ")
                            append(moreFormat.format(overflow))
                        }
                    }
                    SnackbarManager.showSnackbar(
                        message = bulkArchivePartialFormat.format(event.archivedCount, joined),
                    )
                }

                is Event.ShowBulkDeleteSuccess ->
                    SnackbarManager.showSnackbar(
                        message = resources.getQuantityString(
                            R.plurals.feature_all_exercises_bulk_delete_success,
                            event.count,
                            event.count,
                        ),
                    )
            }
        }

        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnSelectionExit)
        }

        AllExercisesScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
