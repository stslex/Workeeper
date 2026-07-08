// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.images.ImageRef
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.utils.CommonExt.parseOrRandom
import io.github.stslex.workeeper.core.ui.kit.components.dialog.BlockedArchiveItem
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.domain.PlanDraftReducer
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseChangeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.SaveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.TrackNowConflict
import io.github.stslex.workeeper.feature.exercise.ui.mvi.mapper.ExerciseUiMapper.toAdhocPlanSummary
import io.github.stslex.workeeper.feature.exercise.ui.mvi.mapper.ExerciseUiMapper.toDomain
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageErrorType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageSourceUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.DiscardTarget
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions", "LargeClass")
@SingleIn(ExerciseScope::class)
internal class ClickHandler @Inject constructor(
    private val interactor: ExerciseInteractor,
    private val resourceWrapper: ResourceWrapper,
    // Plain Context: @ApplicationContext is resolved on the Hilt side of the bridge
    // (ExerciseHiltEntryPoint) and bound bare into the graph — one Context per graph.
    private val context: Context,
    @MainImmediateDispatcher
    private val mainDispatcher: CoroutineDispatcher,
    store: ExerciseHandlerStore,
) : Handler<Action.Click>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnBackClick -> processBackClick()
            Action.Click.OnEditClick -> processEditClick()
            Action.Click.OnArchiveMenuClick -> processArchiveClick()
            Action.Click.OnTrackNowClick -> processTrackNowClick()
            Action.Click.OnTrackNowResumeConfirm -> processTrackNowResumeConfirm()
            Action.Click.OnTrackNowDeleteAndStart -> processTrackNowDeleteAndStart()
            Action.Click.OnTrackNowConflictDismiss -> processCloseDialog()
            is Action.Click.OnHistoryRowClick -> processHistoryRowClick(action)
            Action.Click.OnSaveClick -> processSaveClick()
            Action.Click.OnCancelClick -> processCancelClick()
            is Action.Click.OnConfirmDiscard -> processConfirmDiscard(action.target)
            Action.Click.OnDismissDiscard -> processCloseDialog()
            Action.Click.OnDismissArchiveBlocked -> processCloseDialog()
            Action.Click.FlipToReadMode -> processFlipToReadMode()
            Action.Click.OnPermanentDeleteMenuClick -> processPermanentDeleteMenuClick()
            Action.Click.OnConfirmPermanentDelete -> processConfirmPermanentDelete()
            Action.Click.OnDismissPermanentDelete -> processCloseDialog()
            is Action.Click.OnUndoArchive -> processUndoArchive(action)
            Action.Click.OnEditPlanClick -> processEditPlanClick()
            is Action.Click.OnAdhocPlanEditorAction -> processAdhocPlanEditorAction(action)
            is Action.Click.OnTagToggle -> processTagToggle(action)
            is Action.Click.OnTagRemove -> processTagRemove(action)
            is Action.Click.OnTagCreate -> processTagCreate(action)
            Action.Click.OnEditImageClick -> processEditImageClick()
            Action.Click.OnImageThumbnailClick -> processImageThumbnailClick()
            is Action.Click.OnImageSourceSelected -> processImageSourceSelected(action)
            Action.Click.OnRemoveImageClick -> processRemoveImageClick()
            Action.Click.OnPrCardClick -> processPrCardClick()
            Action.Click.OnImageSourceDialogDismiss -> processCloseDialog()
            Action.Click.OnPermissionDeniedDialogDismiss -> processCloseDialog()
            Action.Click.OnPermissionDeniedSettingsClick -> processPermissionDeniedSettingsClick()
            Action.Click.RequestCameraCapture -> launchCameraCapture()
            Action.Click.OnCameraPermissionDenied -> processCameraPermissionDenied()
        }
    }

    private fun processCloseDialog() {
        updateState { it.copy(dialogState = DialogState.Hidden) }
    }

    private fun processBackClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val current = state.value
        // Back gesture dismisses the topmost dialog before propagating.
        if (current.dialogState !is DialogState.Hidden) {
            updateState { it.copy(dialogState = DialogState.Hidden) }
            return
        }
        val mode = current.mode
        if (mode !is Mode.Edit) {
            consume(Action.Navigation.Back)
            return
        }
        val target = if (mode.isCreate) DiscardTarget.POP_SCREEN else DiscardTarget.FLIP_TO_READ
        if (current.hasChanges) {
            updateState { it.copy(dialogState = DialogState.DiscardConfirm(target)) }
        } else {
            applyDiscardTarget(target)
        }
    }

    private fun processEditClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                mode = Mode.Edit(isCreate = false),
                originalSnapshot = State.Snapshot(
                    name = current.name,
                    type = current.type,
                    description = current.description,
                    tagUuids = current.tags.map { it.uuid },
                    adhocPlan = current.adhocPlan,
                ),
            )
        }
    }

    private fun processArchiveClick() {
        val uuid = state.value.uuid ?: return
        val name = state.value.name
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        launch {
            when (val result = interactor.archive(uuid)) {
                ArchiveResult.Success -> {
                    sendEvent(
                        Event.ShowArchiveSuccess(
                            uuid = uuid,
                            message = resourceWrapper.getString(
                                R.string.feature_exercise_detail_archive_success_format,
                                name,
                            ),
                        ),
                    )
                    // launch defaults to defaultDispatcher; navigator must be touched on Main.
                    withContext(mainDispatcher) {
                        consume(Action.Navigation.Back)
                    }
                }

                is ArchiveResult.Blocked -> {
                    val item = BlockedArchiveItem(
                        exerciseName = name,
                        trainingsLabel = resourceWrapper.getString(
                            R.string.feature_exercise_detail_archive_blocked_used_in_format,
                            result.activeTrainings.joinToString(", "),
                        ),
                    )
                    updateStateImmediate {
                        it.copy(dialogState = DialogState.ArchiveBlocked(item = item))
                    }
                }
            }
        }
    }

    private fun processTrackNowClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val exerciseUuid = state.value.uuid ?: return
        launch {
            when (val resolution = interactor.resolveTrackNowConflict()) {
                TrackNowConflict.ProceedFresh -> startFreshTrackNow(exerciseUuid)
                is TrackNowConflict.NeedsUserChoice -> {
                    val sessionLabel = resolution.trainingName ?: resourceWrapper.getString(
                        R.string.feature_exercise_track_now_conflict_unnamed,
                    )
                    val progressLabel = resourceWrapper.getString(
                        R.string.feature_exercise_track_now_conflict_progress_format,
                        0,
                        0,
                    )
                    val sessionUuid = resolution.active.sessionUuid
                    updateStateImmediate {
                        it.copy(
                            dialogState = DialogState.ActiveSessionConflict(
                                sessionUuid = sessionUuid,
                                activeSessionName = sessionLabel,
                                progressLabel = progressLabel,
                            ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun startFreshTrackNow(exerciseUuid: String) {
        val sessionUuid = interactor.startTrackNowSession(
            exerciseUuid = exerciseUuid,
            defaultName = resourceWrapper.getString(R.string.feature_exercise_track_now_default_training_name),
        )
        consumeOnMain(Action.Navigation.OpenLiveWorkout(sessionUuid))
    }

    private fun processTrackNowResumeConfirm() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val info = state.value.dialogState as? DialogState.ActiveSessionConflict ?: return
        updateState { it.copy(dialogState = DialogState.Hidden) }
        consume(Action.Navigation.OpenLiveWorkout(info.sessionUuid))
    }

    private fun processTrackNowDeleteAndStart() {
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        val current = state.value
        val info = current.dialogState as? DialogState.ActiveSessionConflict ?: return
        val exerciseUuid = current.uuid ?: return
        updateState { it.copy(dialogState = DialogState.Hidden) }
        launch {
            interactor.deleteSession(info.sessionUuid)
            startFreshTrackNow(exerciseUuid)
        }
    }

    private fun processHistoryRowClick(action: Action.Click.OnHistoryRowClick) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenSession(action.sessionUuid))
    }

    @Suppress("LongMethod")
    private fun processSaveClick() {
        val current = state.value
        if (current.name.isBlank()) {
            updateState { it.copy(nameError = true) }
            return
        }
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val mode = current.mode
        val isCreate = mode is Mode.Edit && mode.isCreate
        // HandlerStore.launch defaults eachDispatcher to defaultDispatcher, so onSuccess runs
        // on a background thread. Switch to mainDispatcher before consume(Action.Navigation.*)
        // so navigator.popBack() lands on the UI thread.

        val resolvedUuid = Uuid.parseOrRandom(current.uuid)
        launch(
            onSuccess = { outcome ->
                when (outcome) {
                    is SaveOutcome.Success -> handleSaveSuccess(
                        resolvedUuid = outcome.resolvedUuid,
                        isCreate = isCreate,
                        current = current,
                        finalImagePath = outcome.finalImagePath,
                    )

                    SaveOutcome.DuplicateName -> updateStateImmediate {
                        it.copy(nameDuplicateError = true)
                    }

                    SaveOutcome.ImageSaveFailed -> Unit // error toast already emitted.
                }
            },
        ) {
            // Commit pending image first; if it fails, abort the DB write so we never
            // end up with a half-applied save (image referenced from DB but missing on disk).
            val imageOutcome = commitPendingImage(current, resolvedUuid)
            if (imageOutcome is ImageCommitOutcome.Failed) {
                sendEvent(Event.ShowImageError(ImageErrorType.SaveFailed))
                return@launch SaveOutcome.ImageSaveFailed
            }
            val finalImagePath = when (imageOutcome) {
                is ImageCommitOutcome.Stored -> imageOutcome.newPath
                is ImageCommitOutcome.Removed -> null
                ImageCommitOutcome.Unchanged -> current.imagePath
                ImageCommitOutcome.Failed -> error("unreachable")
            }
            val snapshot = ExerciseChangeDomain(
                uuid = resolvedUuid,
                name = current.name.trim(),
                type = current.type.toDomain(),
                description = current.description.takeIf { it.isNotBlank() },
                imagePath = finalImagePath,
                archived = false,
                timestamp = System.currentTimeMillis(),
                labels = current.tags.map { it.name },
                lastAdhocSets = current.adhocPlan?.map { it.toDomain() },
            )
            when (interactor.saveExercise(snapshot)) {
                is SaveResult.Success -> {
                    // Only after the DB row is updated do we delete the previous file —
                    // a process kill between write and DB update leaves an orphaned new file
                    // (better than the reverse, which would leave the DB pointing at nothing).
                    if (imageOutcome is ImageCommitOutcome.Stored) {
                        imageOutcome.previousPath
                            ?.takeIf { it.isNotBlank() && it != imageOutcome.newPath }
                            ?.let { interactor.deleteImageFile(it) }
                    }
                    if (imageOutcome is ImageCommitOutcome.Removed) {
                        imageOutcome.previousPath?.let { interactor.deleteImageFile(it) }
                    }
                    SaveOutcome.Success(
                        resolvedUuid = resolvedUuid.toString(),
                        finalImagePath = finalImagePath,
                    )
                }

                SaveResult.DuplicateName -> SaveOutcome.DuplicateName
            }
        }
    }

    private suspend fun commitPendingImage(
        current: State,
        resolvedUuid: Uuid,
    ): ImageCommitOutcome = when (val pending = current.pendingImage) {
        PendingImage.Unchanged -> ImageCommitOutcome.Unchanged
        PendingImage.RemoveExisting -> ImageCommitOutcome.Removed(previousPath = current.imagePath)
        is PendingImage.NewFromUri -> when (
            val saveResult =
                interactor.saveImage(ImageRef(pending.uri.toString()), resolvedUuid.toString())
        ) {
            is ImageSaveResult.Success -> ImageCommitOutcome.Stored(
                newPath = saveResult.absolutePath,
                previousPath = current.imagePath,
            )

            is ImageSaveResult.Failure -> ImageCommitOutcome.Failed
        }
    }

    private suspend fun handleSaveSuccess(
        resolvedUuid: String,
        isCreate: Boolean,
        current: State,
        finalImagePath: String?,
    ) {
        if (isCreate) {
            withContext(mainDispatcher) {
                consume(Action.Navigation.Back)
            }
        } else {
            val savedSnapshot = State.Snapshot(
                name = current.name.trim(),
                type = current.type,
                description = current.description,
                tagUuids = current.tags.map { it.uuid },
                adhocPlan = current.adhocPlan,
            )
            updateStateImmediate { latest ->
                latest.copy(
                    uuid = resolvedUuid,
                    mode = Mode.Read,
                    originalSnapshot = savedSnapshot,
                    imagePath = finalImagePath,
                    imageLastModified = System.currentTimeMillis(),
                    pendingImage = PendingImage.Unchanged,
                )
            }
        }
    }

    private sealed interface SaveOutcome {

        data class Success(
            val resolvedUuid: String,
            val finalImagePath: String?,
        ) : SaveOutcome

        data object DuplicateName : SaveOutcome

        data object ImageSaveFailed : SaveOutcome
    }

    private sealed interface ImageCommitOutcome {

        data object Unchanged : ImageCommitOutcome

        data class Stored(val newPath: String, val previousPath: String?) : ImageCommitOutcome

        data class Removed(val previousPath: String?) : ImageCommitOutcome

        data object Failed : ImageCommitOutcome
    }

    private fun processCancelClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val current = state.value
        val mode = current.mode
        if (mode !is Mode.Edit) {
            consume(Action.Navigation.Back)
            return
        }
        val target = if (mode.isCreate) DiscardTarget.POP_SCREEN else DiscardTarget.FLIP_TO_READ
        if (current.hasChanges) {
            updateState { it.copy(dialogState = DialogState.DiscardConfirm(target)) }
        } else {
            applyDiscardTarget(target)
        }
    }

    private fun processConfirmDiscard(target: DiscardTarget) {
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        updateState { it.copy(dialogState = DialogState.Hidden) }
        applyDiscardTarget(target)
    }

    private fun processFlipToReadMode() {
        updateState { current ->
            val snapshot = current.originalSnapshot
            if (snapshot == null) {
                current.copy(mode = Mode.Read, pendingImage = PendingImage.Unchanged)
            } else {
                current.copy(
                    mode = Mode.Read,
                    name = snapshot.name,
                    nameError = false,
                    nameDuplicateError = false,
                    type = snapshot.type,
                    description = snapshot.description,
                    tags = current.availableTags
                        .filter { tag -> tag.uuid in snapshot.tagUuids }
                        .toImmutableList(),
                    tagSearchQuery = "",
                    pendingImage = PendingImage.Unchanged,
                )
            }
        }
    }

    private fun applyDiscardTarget(target: DiscardTarget) {
        when (target) {
            DiscardTarget.POP_SCREEN -> consume(Action.Navigation.Back)
            DiscardTarget.FLIP_TO_READ -> processFlipToReadMode()
        }
    }

    private fun processPermanentDeleteMenuClick() {
        val current = state.value
        if (!current.canPermanentlyDelete) return
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        // Pre-resolve display strings outside the updateState lambda — Rule 1 of
        // compose-state-discipline.
        val title = resourceWrapper.getString(
            R.string.feature_exercise_detail_permanent_delete_confirm_title,
            current.name,
        )
        val body = resourceWrapper.getString(
            R.string.feature_exercise_detail_permanent_delete_confirm_body,
        )
        val impactSummary = resourceWrapper.getString(
            R.string.feature_exercise_detail_permanent_delete_confirm_impact,
        )
        val confirmLabel = resourceWrapper.getString(
            R.string.feature_exercise_detail_permanent_delete_confirm_button,
        )
        updateState {
            it.copy(
                dialogState = DialogState.PermanentDeleteConfirm(
                    title = title,
                    body = body,
                    impactSummary = impactSummary,
                    confirmLabel = confirmLabel,
                ),
            )
        }
    }

    private fun processConfirmPermanentDelete() {
        val uuid = state.value.uuid ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        updateState { it.copy(dialogState = DialogState.Hidden) }
        launch(
            onSuccess = {
                sendEvent(
                    Event.ShowPermanentDeleteSuccess(
                        message = resourceWrapper.getString(
                            R.string.feature_exercise_detail_permanent_delete_success,
                        ),
                    ),
                )
                withContext(mainDispatcher) { consume(Action.Navigation.Back) }
            },
        ) {
            interactor.permanentlyDelete(uuid)
        }
    }

    private fun processUndoArchive(action: Action.Click.OnUndoArchive) {
        launch { interactor.restore(action.uuid) }
    }

    private fun processEditPlanClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val current = state.value
        val uuid = current.uuid
        if (uuid != null) {
            // Existing exercise — PlanEditor saves directly to DB on its own Save,
            // round-trips a `planEditorSavedAttr = true` signal, and the parent does a
            // partial reload of (type, adhocPlan).
            consume(Action.Navigation.OpenPlanEditorExisting(exerciseUuid = uuid))
            return
        }
        // No persisted UUID yet — Draft mode. PlanEditor never touches the DB; on Done
        // it pops back with the seed merged into local state, and the parent's own Save
        // is what eventually persists everything to disk.
        val seedJson = current.adhocPlan
            ?.takeIf { it.isNotEmpty() }
            ?.let { plan ->
                // Use the explicit serializer overload so the call resolves to a
                // member function rather than the (deprecated-conflicting)
                // `kotlinx.serialization.encodeToString` extension — see the
                // MemberExtensionConflict lint and issuetracker.google.com/issues/350432371.
                Json.encodeToString(ListSerializer(PlanSetUiModel.serializer()), plan.toList())
            }
        consume(
            Action.Navigation.OpenPlanEditorDraft(
                initialType = current.type,
                initialPlanJson = seedJson,
            ),
        )
    }

    private fun processAdhocPlanEditorAction(action: Action.Click.OnAdhocPlanEditorAction) {
        val current = state.value
        val isWeighted = current.type == ExerciseTypeUiModel.WEIGHTED
        val nextDraft = PlanDraftReducer.reduce(
            draft = current.adhocPlan ?: persistentListOf(),
            action = action.action,
            isWeighted = isWeighted,
        )
        // Empty draft normalizes back to null so `state.adhocPlan == null` continues to
        // mean "no default plan attached" (the persisted shape on `last_adhoc_sets`).
        val nextPlan = nextDraft.takeIf { it.isNotEmpty() }
        val nextSummary = nextPlan.toAdhocPlanSummary(resourceWrapper)
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { latest ->
            latest.copy(
                adhocPlan = nextPlan,
                adhocPlanSummaryLabel = nextSummary,
            )
        }
    }

    private fun processTagToggle(action: Action.Click.OnTagToggle) {
        val current = state.value
        val tag = current.availableTags.firstOrNull { it.uuid == action.tagUuid } ?: return
        val isSelected = current.tags.any { it.uuid == action.tagUuid }
        if (isSelected) {
            sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
            updateState {
                it.copy(
                    tags = it.tags.filterNot { existing -> existing.uuid == action.tagUuid }
                        .toImmutableList(),
                )
            }
        } else {
            if (current.tags.size >= MAX_TAGS_PER_EXERCISE) {
                sendEvent(
                    Event.ShowTagLimitReached(
                        message = resourceWrapper.getString(R.string.feature_exercise_edit_tag_limit),
                    ),
                )
                return
            }
            sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
            updateState { it.copy(tags = (it.tags + tag).toImmutableList()) }
        }
    }

    private fun processTagRemove(action: Action.Click.OnTagRemove) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState {
            it.copy(
                tags = it.tags.filterNot { tag -> tag.uuid == action.tagUuid }
                    .toImmutableList(),
            )
        }
    }

    private fun processTagCreate(action: Action.Click.OnTagCreate) {
        val current = state.value
        if (current.tags.size >= MAX_TAGS_PER_EXERCISE) {
            sendEvent(
                Event.ShowTagLimitReached(
                    message = resourceWrapper.getString(R.string.feature_exercise_edit_tag_limit),
                ),
            )
            return
        }
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        launch(
            onSuccess = { tag ->
                updateStateImmediate { state ->
                    state.copy(
                        tags = (
                            state.tags + TagUiModel(
                                uuid = tag.uuid,
                                name = tag.name,
                            )
                            ).toImmutableList(),
                        tagSearchQuery = "",
                    )
                }
            },
        ) {
            interactor.createTag(action.name.trim())
        }
    }

    private fun processEditImageClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { it.copy(dialogState = DialogState.ImageSourcePicker) }
    }

    private fun processImageThumbnailClick() {
        // Derive the viewer's model arg from whatever is currently displayed — committed
        // file path OR the freshly-picked content URI. ImageDisplay.None means the
        // thumbnail isn't visible anyway, so the click can't physically happen.
        val model = when (val display = state.value.effectiveImageDisplay) {
            is ImageDisplay.FromPath -> display.path
            is ImageDisplay.FromUri -> display.uri.toString()
            ImageDisplay.None -> return
        }
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenImageViewer(model))
    }

    private fun processImageSourceSelected(action: Action.Click.OnImageSourceSelected) {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { it.copy(dialogState = DialogState.Hidden) }
        when (action.source) {
            ImageSourceUiModel.Camera -> {
                if (hasCameraPermission()) {
                    launchCameraCapture()
                } else {
                    sendEvent(Event.NavigateRequestCameraPermission)
                }
            }

            ImageSourceUiModel.Gallery -> sendEvent(Event.NavigateLaunchGallery)
        }
    }

    private fun launchCameraCapture() {
        launch(
            onSuccess = { ref ->
                sendEvent(Event.NavigateLaunchCamera(ref.value.toUri()))
            },
        ) {
            interactor.createTempCaptureRef()
        }
    }

    private fun processRemoveImageClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState {
            it.copy(
                pendingImage = PendingImage.RemoveExisting,
                dialogState = DialogState.Hidden,
            )
        }
    }

    private fun processPrCardClick() {
        val uuid = state.value.uuid ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenChart(uuid))
    }

    private fun processPermissionDeniedSettingsClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { it.copy(dialogState = DialogState.Hidden) }
        sendEvent(Event.NavigateOpenAppSettings(context.packageName))
    }

    private fun processCameraPermissionDenied() {
        updateState { it.copy(dialogState = DialogState.PermissionDenied) }
    }

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val MAX_TAGS_PER_EXERCISE = 10
    }
}
