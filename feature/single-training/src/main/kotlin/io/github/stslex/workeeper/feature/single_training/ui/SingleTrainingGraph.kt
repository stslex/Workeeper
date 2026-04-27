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
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppPlanEditor
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppPlanEditorAction
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppPlanEditorState
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
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
        val archiveSuccessFormat =
            stringResource(R.string.feature_training_detail_archive_success_format)
        val archiveBlocked = stringResource(R.string.feature_training_detail_archive_blocked)
        val livePending = stringResource(R.string.feature_training_detail_live_workout_pending)
        val otherSessionFormat =
            stringResource(R.string.feature_training_detail_other_session_active_format)
        val noExercises = stringResource(R.string.feature_training_edit_error_no_exercises)
        val discardTitle = stringResource(R.string.feature_training_edit_discard_title)
        val discardBody = stringResource(R.string.feature_training_edit_discard_body)
        val discardConfirm = stringResource(R.string.feature_training_edit_discard_confirm)
        val discardDismiss = stringResource(R.string.feature_training_edit_discard_dismiss)
        val permanentDeleteTitleFormat =
            stringResource(R.string.feature_training_detail_permanent_delete_title)
        val permanentDeleteBody =
            stringResource(R.string.feature_training_detail_permanent_delete_body)
        val permanentDeleteImpact =
            stringResource(R.string.feature_training_detail_permanent_delete_impact)
        val permanentDeleteConfirmLabel =
            stringResource(R.string.feature_training_detail_permanent_delete_confirm)

        var showDiscardDialog by remember { mutableStateOf(false) }
        var showPermanentDeleteDialog by remember { mutableStateOf(false) }

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.ShowArchiveSuccess ->
                    SnackbarManager.showSnackbar(message = archiveSuccessFormat.format(event.name))

                is Event.ShowArchiveBlocked -> SnackbarManager.showSnackbar(message = archiveBlocked)
                Event.ShowDiscardConfirmDialog -> { showDiscardDialog = true }
                Event.ShowPermanentDeleteConfirmDialog -> { showPermanentDeleteDialog = true }
                Event.ShowLiveWorkoutPending -> SnackbarManager.showSnackbar(message = livePending)
                is Event.ShowOtherSessionActive ->
                    SnackbarManager.showSnackbar(message = otherSessionFormat.format(event.trainingName))

                is Event.ShowSaveError -> SnackbarManager.showSnackbar(message = noExercises)
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
                state = AppPlanEditorState(
                    exerciseName = target.exerciseName,
                    draft = target.draft,
                ),
                isWeighted = target.isWeighted,
                onAction = { action -> processor.consume(action.toStoreAction()) },
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
        if (showPermanentDeleteDialog) {
            AppConfirmDialog(
                title = permanentDeleteTitleFormat.format(state.name),
                body = permanentDeleteBody,
                impactSummary = permanentDeleteImpact,
                confirmLabel = permanentDeleteConfirmLabel,
                onConfirm = {
                    showPermanentDeleteDialog = false
                    processor.consume(Action.Click.OnPermanentDeleteConfirm)
                },
                onDismiss = {
                    showPermanentDeleteDialog = false
                    processor.consume(Action.Click.OnPermanentDeleteDismiss)
                },
            )
        }
    }
}

private fun AppPlanEditorAction.toStoreAction(): Action.Click = when (this) {
    is AppPlanEditorAction.OnSetWeightChange ->
        Action.Click.OnPlanEditorSetWeight(index = index, value = value)

    is AppPlanEditorAction.OnSetRepsChange ->
        Action.Click.OnPlanEditorSetReps(index = index, reps = reps)

    is AppPlanEditorAction.OnSetTypeChange ->
        Action.Click.OnPlanEditorSetType(index = index, type = type)

    is AppPlanEditorAction.OnSetRemove ->
        Action.Click.OnPlanEditorRemoveSet(index = index)

    AppPlanEditorAction.OnAddSet -> Action.Click.OnPlanEditorAddSet
    AppPlanEditorAction.OnSave -> Action.Click.OnPlanEditorSave
    AppPlanEditorAction.OnDismiss -> Action.Click.OnPlanEditorDismiss
}
