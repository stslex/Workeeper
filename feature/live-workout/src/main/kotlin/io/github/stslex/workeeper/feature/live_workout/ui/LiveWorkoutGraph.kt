// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.plan_editor.AppPlanEditor
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutFeature
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.ui.components.FinishConfirmDialog

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun NavGraphBuilder.liveWorkoutGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreen(LiveWorkoutFeature) { processor ->
        val haptic = LocalHapticFeedback.current
        val resources = LocalResources.current

        // TODO: Move these strings into the store and emit them as part of the state, so that the UI is dumb and just displays what it's given, and so that the processor can decide not just when to show a dialog, but also what content it should have
        val finishSavedMessage = stringResource(R.string.feature_live_workout_finish_success)
        val resetTitle = stringResource(R.string.feature_live_workout_reset_title)
        val resetBody = stringResource(R.string.feature_live_workout_reset_body)
        val resetConfirm = stringResource(R.string.feature_live_workout_reset_confirm)
        val resetDismiss = stringResource(R.string.feature_live_workout_reset_dismiss)
        val skipTitle = stringResource(R.string.feature_live_workout_skip_title)
        val skipBody = stringResource(R.string.feature_live_workout_skip_body)
        val skipConfirm = stringResource(R.string.feature_live_workout_skip_confirm)
        val skipDismiss = stringResource(R.string.feature_live_workout_skip_dismiss)
        val cancelTitle = stringResource(R.string.feature_live_workout_cancel_title)
        val cancelBody = stringResource(R.string.feature_live_workout_cancel_body)
        val cancelConfirm = stringResource(R.string.feature_live_workout_cancel_confirm)
        val cancelDismiss = stringResource(R.string.feature_live_workout_cancel_dismiss)


        // TODO: Refactor to use a single dialog state with a sealed class for type, instead of multiple booleans
        var showResetDialog by remember { mutableStateOf(false) }
        var showSkipDialog by remember { mutableStateOf(false) }
        var showCancelDialog by remember { mutableStateOf(false) }
        var showFinishDialog by remember { mutableStateOf(false) }


        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.HapticImpact -> haptic.performHapticFeedback(event.type)
                is Event.ShowSessionSavedSnackbar -> SnackbarManager.showSnackbar(message = finishSavedMessage)
                is Event.ShowError -> SnackbarManager.showSnackbar(
                    message = resources.getString(event.type.msgRes),
                )

                Event.ShowFinishConfirmDialog -> {
                    showFinishDialog = true
                }

                Event.ShowResetSetsConfirmDialog -> {
                    showResetDialog = true
                }

                Event.ShowSkipExerciseConfirmDialog -> {
                    showSkipDialog = true
                }

                Event.ShowCancelSessionConfirmDialog -> {
                    showCancelDialog = true
                }
            }
        }

        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnBackClick)
        }

        val state = processor.state.value
        LiveWorkoutScreen(
            modifier = modifier,
            state = state,
            consume = processor::consume,
        )

        state.planEditorTarget?.let { target ->
            AppPlanEditor(
                exerciseName = target.exerciseName,
                draft = target.draft,
                isWeighted = target.isWeighted,
                onAction = { action -> processor.consume(Action.PlanEditAction(action)) },
            )
        }

        if (showFinishDialog) {
            state.pendingFinishConfirm?.let { stats ->
                FinishConfirmDialog(
                    stats = stats,
                    onConfirm = {
                        showFinishDialog = false
                        processor.consume(Action.Click.OnFinishConfirm)
                    },
                    onDismiss = {
                        showFinishDialog = false
                        processor.consume(Action.Click.OnFinishDismiss)
                    },
                )
            } ?: run {
                showFinishDialog = false
            }
        }

        if (showResetDialog) {
            val target = state.pendingResetExerciseUuid
            if (target != null) {
                AppDialog(
                    title = resetTitle,
                    body = resetBody,
                    confirmLabel = resetConfirm,
                    dismissLabel = resetDismiss,
                    destructive = true,
                    onConfirm = {
                        showResetDialog = false
                        processor.consume(Action.Click.OnResetSetsConfirm(target))
                    },
                    onDismiss = {
                        showResetDialog = false
                        processor.consume(Action.Click.OnResetSetsDismiss)
                    },
                )
            } else {
                showResetDialog = false
            }
        }

        if (showSkipDialog) {
            val target = state.pendingSkipExerciseUuid
            if (target != null) {
                AppDialog(
                    title = skipTitle,
                    body = skipBody,
                    confirmLabel = skipConfirm,
                    dismissLabel = skipDismiss,
                    destructive = true,
                    onConfirm = {
                        showSkipDialog = false
                        processor.consume(Action.Click.OnSkipExerciseConfirm(target))
                    },
                    onDismiss = {
                        showSkipDialog = false
                        processor.consume(Action.Click.OnSkipExerciseDismiss)
                    },
                )
            } else {
                showSkipDialog = false
            }
        }

        if (showCancelDialog) {
            AppDialog(
                title = cancelTitle,
                body = cancelBody,
                confirmLabel = cancelConfirm,
                dismissLabel = cancelDismiss,
                destructive = true,
                onConfirm = {
                    showCancelDialog = false
                    processor.consume(Action.Click.OnCancelSessionConfirm)
                },
                onDismiss = {
                    showCancelDialog = false
                    processor.consume(Action.Click.OnCancelSessionDismiss)
                },
            )
        }
    }
}
