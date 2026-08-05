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
import io.github.stslex.workeeper.core.ui.kit.components.pr.PrExplainerDialog
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppConfirmSheet
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagPickerSheetContent
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.getStateFlow
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreenWithState
import io.github.stslex.workeeper.core.ui.mvi.setAttrDefaultValue
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
import io.github.stslex.workeeper.core.ui.kit.R as KitR

@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun NavGraphBuilder.exerciseGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreenWithState(ExerciseFeature) { stateHandle, processor ->

        // Image-viewer return. The viewer carries the picture's two verbs now (§26, "The image
        // moves into the pushed top bar") and performs neither: it pops with a REQUEST, and the
        // machinery that can honour it — the source sheet, the camera permission, the temp URI,
        // the uncommitted `PendingImage` — stays here, where it already was. Same shape as the
        // plan editor's two returns above, including the reset: an attr left set would re-fire
        // the request on the next resume.
        val imageRequestAttr by stateHandle
            .getStateFlow(Screen.ExerciseImage.exerciseImageRequestAttr)
            .collectAsState()
        LaunchedEffect(imageRequestAttr) {
            val request = imageRequestAttr
                ?.let { name -> Screen.ExerciseImageRequest.entries.firstOrNull { it.name == name } }
            if (request != null) {
                when (request) {
                    Screen.ExerciseImageRequest.REPLACE ->
                        processor.consume(Action.Click.OnEditImageClick)

                    Screen.ExerciseImageRequest.REMOVE ->
                        processor.consume(Action.Click.OnRemoveImageClick)
                }
                stateHandle.setAttrDefaultValue(Screen.ExerciseImage.exerciseImageRequestAttr)
            }
        }

        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current
        val undoLabel = stringResource(R.string.feature_exercise_detail_archive_undo)
        val undoToastLabel = stringResource(KitR.string.core_ui_kit_toast_undo)
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
                        action = { processor.consume(Action.Click.OnUndoArchive(event.uuid)) },
                    ),
                )

                is Event.ShowTagLimitReached -> SnackbarManager.showSnackbar(message = event.message)

                // ED11's deferred delete: the toast is hosted app-level — ABOVE the popped
                // destination — and its own lifetime IS the undo window. `action` (Отменить)
                // does nothing because nothing has been deleted; `onDismissed` is the commit,
                // run by the host's collector, which outlives this screen (D-OPEN-10: a
                // process death cancels it and the row survives).
                is Event.ShowPermanentDeleteUndo -> SnackbarManager.showSnackbar(
                    AppSnackbarModel(
                        message = event.message,
                        actionLabel = undoToastLabel,
                        action = { },
                        onDismissed = event.commit,
                    ),
                )

                is Event.ShowSetRemovedUndo -> SnackbarManager.showSnackbar(
                    AppSnackbarModel(
                        message = event.message,
                        actionLabel = undoToastLabel,
                        action = {
                            processor.consume(
                                Action.Click.OnUndoSetRemove(set = event.set, index = event.index),
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

        // Intercept the system back gesture for unsaved edits or open dialogs — otherwise
        // BackHandler stays unsubscribed so Compose nav handles the gesture natively
        // (including the Android 13+ predictive-back preview animation). The TopAppBar
        // back arrow and Cancel button still emit OnBackClick directly so explicit taps
        // always flow through the store regardless of interceptBack.
        BackHandler(enabled = processor.state.value.interceptBack) {
            processor.consume(Action.Click.OnBackClick)
        }

        val state = processor.state.value

        // §26 "A route does not compose until it has loaded". Everything above this line still
        // runs while the load is in flight — the two `LaunchedEffect`s, the activity-result
        // launchers, the event `Handle`, the back interception — and only the screen waits.
        //
        // Nothing is drawn instead, deliberately: neither mockup draws a loading surface, and
        // `AppNavigationHost` paints the background under every destination, so an unloaded
        // route is an empty frame in the app's own colour rather than a hole.
        //
        // It gates BOTH modes because they are one route and one store. `isLoading` is
        // `uuid != null`, so a create flow is never withheld — there is nothing to load — and
        // an existing exercise never draws a shell with a blank name and an empty history
        // while the read is in flight.
        //
        // LOAD-BEARING PRECONDITION: `loadExercise` must clear `isLoading` on FAILURE as well
        // as on success, because `HandlerStore.launch` defaults `onError` to `{}` (B17, B21).
        // A throw that leaves the flag set is a permanently empty screen — this gate is what
        // gives that failure a cost. `CommonHandler.loadExercise` closes its own.
        if (state.isLoading) return@navComponentScreenWithState

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

            // ED7: search · the dictionary as chips, a tap toggles live · «+ Создать «X»» ·
            // «Готово». Dismissal by any route lands on the same action — the selection is
            // already applied, so there is nothing to confirm or roll back.
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

            // §26 "Every modal on the three editors is a SHEET" — the drawing has no dialog
            // primitive at all. Strings from the kit: one component, one table, three editors.
            is DialogState.DiscardConfirm -> AppConfirmSheet(
                title = stringResource(KitR.string.core_ui_kit_discard_sheet_title),
                body = stringResource(KitR.string.core_ui_kit_discard_sheet_body),
                confirmLabel = stringResource(KitR.string.core_ui_kit_discard_sheet_confirm),
                dismissLabel = stringResource(KitR.string.core_ui_kit_discard_sheet_dismiss),
                confirmDestructive = true,
                onConfirm = { processor.consume(Action.Click.OnConfirmDiscard(dialog.target)) },
                onDismiss = { processor.consume(Action.Click.OnDismissDiscard) },
            )

            // The inline plan editor's type switch. Same sheet the full-screen route raises, so
            // the two hosts ask the question the same way.
            is DialogState.TypeChangeConfirm -> AppConfirmSheet(
                title = dialog.title,
                body = dialog.body,
                emphasis = dialog.impactSummary,
                confirmLabel = dialog.confirmLabel,
                dismissLabel = stringResource(KitR.string.core_ui_kit_discard_sheet_dismiss),
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

            // `#sh-del`'s form: the one true confirmation is a SHEET (D-OPEN-1 — §7.4 stands,
            // no dialog primitive in this language). The impact line rides `emphasis`.
            is DialogState.PermanentDeleteConfirm -> AppConfirmSheet(
                title = dialog.title,
                body = dialog.body,
                emphasis = dialog.impactSummary,
                confirmLabel = dialog.confirmLabel,
                dismissLabel = stringResource(KitR.string.core_ui_kit_action_cancel),
                confirmDestructive = true,
                onConfirm = { processor.consume(Action.Click.OnConfirmPermanentDelete) },
                onDismiss = { processor.consume(Action.Click.OnDismissPermanentDelete) },
            )

            DialogState.PrExplainer -> PrExplainerDialog(
                onDismiss = { processor.consume(Action.Click.OnPrExplainerDismiss) },
            )

            // A MENU sheet rather than a confirm one: two choices and no question, which is
            // `#sh-pick`'s shape. The two Material photo glyphs go with the dialog — the kit
            // ships neither, and inventing them would settle B33(b)'s open questions.
            // The cancel button goes too: a sheet's scrim and drag are its dismiss.
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
                dismissLabel = stringResource(KitR.string.core_ui_kit_action_cancel),
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
