// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.components.dialog.ActiveSessionConflictDialog
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppConfirmSheet
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagPickerSheetContent
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreenWithState
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingFeature
import io.github.stslex.workeeper.feature.single_training.mvi.store.DialogState
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.Mode
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.PickerState
import io.github.stslex.workeeper.feature.single_training.ui.components.ExercisePickerSheet
import io.github.stslex.workeeper.feature.single_training.ui.components.TrainingDetailMenuSheetContent
import kotlinx.collections.immutable.toImmutableSet
import io.github.stslex.workeeper.core.ui.kit.R as KitR

@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("UnusedParameter", "LongMethod", "CyclomaticComplexMethod")
fun NavGraphBuilder.singleTrainingsGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreenWithState(SingleTrainingFeature) { _, processor ->

        val haptic = LocalHapticFeedback.current
        val undoToastLabel = stringResource(KitR.string.core_ui_kit_toast_undo)

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.ShowArchiveSuccess -> SnackbarManager.showSnackbar(message = event.message)
                is Event.ShowArchiveBlocked -> SnackbarManager.showSnackbar(message = event.message)
                is Event.ShowSaveError -> SnackbarManager.showSnackbar(message = event.message)

                // §4's table, rows 1 and 2: DRAFT edits with an undo toast — the undo
                // re-inserts the removed thing, and nothing is persisted or deferred.
                is Event.ShowSetRemovedUndo -> SnackbarManager.showSnackbar(
                    AppSnackbarModel(
                        message = event.message,
                        actionLabel = undoToastLabel,
                        action = {
                            processor.consume(
                                Action.Click.OnUndoSetRemove(
                                    exerciseUuid = event.exerciseUuid,
                                    set = event.set,
                                    index = event.index,
                                ),
                            )
                        },
                    ),
                )

                is Event.ShowExerciseRemovedUndo -> SnackbarManager.showSnackbar(
                    AppSnackbarModel(
                        message = event.message,
                        actionLabel = undoToastLabel,
                        action = {
                            processor.consume(
                                Action.Click.OnUndoExerciseRemove(
                                    item = event.item,
                                    wasExpanded = event.wasExpanded,
                                ),
                            )
                        },
                    ),
                )
            }
        }

        // Intercept back for unsaved edits — a card's plan edit included, through the
        // snapshot signature — or to dismiss the topmost dialog.
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
        // clears it synchronously on that branch.
        //
        // LOAD-BEARING PRECONDITION: `loadTraining` must clear `isLoading` on FAILURE as well
        // as on success, because `HandlerStore.launch` defaults `onError` to `{}` (B17, B21).
        // A throw that leaves the flag set is a permanently empty screen — this gate is what
        // gives that failure a cost. `CommonHandler.loadTraining` closes its own.
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

            // ED7: search · the dictionary as chips, a tap toggles live · «+ Создать «X»» ·
            // «Готово». Dismissal by any route lands on the same action — the selection is
            // already applied, so there is nothing to confirm or roll back.
            DialogState.TagPicker -> AppBottomSheet(
                onDismiss = { processor.consume(Action.Click.OnTagPickerDismiss) },
            ) {
                AppTagPickerSheetContent(
                    selectedTagUuids = remember(state.tags) {
                        state.tags.map { it.uuid }.toImmutableSet()
                    },
                    availableTags = state.availableTags,
                    searchQuery = state.tagSearchQuery,
                    onSearchQueryChange = { processor.consume(Action.Input.OnTagSearchChange(it)) },
                    onTagToggle = { processor.consume(Action.Click.OnTagToggle(it)) },
                    onTagCreate = { processor.consume(Action.Click.OnTagCreate(it)) },
                    onDone = { processor.consume(Action.Click.OnTagPickerDismiss) },
                )
            }

            // ED10: the `⋮` menu, minus `Изменить` (it lives on the dock now).
            DialogState.DetailMenu -> AppBottomSheet(
                onDismiss = { processor.consume(Action.Click.OnDetailMenuDismiss) },
            ) {
                TrainingDetailMenuSheetContent(
                    canPermanentlyDelete = state.canPermanentlyDelete,
                    consume = processor::consume,
                )
            }

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

            // `#sh-del`'s form: the one true confirmation is a SHEET (D-OPEN-1 — §7.4 stands,
            // no dialog primitive in this language). The impact line rides `emphasis`, the
            // sheet's role-based rendering of what `AppConfirmDialog` drew as a panel.
            is DialogState.PermanentDeleteConfirm -> AppConfirmSheet(
                title = dialog.title,
                body = dialog.body,
                emphasis = dialog.impactSummary,
                confirmLabel = dialog.confirmLabel,
                dismissLabel = stringResource(KitR.string.core_ui_kit_action_cancel),
                confirmDestructive = true,
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
