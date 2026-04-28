// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui

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
import io.github.stslex.workeeper.core.ui.kit.components.dialog.ActiveSessionConflictDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.plan_editor.AppPlanEditor
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingFeature
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.Mode
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.PickerState
import io.github.stslex.workeeper.feature.single_training.ui.components.ExercisePickerSheet

@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("UnusedParameter", "LongMethod", "CyclomaticComplexMethod")
fun NavGraphBuilder.singleTrainingsGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(SingleTrainingFeature) { processor ->
        val haptic = LocalHapticFeedback.current
        // TODO(tech-debt): UI mapping boundary — see documentation/tech-debt.md
        val discardTitle = stringResource(R.string.feature_training_edit_discard_title)
        val discardBody = stringResource(R.string.feature_training_edit_discard_body)
        val discardConfirm = stringResource(R.string.feature_training_edit_discard_confirm)
        val discardDismiss = stringResource(R.string.feature_training_edit_discard_dismiss)

        var showDiscardDialog by remember { mutableStateOf(false) }
        var permanentDeleteDialog by remember { mutableStateOf<Event.ShowPermanentDeleteConfirmDialog?>(null) }

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.ShowArchiveSuccess ->
                    SnackbarManager.showSnackbar(message = event.message)

                is Event.ShowArchiveBlocked -> SnackbarManager.showSnackbar(message = event.message)
                Event.ShowDiscardConfirmDialog -> {
                    showDiscardDialog = true
                }

                is Event.ShowPermanentDeleteConfirmDialog -> {
                    permanentDeleteDialog = event
                }

                is Event.ShowActiveSessionConflict -> Unit // rendered from state.pendingConflict

                is Event.ShowSaveError -> SnackbarManager.showSnackbar(message = event.message)
            }
        }

        // Intercept back when EITHER training-level edits OR plan-editor draft are dirty;
        // the handler routes the dialog to the right surface (training vs plan) from state.
        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnBackClick)
        }

        val state = processor.state.value
        when (state.mode) {
            Mode.Read -> TrainingDetailScreen(
                modifier = modifier,
                state = state,
                consume = processor::consume,
            )

            is Mode.Edit -> TrainingEditScreen(
                modifier = modifier,
                state = state,
                consume = processor::consume,
            )
        }

        state.planEditorTarget?.let { target ->
            AppPlanEditor(
                exerciseName = target.exerciseName,
                draft = target.draft,
                isWeighted = target.isWeighted,
                onAction = { action -> processor.consume(Action.PlanEditAction(action)) },
            )
        }

        (state.pickerState as? PickerState.Open)?.let { picker ->
            ExercisePickerSheet(
                query = picker.query,
                results = picker.results,
                selectedUuids = picker.selectedUuids,
                onSearchChange = { processor.consume(Action.Input.OnPickerSearchChange(it)) },
                onToggle = { processor.consume(Action.Click.OnPickerToggle(it)) },
                onConfirm = { processor.consume(Action.Click.OnPickerConfirm) },
                onDismiss = { processor.consume(Action.Click.OnPickerDismiss) },
            )
        }

        if (showDiscardDialog) {
            AppDialog(
                title = discardTitle,
                body = discardBody,
                confirmLabel = discardConfirm,
                dismissLabel = discardDismiss,
                destructive = true,
                onConfirm = {
                    showDiscardDialog = false
                    processor.consume(Action.Click.OnConfirmDiscard)
                },
                onDismiss = {
                    showDiscardDialog = false
                    processor.consume(Action.Click.OnDismissDiscard)
                },
            )
        }
        permanentDeleteDialog?.let { dialog ->
            AppConfirmDialog(
                title = dialog.title,
                body = dialog.body,
                impactSummary = dialog.impactSummary,
                confirmLabel = dialog.confirmLabel,
                onConfirm = {
                    permanentDeleteDialog = null
                    processor.consume(Action.Click.OnPermanentDeleteConfirm)
                },
                onDismiss = {
                    permanentDeleteDialog = null
                    processor.consume(Action.Click.OnPermanentDeleteDismiss)
                },
            )
        }
        state.pendingConflict?.let { info ->
            ActiveSessionConflictDialog(
                activeSessionName = info.activeSessionName,
                progressLabel = info.progressLabel,
                onResume = { processor.consume(Action.Click.OnConflictResume) },
                onDeleteAndStartNew = { processor.consume(Action.Click.OnConflictDeleteAndStart) },
                onCancel = { processor.consume(Action.Click.OnConflictDismiss) },
            )
        }
    }
}
