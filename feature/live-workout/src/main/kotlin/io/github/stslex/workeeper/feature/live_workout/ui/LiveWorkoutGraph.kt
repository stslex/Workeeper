// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.plan_editor.AppPlanEditor
import io.github.stslex.workeeper.core.ui.plan_editor.ExercisePickerBottomSheet
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutFeature
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State.EmptyFinishDialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State.ExercisePickerSheetState
import io.github.stslex.workeeper.feature.live_workout.ui.components.FinishConfirmDialog

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun NavGraphBuilder.liveWorkoutGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(LiveWorkoutFeature) { processor ->
        val haptic = LocalHapticFeedback.current
        var resetDialog by remember { mutableStateOf<LiveWorkoutStore.ConfirmDialog?>(null) }
        var skipDialog by remember { mutableStateOf<LiveWorkoutStore.ConfirmDialog?>(null) }
        var cancelDialog by remember { mutableStateOf<LiveWorkoutStore.ConfirmDialog?>(null) }
        var showFinishDialog by remember { mutableStateOf(false) }

        processor.Handle { event ->
            Log.tag("MVI_STORE_LiveWorkout").i { "Received event: $event" }
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.HapticImpact -> haptic.performHapticFeedback(event.type)
                is Event.ShowSessionSavedSnackbar -> SnackbarManager.showSnackbar(message = event.message)
                is Event.ShowError -> SnackbarManager.showSnackbar(message = event.message)

                Event.ShowFinishConfirmDialog -> {
                    showFinishDialog = true
                }

                is Event.ShowResetSetsConfirmDialog -> {
                    resetDialog = event.dialog
                }

                is Event.ShowSkipExerciseConfirmDialog -> {
                    skipDialog = event.dialog
                }

                is Event.ShowCancelSessionConfirmDialog -> {
                    cancelDialog = event.dialog
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
            activeSessionBannerModifier = with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = sharedTransitionScope.rememberSharedContentState("activeSessionBanner"),
                    animatedVisibilityScope = this@navComponentScreen,
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                        ContentScale.FillBounds,
                        Alignment.Center,
                    ),
                )
            },
        )

        state.planEditorTarget?.let { target ->
            AppPlanEditor(
                exerciseName = target.exerciseName,
                draft = target.draft,
                isWeighted = target.isWeighted,
                onAction = { action -> processor.consume(Action.PlanEditAction(action)) },
            )
        }

        (state.exercisePickerSheet as? ExercisePickerSheetState.Visible)?.let { sheet ->
            ExercisePickerBottomSheet(
                query = sheet.query,
                results = sheet.results,
                noMatchHeadline = sheet.noMatchHeadline,
                createCtaLabel = sheet.createCtaLabel,
                searchHint = stringResource(R.string.feature_live_workout_picker_search_hint),
                isPrimaryActionEnabled = state.canAddExercise,
                onAction = { action -> processor.consume(Action.Click.PickerAction(action)) },
            )
        }

        (state.emptyFinishDialog as? EmptyFinishDialogState.Visible)?.let { dialog ->
            AppDialog(
                title = stringResource(R.string.feature_live_workout_empty_finish_title),
                body = stringResource(R.string.feature_live_workout_empty_finish_body),
                confirmLabel = stringResource(
                    if (dialog.canDiscard) {
                        R.string.feature_live_workout_empty_finish_discard
                    } else {
                        R.string.feature_live_workout_empty_finish_continue
                    },
                ),
                dismissLabel = stringResource(R.string.feature_live_workout_empty_finish_continue),
                destructive = dialog.canDiscard,
                onConfirm = {
                    if (dialog.canDiscard) {
                        processor.consume(Action.Click.OnEmptyFinishDiscard)
                    } else {
                        processor.consume(Action.Click.OnEmptyFinishContinue)
                    }
                },
                onDismiss = { processor.consume(Action.Click.OnEmptyFinishContinue) },
            )
        }

        if (showFinishDialog) {
            state.pendingFinishConfirm?.let { stats ->
                FinishConfirmDialog(
                    stats = stats,
                    onNameChange = { processor.consume(Action.Click.OnFinishNameChange(it)) },
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

        resetDialog?.let { dialog ->
            val target = state.pendingResetExerciseUuid
            if (target != null) {
                AppDialog(
                    title = dialog.title,
                    body = dialog.body,
                    confirmLabel = dialog.confirmLabel,
                    dismissLabel = dialog.dismissLabel,
                    destructive = true,
                    onConfirm = {
                        resetDialog = null
                        processor.consume(Action.Click.OnResetSetsConfirm(target))
                    },
                    onDismiss = {
                        resetDialog = null
                        processor.consume(Action.Click.OnResetSetsDismiss)
                    },
                )
            } else {
                resetDialog = null
            }
        }

        skipDialog?.let { dialog ->
            val target = state.pendingSkipExerciseUuid
            if (target != null) {
                AppDialog(
                    title = dialog.title,
                    body = dialog.body,
                    confirmLabel = dialog.confirmLabel,
                    dismissLabel = dialog.dismissLabel,
                    destructive = true,
                    onConfirm = {
                        skipDialog = null
                        processor.consume(Action.Click.OnSkipExerciseConfirm(target))
                    },
                    onDismiss = {
                        skipDialog = null
                        processor.consume(Action.Click.OnSkipExerciseDismiss)
                    },
                )
            } else {
                skipDialog = null
            }
        }

        cancelDialog?.let { dialog ->
            AppDialog(
                title = dialog.title,
                body = dialog.body,
                confirmLabel = dialog.confirmLabel,
                dismissLabel = dialog.dismissLabel,
                destructive = true,
                onConfirm = {
                    cancelDialog = null
                    processor.consume(Action.Click.OnCancelSessionConfirm)
                },
                onDismiss = {
                    cancelDialog = null
                    processor.consume(Action.Click.OnCancelSessionDismiss)
                },
            )
        }
    }
}
