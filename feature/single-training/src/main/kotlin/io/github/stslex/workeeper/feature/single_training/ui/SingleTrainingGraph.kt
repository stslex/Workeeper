// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.dialog.ActiveSessionConflictDialog
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadedContent
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppConfirmSheet
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagPickerSheetContent
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
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
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun NavGraphScope.singleTrainingsGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreen(SingleTrainingFeature) { processor ->

        val haptic = LocalHapticFeedback.current
        val undoToastLabel = stringResource(KitR.string.core_ui_kit_toast_undo)

        processor.Handle { event ->
            when (event) {
                is Event.HapticClick -> haptic.performHapticFeedback(event.type)
                is Event.ShowArchiveSuccess -> SnackbarManager.showSnackbar(message = event.message)
                is Event.ShowArchiveBlocked -> SnackbarManager.showSnackbar(message = event.message)
                is Event.ShowSaveError -> SnackbarManager.showSnackbar(message = event.message)

                // §4 rows 1 and 2: draft edits with an undo toast. The toast can outlive the
                // draft, so the action carries the event's draftEpoch back for the handler.
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
                                    draftEpoch = event.draftEpoch,
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
                                    draftEpoch = event.draftEpoch,
                                ),
                            )
                        },
                    ),
                )
            }
        }

        // Intercept back for unsaved edits, or to dismiss the topmost dialog.
        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnBackClick)
        }

        val state = processor.state.value

        // §26 "A route does not compose until it has loaded"; `loadTraining` must clear
        // `isLoading` on failure too, or the screen is empty forever.
        // GUARD: this wrapper must sit ABOVE the early return — `AnimatedVisibility` does not
        // animate a composable that enters composition already visible.
        AppLoadedContent(isLoaded = state.isLoading.not()) {
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
        }

        if (state.isLoading) return@navComponentScreen

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

            // ED7: every dismissal route lands on the same action — selection applies live.
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

            // §26 "Every modal on the three editors is a SHEET"; strings come from the kit.
            DialogState.DiscardConfirm -> AppConfirmSheet(
                title = stringResource(KitR.string.core_ui_kit_discard_sheet_title),
                body = stringResource(KitR.string.core_ui_kit_discard_sheet_body),
                confirmLabel = stringResource(KitR.string.core_ui_kit_discard_sheet_confirm),
                dismissLabel = stringResource(KitR.string.core_ui_kit_discard_sheet_dismiss),
                confirmDestructive = true,
                onConfirm = { processor.consume(Action.Click.OnConfirmDiscard) },
                onDismiss = { processor.consume(Action.Click.OnDismissDiscard) },
            )

            // `#sh-del`: the one true confirmation is a sheet (D-OPEN-1), impact on `emphasis`.
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
