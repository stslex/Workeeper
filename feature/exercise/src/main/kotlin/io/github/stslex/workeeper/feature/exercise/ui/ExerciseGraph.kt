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
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.kit.components.dialog.ActiveSessionConflictDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.plan_editor.AppPlanEditor
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.di.ExerciseFeature
import io.github.stslex.workeeper.feature.exercise.mvi.model.ImageErrorType
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.DiscardTarget
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State.Mode
import io.github.stslex.workeeper.feature.exercise.ui.components.ImageSourceDialog
import io.github.stslex.workeeper.feature.exercise.ui.components.PermissionDeniedDialog

@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("UnusedParameter", "LongMethod", "CyclomaticComplexMethod")
fun NavGraphBuilder.exerciseGraph(
//    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(ExerciseFeature) { processor ->
        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current
        val undoLabel = stringResource(R.string.feature_exercise_detail_archive_undo)
        val discardTitle = stringResource(R.string.feature_exercise_edit_discard_title)
        val discardBody = stringResource(R.string.feature_exercise_edit_discard_body)
        val discardConfirm = stringResource(R.string.feature_exercise_edit_discard_confirm)
        val discardDismiss = stringResource(R.string.feature_exercise_edit_discard_dismiss)
        val archiveBlockedTitle =
            stringResource(R.string.feature_exercise_detail_archive_blocked_title)
        val archiveBlockedOk = stringResource(R.string.feature_exercise_detail_archive_blocked_ok)
        val imageSaveFailed = stringResource(R.string.feature_exercise_image_error_save_failed)
        val imageLoadFailed = stringResource(R.string.feature_exercise_image_error_load_failed)
        val imageDecodeFailed = stringResource(R.string.feature_exercise_image_error_decode_failed)

        var pendingDiscard by remember { mutableStateOf<DiscardTarget?>(null) }
        var archiveBlockedBody by remember { mutableStateOf<String?>(null) }
        var permanentDeleteDialog by remember {
            mutableStateOf<Event.ShowPermanentDeleteConfirm?>(
                null,
            )
        }
        var typeChangeDialog by remember { mutableStateOf<Event.ShowTypeChangeConfirm?>(null) }
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
                        withDismissAction = true,
                        action = { processor.consume(Action.Click.OnUndoArchive(event.uuid)) },
                    ),
                )

                is Event.ShowArchiveBlocked -> {
                    archiveBlockedBody = event.body
                }

                is Event.ShowTagLimitReached -> SnackbarManager.showSnackbar(message = event.message)
                is Event.ShowActiveSessionConflict -> Unit // rendered from state.pendingConflict
                is Event.ShowDiscardConfirmDialog -> {
                    pendingDiscard = event.target
                }

                is Event.ShowPermanentDeleteConfirm -> {
                    permanentDeleteDialog = event
                }

                is Event.ShowPermanentDeleteSuccess ->
                    SnackbarManager.showSnackbar(message = event.message)

                is Event.ShowTypeChangeConfirm -> {
                    typeChangeDialog = event
                }

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
        archiveBlockedBody?.let { body ->
            AppDialog(
                title = archiveBlockedTitle,
                body = body,
                confirmLabel = archiveBlockedOk,
                onConfirm = {
                    archiveBlockedBody = null
                    processor.consume(Action.Click.OnDismissArchiveBlocked)
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
                    processor.consume(Action.Click.OnConfirmPermanentDelete)
                },
                onDismiss = {
                    permanentDeleteDialog = null
                    processor.consume(Action.Click.OnDismissPermanentDelete)
                },
            )
        }
        typeChangeDialog?.let { dialog ->
            AppConfirmDialog(
                title = dialog.title,
                body = dialog.body,
                impactSummary = dialog.impactSummary,
                confirmLabel = dialog.confirmLabel,
                onConfirm = {
                    typeChangeDialog = null
                    processor.consume(Action.Click.OnTypeChangeConfirm)
                },
                onDismiss = {
                    typeChangeDialog = null
                    processor.consume(Action.Click.OnTypeChangeDismiss)
                },
            )
        }
        state.planEditorTarget?.let { target ->
            AppPlanEditor(
                exerciseName = state.name,
                draft = target.draft,
                isWeighted = state.type == ExerciseTypeUiModel.WEIGHTED,
                onAction = { action -> processor.consume(Action.PlanEditorAction(action)) },
            )
        }
        if (state.sourceDialogVisible) {
            ImageSourceDialog(
                onSourceSelected = { source ->
                    processor.consume(Action.Click.OnImageSourceSelected(source))
                },
                onDismiss = {
                    processor.consume(Action.Click.OnImageSourceDialogDismiss)
                },
            )
        }
        if (state.permissionDeniedDialogVisible) {
            PermissionDeniedDialog(
                onSettingsClick = {
                    processor.consume(Action.Click.OnPermissionDeniedSettingsClick)
                },
                onDismiss = {
                    processor.consume(Action.Click.OnPermissionDeniedDialogDismiss)
                },
            )
        }
        state.pendingConflict?.let { info ->
            ActiveSessionConflictDialog(
                activeSessionName = info.activeSessionName,
                progressLabel = info.progressLabel,
                onResume = { processor.consume(Action.Click.OnTrackNowResumeConfirm) },
                onDeleteAndStartNew = {
                    processor.consume(Action.Click.OnTrackNowDeleteAndStart)
                },
                onCancel = { processor.consume(Action.Click.OnTrackNowConflictDismiss) },
            )
        }
    }
}
