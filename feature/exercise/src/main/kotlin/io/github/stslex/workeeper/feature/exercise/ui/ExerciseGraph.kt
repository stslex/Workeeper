// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import io.github.stslex.workeeper.core.ui.kit.components.dialog.ActiveSessionConflictDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppBlockedArchiveDialog
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadedContent
import io.github.stslex.workeeper.core.ui.kit.components.pr.PrExplainerDialog
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppConfirmSheet
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagPickerSheetContent
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_cancel
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_discard_sheet_body
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_discard_sheet_confirm
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_discard_sheet_dismiss
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_discard_sheet_title
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_toast_undo
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreenWithResults
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.di.ExerciseFeature
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseDetailMenuSheetContent
import io.github.stslex.workeeper.feature.exercise.ui.components.ImageSourceSheetContent
import io.github.stslex.workeeper.feature.exercise.ui.components.PlanInfoSheetContent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageErrorType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun NavGraphScope.exerciseGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreenWithResults(ExerciseFeature) { results, processor ->

        // Image-viewer return: the viewer pops with a REQUEST, the Store resolves what it means.
        results.OnResult(Screen.ExerciseImage::class) { request ->
            processor.consume(Action.Common.ImageRequestReceived(request))
        }

        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current
        val undoLabel = stringResource(R.string.feature_exercise_detail_archive_undo)
        val undoToastLabel = stringResource(Res.string.core_ui_kit_toast_undo)
        val imageSaveFailed = stringResource(R.string.feature_exercise_image_error_save_failed)
        val imageLoadFailed = stringResource(R.string.feature_exercise_image_error_load_failed)
        val imageDecodeFailed =
            stringResource(R.string.feature_exercise_image_error_decode_failed)

        // Bridge state for the camera contract: the result callback needs the URI it launched
        // with. Rotation between launch and result is out of scope.
        var pendingCameraTempUri by remember { mutableStateOf<Uri?>(null) }

        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture(),
        ) { success ->
            val uri = pendingCameraTempUri
            if (success && uri != null) {
                processor.consume(Action.Common.ImagePicked(uri))
            } else {
                processor.consume(Action.Common.ImagePickCancelled)
            }
            pendingCameraTempUri = null
        }
        val galleryLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                processor.consume(Action.Common.ImagePicked(uri))
            } else {
                processor.consume(Action.Common.ImagePickCancelled)
            }
        }
        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                processor.consume(Action.Click.RequestCameraCapture)
            } else {
                processor.consume(Action.Click.OnCameraPermissionDenied)
            }
        }

        processor.Handle { event ->
            when (event) {
                is Event.Haptic -> haptic.performHapticFeedback(event.type)
                is Event.ShowArchiveSuccess -> SnackbarManager.showSnackbar(
                    AppSnackbarModel(
                        message = event.message,
                        actionLabel = undoLabel,
                        action = { processor.consume(Action.Click.OnUndoArchive(event.uuid)) },
                    ),
                )

                is Event.ShowTagLimitReached -> SnackbarManager.showSnackbar(message = event.message)

                // The toast can outlive the draft, so the action carries the epoch back.
                is Event.ShowSetRemovedUndo -> SnackbarManager.showSnackbar(
                    AppSnackbarModel(
                        message = event.message,
                        actionLabel = undoToastLabel,
                        action = {
                            processor.consume(
                                Action.Click.OnUndoSetRemove(
                                    set = event.set,
                                    index = event.index,
                                    draftEpoch = event.draftEpoch,
                                ),
                            )
                        },
                    ),
                )

                is Event.NavigateLaunchCamera -> {
                    pendingCameraTempUri = event.tempUri
                    cameraLauncher.launch(event.tempUri)
                }

                Event.NavigateLaunchGallery -> {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }

                Event.NavigateRequestCameraPermission -> {
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }

                is Event.NavigateOpenAppSettings -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${event.packageName}".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }

                is Event.ShowImageError -> {
                    // TODO(tech-debt): UI mapping boundary — see documentation/tech-debt.md
                    val message = when (event.errorType) {
                        ImageErrorType.SaveFailed -> imageSaveFailed
                        ImageErrorType.LoadFailed -> imageLoadFailed
                        ImageErrorType.DecodeFailed -> imageDecodeFailed
                    }
                    SnackbarManager.showSnackbar(message = message)
                }
            }
        }

        // Intercepted only for unsaved edits or open dialogs; otherwise nav handles back natively.
        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnBackClick)
        }

        val state = processor.state.value

        // GUARD: this wrapper must sit ABOVE the early return — AnimatedVisibility does not
        // animate a composable that enters composition already visible, so the fade would vanish.
        AppLoadedContent(isLoaded = state.isLoading.not()) {
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
        }

        if (state.isLoading) return@navComponentScreenWithResults

        when (state.bottomSheetState) {
            BottomSheetState.Hidden -> Unit

            BottomSheetState.DetailMenu -> AppBottomSheet(
                onDismiss = { processor.consume(Action.Click.OnSheetDismiss) },
            ) {
                ExerciseDetailMenuSheetContent(
                    canPermanentlyDelete = state.canPermanentlyDelete,
                    consume = processor::consume,
                )
            }

            // ED8: the plan head's `(i)`. The head keeps a short label; the reason lives here.
            BottomSheetState.PlanInfo -> AppBottomSheet(
                onDismiss = { processor.consume(Action.Click.OnSheetDismiss) },
            ) {
                PlanInfoSheetContent(consume = processor::consume)
            }

            // ED7: dismissal by any route lands on one action — the selection is already applied.
            BottomSheetState.TagPicker -> AppBottomSheet(
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
        }

        when (val dialog = state.dialogState) {
            DialogState.Hidden -> Unit

            // §26: every modal on the three editors is a sheet; the strings come from the kit.
            is DialogState.DiscardConfirm -> AppConfirmSheet(
                title = stringResource(Res.string.core_ui_kit_discard_sheet_title),
                body = stringResource(Res.string.core_ui_kit_discard_sheet_body),
                confirmLabel = stringResource(Res.string.core_ui_kit_discard_sheet_confirm),
                dismissLabel = stringResource(Res.string.core_ui_kit_discard_sheet_dismiss),
                confirmDestructive = true,
                onConfirm = { processor.consume(Action.Click.OnConfirmDiscard(dialog.target)) },
                onDismiss = { processor.consume(Action.Click.OnDismissDiscard) },
            )

            // The inline plan editor's type switch — the same sheet the full-screen route raises.
            is DialogState.TypeChangeConfirm -> AppConfirmSheet(
                title = dialog.title,
                body = dialog.body,
                emphasis = dialog.impactSummary,
                confirmLabel = dialog.confirmLabel,
                dismissLabel = stringResource(Res.string.core_ui_kit_discard_sheet_dismiss),
                confirmDestructive = true,
                onConfirm = { processor.consume(Action.Click.OnTypeChangeConfirm) },
                onDismiss = { processor.consume(Action.Click.OnTypeChangeDismiss) },
            )

            is DialogState.ArchiveBlocked -> AppBlockedArchiveDialog(
                title = stringResource(R.string.feature_exercise_detail_archive_blocked_title),
                items = persistentListOf(dialog.item),
                nextStep = stringResource(R.string.feature_exercise_detail_archive_blocked_next_step),
                confirmLabel = stringResource(R.string.feature_exercise_detail_archive_blocked_ok),
                onDismiss = { processor.consume(Action.Click.OnDismissArchiveBlocked) },
            )

            // `#sh-del`: the one true confirmation is a sheet; the impact line rides `emphasis`.
            is DialogState.PermanentDeleteConfirm -> AppConfirmSheet(
                title = dialog.title,
                body = dialog.body,
                emphasis = dialog.impactSummary,
                confirmLabel = dialog.confirmLabel,
                dismissLabel = stringResource(Res.string.core_ui_kit_action_cancel),
                confirmDestructive = true,
                onConfirm = { processor.consume(Action.Click.OnConfirmPermanentDelete) },
                onDismiss = { processor.consume(Action.Click.OnDismissPermanentDelete) },
            )

            DialogState.PrExplainer -> PrExplainerDialog(
                onDismiss = { processor.consume(Action.Click.OnPrExplainerDismiss) },
            )

            // A MENU sheet rather than a confirm one: two choices and no question (`#sh-pick`).
            DialogState.ImageSourcePicker -> AppBottomSheet(
                onDismiss = { processor.consume(Action.Click.OnImageSourceDialogDismiss) },
            ) {
                ImageSourceSheetContent(
                    onSourceSelected = { source ->
                        processor.consume(Action.Click.OnImageSourceSelected(source))
                    },
                )
            }

            DialogState.PermissionDenied -> AppConfirmSheet(
                title = stringResource(R.string.feature_exercise_image_permission_denied_title),
                body = stringResource(R.string.feature_exercise_image_permission_denied_body),
                confirmLabel = stringResource(
                    R.string.feature_exercise_image_permission_denied_action_settings,
                ),
                dismissLabel = stringResource(Res.string.core_ui_kit_action_cancel),
                onConfirm = {
                    processor.consume(Action.Click.OnPermissionDeniedSettingsClick)
                },
                onDismiss = { processor.consume(Action.Click.OnPermissionDeniedDialogDismiss) },
            )

            is DialogState.ActiveSessionConflict -> ActiveSessionConflictDialog(
                activeSessionName = dialog.activeSessionName,
                progressLabel = dialog.progressLabel,
                onResume = { processor.consume(Action.Click.OnTrackNowResumeConfirm) },
                onDeleteAndStartNew = {
                    processor.consume(Action.Click.OnTrackNowDeleteAndStart)
                },
                onCancel = { processor.consume(Action.Click.OnTrackNowConflictDismiss) },
            )
        }
    }
}
