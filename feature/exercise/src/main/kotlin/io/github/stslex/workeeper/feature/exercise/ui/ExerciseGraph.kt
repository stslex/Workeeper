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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppBlockedArchiveDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.getStateFlow
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreenWithState
import io.github.stslex.workeeper.core.ui.mvi.setAttrDefaultValue
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.di.ExerciseFeature
import io.github.stslex.workeeper.feature.exercise.ui.components.ImageSourceDialog
import io.github.stslex.workeeper.feature.exercise.ui.components.PermissionDeniedDialog
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageErrorType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun NavGraphBuilder.exerciseGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreenWithState(ExerciseFeature) { stateHandle, processor ->

        // Existing-mode return: PlanEditor wrote (type, plan) to disk and signaled with
        // `planEditorSavedAttr = true`. The CommonHandler runs a *partial* reload — only
        // (type, adhocPlan) are refreshed — so any unsaved name/description/tag/image
        // edit on this form is preserved (this is the v1.41.0 dirty-baseline regression
        // fix).
        val savedAttr by stateHandle
            .getStateFlow(Screen.PlanEditor.planEditorSavedAttr)
            .collectAsState()
        LaunchedEffect(savedAttr) {
            if (savedAttr == true) {
                processor.consume(Action.Common.PlanEditorExistingReturned)
                stateHandle.setAttrDefaultValue(Screen.PlanEditor.planEditorSavedAttr)
            }
        }

        // Draft-mode return: PlanEditor never touched the DB. The Done click pops back
        // with the serialized PlanDraftResult JSON in `planEditorDraftResultAttr`. The
        // CommonHandler decodes the JSON and merges (type, adhocPlan) into State without
        // updating `originalSnapshot` — the draft is treated as an unsaved edit until the
        // parent form's own Save fires.
        val draftAttr by stateHandle
            .getStateFlow(Screen.PlanEditor.planEditorDraftResultAttr)
            .collectAsState()
        LaunchedEffect(draftAttr) {
            val payload = draftAttr
            if (payload != null) {
                processor.consume(Action.Common.PlanEditorDraftReturned(payload))
                stateHandle.setAttrDefaultValue(Screen.PlanEditor.planEditorDraftResultAttr)
            }
        }

        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current
        val undoLabel = stringResource(R.string.feature_exercise_detail_archive_undo)
        val imageSaveFailed = stringResource(R.string.feature_exercise_image_error_save_failed)
        val imageLoadFailed = stringResource(R.string.feature_exercise_image_error_load_failed)
        val imageDecodeFailed =
            stringResource(R.string.feature_exercise_image_error_decode_failed)

        // pendingCameraTempUri is *bridge state* for the camera Activity Result Contract,
        // not a UI dialog. The launcher's result callback needs the URI it was launched
        // with so it can decide whether the capture succeeded; rotation between launch and
        // result is rare in practice and recovering after process death is out of scope
        // for this rule. Keep as a local var.
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

                is Event.ShowTagLimitReached -> SnackbarManager.showSnackbar(message = event.message)

                is Event.ShowPermanentDeleteSuccess ->
                    SnackbarManager.showSnackbar(message = event.message)

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

        // Intercept the system back gesture for unsaved edits or open dialogs — otherwise
        // BackHandler stays unsubscribed so Compose nav handles the gesture natively
        // (including the Android 13+ predictive-back preview animation). The TopAppBar
        // back arrow and Cancel button still emit OnBackClick directly so explicit taps
        // always flow through the store regardless of interceptBack.
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

        when (val dialog = state.dialogState) {
            DialogState.Hidden -> Unit

            is DialogState.DiscardConfirm -> AppDialog(
                title = stringResource(R.string.feature_exercise_edit_discard_title),
                body = stringResource(R.string.feature_exercise_edit_discard_body),
                confirmLabel = stringResource(R.string.feature_exercise_edit_discard_confirm),
                dismissLabel = stringResource(R.string.feature_exercise_edit_discard_dismiss),
                destructive = true,
                onConfirm = { processor.consume(Action.Click.OnConfirmDiscard(dialog.target)) },
                onDismiss = { processor.consume(Action.Click.OnDismissDiscard) },
            )

            is DialogState.ArchiveBlocked -> AppBlockedArchiveDialog(
                title = stringResource(R.string.feature_exercise_detail_archive_blocked_title),
                items = persistentListOf(dialog.item),
                nextStep = stringResource(R.string.feature_exercise_detail_archive_blocked_next_step),
                confirmLabel = stringResource(R.string.feature_exercise_detail_archive_blocked_ok),
                onDismiss = { processor.consume(Action.Click.OnDismissArchiveBlocked) },
            )

            is DialogState.PermanentDeleteConfirm -> AppConfirmDialog(
                title = dialog.title,
                body = dialog.body,
                impactSummary = dialog.impactSummary,
                confirmLabel = dialog.confirmLabel,
                onConfirm = { processor.consume(Action.Click.OnConfirmPermanentDelete) },
                onDismiss = { processor.consume(Action.Click.OnDismissPermanentDelete) },
            )

            DialogState.ImageSourcePicker -> ImageSourceDialog(
                onSourceSelected = { source ->
                    processor.consume(Action.Click.OnImageSourceSelected(source))
                },
                onDismiss = { processor.consume(Action.Click.OnImageSourceDialogDismiss) },
            )

            DialogState.PermissionDenied -> PermissionDeniedDialog(
                onSettingsClick = {
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
