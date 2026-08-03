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
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppConfirmSheet
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.getStateFlow
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreenWithState
import io.github.stslex.workeeper.core.ui.mvi.setAttrDefaultValue
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingFeature
import io.github.stslex.workeeper.feature.single_training.mvi.store.DialogState
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.Mode
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.PickerState
import io.github.stslex.workeeper.feature.single_training.ui.components.ExercisePickerSheet
import io.github.stslex.workeeper.core.ui.kit.R as KitR

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

        // §26 "A route does not compose until it has loaded". Everything above this line still
        // runs while the load is in flight — the `LaunchedEffect` that picks up the plan
        // editor's saved flag, the event `Handle`, the back interception — and only the screen
        // waits.
        //
        // Nothing is drawn instead, deliberately: neither mockup draws a loading surface, and
        // `AppNavigationHost` paints the background under every destination, so an unloaded
        // route is an empty frame in the app's own colour rather than a hole.
        //
        // `isLoading` is `uuid != null`, so a create flow is never withheld — `processInit`
        // clears it synchronously on that branch — and `Action.Common.Reload` (the plan
        // editor's return) does not re-raise it, so coming back from the plan editor does not
        // blank the screen for a frame.
        //
        // LOAD-BEARING PRECONDITION: `loadTraining` must clear `isLoading` on FAILURE as well
        // as on success, because `HandlerStore.launch` defaults `onError` to `{}` (B17, B21).
        // Before this gate a thrown load cost nothing visible; after it the same throw is a
        // permanently empty screen. `CommonHandler.loadTraining` closes its own.
        if (state.isLoading) return@navComponentScreenWithState

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

            // §26 "Every modal on the three editors is a SHEET". Strings from the kit: one
            // component, one table, three editors — three copies is how a renamed label survives
            // on one screen after being corrected on another.
            DialogState.DiscardConfirm -> AppConfirmSheet(
                title = stringResource(KitR.string.core_ui_kit_discard_sheet_title),
                body = stringResource(KitR.string.core_ui_kit_discard_sheet_body),
                confirmLabel = stringResource(KitR.string.core_ui_kit_discard_sheet_confirm),
                dismissLabel = stringResource(KitR.string.core_ui_kit_discard_sheet_dismiss),
                confirmDestructive = true,
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
