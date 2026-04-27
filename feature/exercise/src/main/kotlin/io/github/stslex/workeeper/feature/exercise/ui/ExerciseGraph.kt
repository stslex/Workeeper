// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.di.ExerciseFeature
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.DiscardTarget
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State.Mode

@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("UnusedParameter", "LongMethod")
fun NavGraphBuilder.exerciseGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(ExerciseFeature) { processor ->
        val haptic = LocalHapticFeedback.current
        val archiveSuccessFormat =
            stringResource(R.string.feature_exercise_detail_archive_success_format)
        val undoLabel = stringResource(R.string.feature_exercise_detail_archive_undo)
        val trackPendingMessage =
            stringResource(R.string.feature_exercise_detail_track_now_pending)
        val tagLimitMessage = stringResource(R.string.feature_exercise_edit_tag_limit)
        val discardTitle = stringResource(R.string.feature_exercise_edit_discard_title)
        val discardBody = stringResource(R.string.feature_exercise_edit_discard_body)
        val discardConfirm = stringResource(R.string.feature_exercise_edit_discard_confirm)
        val discardDismiss = stringResource(R.string.feature_exercise_edit_discard_dismiss)
        val archiveBlockedTitle =
            stringResource(R.string.feature_exercise_detail_archive_blocked_title)
        val archiveBlockedBodyFormat =
            stringResource(R.string.feature_exercise_detail_archive_blocked_body_format)
        val archiveBlockedOk = stringResource(R.string.feature_exercise_detail_archive_blocked_ok)
        val permanentDeleteTitleFormat =
            stringResource(R.string.feature_exercise_detail_permanent_delete_confirm_title)
        val permanentDeleteBody =
            stringResource(R.string.feature_exercise_detail_permanent_delete_confirm_body)
        val permanentDeleteImpact =
            stringResource(R.string.feature_exercise_detail_permanent_delete_confirm_impact)
        val permanentDeleteConfirm =
            stringResource(R.string.feature_exercise_detail_permanent_delete_confirm_button)
        val permanentDeleteSuccess =
            stringResource(R.string.feature_exercise_detail_permanent_delete_success)

        var pendingDiscard by remember { mutableStateOf<DiscardTarget?>(null) }
        var archiveBlockedState by remember {
            mutableStateOf<Pair<String, List<String>>?>(null)
        }
        var permanentDeleteName by remember { mutableStateOf<String?>(null) }

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
                    archiveBlockedState = event.exerciseName to event.trainings
                }

                Event.ShowTagLimitReached -> SnackbarManager.showSnackbar(message = tagLimitMessage)
                Event.ShowTrackNowPending -> SnackbarManager.showSnackbar(message = trackPendingMessage)
                is Event.ShowDiscardConfirmDialog -> { pendingDiscard = event.target }
                is Event.ShowPermanentDeleteConfirm -> { permanentDeleteName = event.name }
                Event.ShowPermanentDeleteSuccess ->
                    SnackbarManager.showSnackbar(message = permanentDeleteSuccess)
            }
        }

        // Only intercept the system back gesture when there are unsaved edits — otherwise
        // BackHandler would shadow the Android 13+ predictive-back preview animation. The
        // TopAppBar back arrow and Cancel button still emit OnBackClick directly so explicit
        // taps always flow through the store regardless of interceptBack.
        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnBackClick)
        }

        val state = processor.state.value
        when (state.mode) {
            Mode.Read -> ExerciseDetailScreen(
                modifier = modifier,
                state = state,
                consume = processor::consume,
            )

            is Mode.Edit -> ExerciseEditScreen(
                modifier = modifier,
                state = state,
                consume = processor::consume,
            )
        }

        pendingDiscard?.let { target ->
            AppDialog(
                title = discardTitle,
                body = discardBody,
                confirmLabel = discardConfirm,
                dismissLabel = discardDismiss,
                destructive = true,
                onConfirm = {
                    pendingDiscard = null
                    processor.consume(Action.Click.OnConfirmDiscard(target))
                },
                onDismiss = {
                    pendingDiscard = null
                    processor.consume(Action.Click.OnDismissDiscard)
                },
            )
        }
        archiveBlockedState?.let { (name, trainings) ->
            AppDialog(
                title = archiveBlockedTitle,
                body = archiveBlockedBodyFormat.format(name, trainings.joinToString(", ")),
                confirmLabel = archiveBlockedOk,
                onConfirm = {
                    archiveBlockedState = null
                    processor.consume(Action.Click.OnDismissArchiveBlocked)
                },
            )
        }
        permanentDeleteName?.let { name ->
            AppConfirmDialog(
                title = permanentDeleteTitleFormat.format(name),
                body = permanentDeleteBody,
                impactSummary = permanentDeleteImpact,
                confirmLabel = permanentDeleteConfirm,
                onConfirm = {
                    permanentDeleteName = null
                    processor.consume(Action.Click.OnConfirmPermanentDelete)
                },
                onDismiss = {
                    permanentDeleteName = null
                    processor.consume(Action.Click.OnDismissPermanentDelete)
                },
            )
        }
    }
}
