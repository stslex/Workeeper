// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.components.dialog.ActiveSessionConflictDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.getStateFlow
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreenWithState
import io.github.stslex.workeeper.core.ui.mvi.setAttrDefaultValue
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingFeature
import io.github.stslex.workeeper.feature.single_training.mvi.store.DialogState
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
    navComponentScreenWithState(SingleTrainingFeature) { stateHandle, processor ->

        val attrValue by stateHandle
            .getStateFlow(Screen.PlanEditor.planEditorSavedAttr)
            .collectAsState()

        LaunchedEffect(attrValue) {
            if (attrValue == true) {
                processor.consume(Action.Common.Reload)
                stateHandle.setAttrDefaultValue(Screen.PlanEditor.planEditorSavedAttr)
            }
        }

        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.ShowArchiveSuccess -> SnackbarManager.showSnackbar(message = event.message)
                is Event.ShowArchiveBlocked -> SnackbarManager.showSnackbar(message = event.message)
                is Event.ShowSaveError -> SnackbarManager.showSnackbar(message = event.message)
            }
        }

        // Intercept back for unsaved edits or to dismiss the topmost dialog. The plan
        // editor lives on its own route (Screen.PlanEditor) and owns its own dirty-state
        // interception.
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

        when (val dialog = state.dialogState) {
            DialogState.Hidden -> Unit

            DialogState.DiscardConfirm -> AppDialog(
                title = stringResource(R.string.feature_training_edit_discard_title),
                body = stringResource(R.string.feature_training_edit_discard_body),
                confirmLabel = stringResource(R.string.feature_training_edit_discard_confirm),
                dismissLabel = stringResource(R.string.feature_training_edit_discard_dismiss),
                destructive = true,
                onConfirm = { processor.consume(Action.Click.OnConfirmDiscard) },
                onDismiss = { processor.consume(Action.Click.OnDismissDiscard) },
            )

            is DialogState.PermanentDeleteConfirm -> AppConfirmDialog(
                title = dialog.title,
                body = dialog.body,
                impactSummary = dialog.impactSummary,
                confirmLabel = dialog.confirmLabel,
                onConfirm = { processor.consume(Action.Click.OnPermanentDeleteConfirm) },
                onDismiss = { processor.consume(Action.Click.OnPermanentDeleteDismiss) },
            )

            is DialogState.ActiveSessionConflict -> ActiveSessionConflictDialog(
                activeSessionName = dialog.activeSessionName,
                progressLabel = dialog.progressLabel,
                onResume = { processor.consume(Action.Click.OnConflictResume) },
                onDeleteAndStartNew = { processor.consume(Action.Click.OnConflictDeleteAndStart) },
                onCancel = { processor.consume(Action.Click.OnConflictDismiss) },
            )
        }
    }
}
