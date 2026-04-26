// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.Modifier
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
@Suppress("UnusedParameter")
fun NavGraphBuilder.allExercisesGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(AllExercisesFeature) { processor ->
        val haptic = LocalHapticFeedback.current
        val archiveSuccessFormat =
            stringResource(R.string.feature_all_exercises_archive_success_format)
        val undoLabel = stringResource(R.string.feature_all_exercises_archive_undo)
        val archiveBlockedFormat =
            stringResource(R.string.feature_all_exercises_archive_blocked_format)
        val moreFormat = stringResource(R.string.feature_all_exercises_overflow_format)

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
            }
        }

        AllExercisesScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
