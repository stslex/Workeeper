// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.handler

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.utils.CommonExt.parseOrRandom
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.toData
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.SaveResult
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.TrackNowConflict
import io.github.stslex.workeeper.feature.exercise.mvi.mapper.toAdhocPlanSummary
import io.github.stslex.workeeper.feature.exercise.mvi.model.ImageDisplay
import io.github.stslex.workeeper.feature.exercise.mvi.model.ImageErrorType
import io.github.stslex.workeeper.feature.exercise.mvi.model.ImageSourceUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.DiscardTarget
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State.ConflictInfo
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State.Mode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
@ViewModelScoped
internal class ClickHandler @Inject constructor(
    private val interactor: ExerciseInteractor,
    private val resourceWrapper: ResourceWrapper,
    @ApplicationContext
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
            Action.Click.OnTrackNowConflictDismiss -> processTrackNowConflictDismiss()
            is Action.Click.OnHistoryRowClick -> processHistoryRowClick(action)
            Action.Click.OnSaveClick -> processSaveClick()
            Action.Click.OnCancelClick -> processCancelClick()
            is Action.Click.OnConfirmDiscard -> processConfirmDiscard(action.target)
            Action.Click.OnDismissDiscard -> processDismissDiscard()
            Action.Click.OnDismissArchiveBlocked -> Unit
            Action.Click.FlipToReadMode -> processFlipToReadMode()
            Action.Click.OnPermanentDeleteMenuClick -> processPermanentDeleteMenuClick()
            Action.Click.OnConfirmPermanentDelete -> processConfirmPermanentDelete()
            Action.Click.OnDismissPermanentDelete -> Unit
            is Action.Click.OnUndoArchive -> processUndoArchive(action)
            is Action.Click.OnTypeSelect -> processTypeSelect(action)
            Action.Click.OnTypeChangeConfirm -> processTypeChangeConfirm()
            Action.Click.OnTypeChangeDismiss -> processTypeChangeDismiss()
            Action.Click.OnEditPlanClick -> processEditPlanClick()
            is Action.Click.OnTagToggle -> processTagToggle(action)
            is Action.Click.OnTagRemove -> processTagRemove(action)
            is Action.Click.OnTagCreate -> processTagCreate(action)
            Action.Click.OnEditImageClick -> processEditImageClick()
            Action.Click.OnImageThumbnailClick -> processImageThumbnailClick()
            is Action.Click.OnImageSourceSelected -> processImageSourceSelected(action)
            Action.Click.OnRemoveImageClick -> processRemoveImageClick()
            Action.Click.OnPrCardClick -> processPrCardClick()
            Action.Click.OnImageSourceDialogDismiss -> processImageSourceDialogDismiss()
            Action.Click.OnPermissionDeniedDialogDismiss -> processPermissionDeniedDialogDismiss()
            Action.Click.OnPermissionDeniedSettingsClick -> processPermissionDeniedSettingsClick()
            Action.Click.RequestCameraCapture -> launchCameraCapture()
            Action.Click.OnCameraPermissionDenied -> processCameraPermissionDenied()
        }
    }

    private fun processBackClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val current = state.value
        // Plan editor is the inner-most surface — its draft takes priority over the
        // form-level dirty check. Same dialog UI as form discard, different commit.
        if (current.isPlanEditorDirty) {
            sendEvent(Event.ShowDiscardConfirmDialog(DiscardTarget.PLAN_EDITOR))
            return
        }
        val mode = current.mode
        if (mode !is Mode.Edit) {
            consume(Action.Navigation.Back)
            return
        }
        val target = if (mode.isCreate) DiscardTarget.POP_SCREEN else DiscardTarget.FLIP_TO_READ
        if (current.hasChanges) {
            sendEvent(Event.ShowDiscardConfirmDialog(target))
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

                is ArchiveResult.Blocked ->
                    sendEvent(
                        Event.ShowArchiveBlocked(
                            body = resourceWrapper.getString(
                                R.string.feature_exercise_detail_archive_blocked_body_format,
                                name,
                                result.activeTrainings.joinToString(", "),
                            ),
                        ),
                    )
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
                    val info = ConflictInfo(
                        sessionUuid = resolution.active.sessionUuid,
                        activeSessionName = resolution.sessionLabel,
                        progressLabel = resourceWrapper.getString(
                            R.string.feature_exercise_track_now_conflict_progress_format,
                            0,
                            0,
                        ),
                    )
                    updateStateImmediate { it.copy(pendingConflict = info) }
                    sendEvent(
                        Event.ShowActiveSessionConflict(
                            activeSessionName = info.activeSessionName,
                            progressLabel = info.progressLabel,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun startFreshTrackNow(exerciseUuid: String) {
        val sessionUuid = interactor.startTrackNowSession(exerciseUuid)
        consumeOnMain(Action.Navigation.OpenLiveWorkout(sessionUuid))
    }

    private fun processTrackNowResumeConfirm() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val info = state.value.pendingConflict ?: return
        updateState { it.copy(pendingConflict = null) }
        consume(Action.Navigation.OpenLiveWorkout(info.sessionUuid))
    }

    private fun processTrackNowDeleteAndStart() {
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        val info = state.value.pendingConflict ?: return
        val exerciseUuid = state.value.uuid ?: return
        updateState { it.copy(pendingConflict = null) }
        launch {
            interactor.deleteSession(info.sessionUuid)
            startFreshTrackNow(exerciseUuid)
        }
    }

    private fun processTrackNowConflictDismiss() {
        updateState { it.copy(pendingConflict = null) }
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
            val snapshot = ExerciseChangeDataModel(
                uuid = resolvedUuid,
                name = current.name.trim(),
                type = current.type.toData(),
                description = current.description.takeIf { it.isNotBlank() },
                imagePath = finalImagePath,
                timestamp = System.currentTimeMillis(),
                labels = current.tags.map { it.name },
                lastAdHocSets = current.adhocPlan?.map { it.toData() },
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
            val saveResult = interactor.saveImage(pending.uri, resolvedUuid.toString())
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
            sendEvent(Event.ShowDiscardConfirmDialog(target))
        } else {
            applyDiscardTarget(target)
        }
    }

    private fun processConfirmDiscard(target: DiscardTarget) {
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        applyDiscardTarget(target)
    }

    private fun processDismissDiscard() = Unit

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
            DiscardTarget.PLAN_EDITOR -> updateState { it.copy(planEditorTarget = null) }
        }
    }

    private fun processPermanentDeleteMenuClick() {
        if (!state.value.canPermanentlyDelete) return
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        sendEvent(
            Event.ShowPermanentDeleteConfirm(
                title = resourceWrapper.getString(
                    R.string.feature_exercise_detail_permanent_delete_confirm_title,
                    state.value.name,
                ),
                body = resourceWrapper.getString(R.string.feature_exercise_detail_permanent_delete_confirm_body),
                impactSummary = resourceWrapper.getString(
                    R.string.feature_exercise_detail_permanent_delete_confirm_impact,
                ),
                confirmLabel = resourceWrapper.getString(
                    R.string.feature_exercise_detail_permanent_delete_confirm_button,
                ),
            ),
        )
    }

    private fun processConfirmPermanentDelete() {
        val uuid = state.value.uuid ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
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

    private fun processTypeSelect(action: Action.Click.OnTypeSelect) {
        val current = state.value
        if (current.type == action.type) return
        // Switching from WEIGHTED to WEIGHTLESS while weighted plan rows exist would
        // silently strand weight data once Live workout pre-fills. Surface a confirm so
        // the user opts in to the multi-row wipe (handled by `processTypeChangeConfirm`).
        val needsWeightWipe = action.type == ExerciseTypeUiModel.WEIGHTLESS &&
            current.type == ExerciseTypeUiModel.WEIGHTED &&
            (current.adhocPlan?.any { it.weight != null } == true)
        if (needsWeightWipe) {
            sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
            updateState { it.copy(pendingTypeChange = action.type) }
            sendEvent(
                Event.ShowTypeChangeConfirm(
                    title = resourceWrapper.getString(
                        R.string.feature_exercise_edit_type_change_weightless_title,
                    ),
                    body = resourceWrapper.getString(
                        R.string.feature_exercise_edit_type_change_weightless_body,
                    ),
                    impactSummary = resourceWrapper.getString(
                        R.string.feature_exercise_edit_type_change_weightless_impact,
                    ),
                    confirmLabel = resourceWrapper.getString(
                        R.string.feature_exercise_edit_type_change_weightless_confirm,
                    ),
                ),
            )
            return
        }
        sendEvent(Event.Haptic(HapticFeedbackType.SegmentTick))
        updateState { it.copy(type = action.type) }
    }

    private fun processTypeChangeConfirm() {
        val current = state.value
        val pending = current.pendingTypeChange ?: return
        val uuid = current.uuid
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        updateState { latest ->
            val nextPlan = latest.adhocPlan?.map { it.copy(weight = null) }?.toImmutableList()
            latest.copy(
                type = pending,
                pendingTypeChange = null,
                adhocPlan = nextPlan,
                adhocPlanSummaryLabel = nextPlan.toAdhocPlanSummary(resourceWrapper),
            )
        }
        if (uuid == null) return
        launch(
            onSuccess = { Unit },
        ) {
            interactor.clearWeightsFromAllPlansForExercise(uuid)
        }
    }

    private fun processTypeChangeDismiss() {
        updateState { it.copy(pendingTypeChange = null) }
    }

    private fun processEditPlanClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        val initial = state.value.adhocPlan ?: persistentListOf()
        updateState {
            it.copy(
                planEditorTarget = State.PlanEditorTarget(
                    initialPlan = initial,
                    draft = initial,
                ),
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
        updateState { it.copy(sourceDialogVisible = true) }
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
        updateState { it.copy(sourceDialogVisible = false) }
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
            onSuccess = { tempUri ->
                sendEvent(Event.NavigateLaunchCamera(tempUri))
            },
        ) {
            interactor.createTempCaptureUri()
        }
    }

    private fun processRemoveImageClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState {
            it.copy(
                pendingImage = PendingImage.RemoveExisting,
                sourceDialogVisible = false,
            )
        }
    }

    private fun processImageSourceDialogDismiss() {
        updateState { it.copy(sourceDialogVisible = false) }
    }

    private fun processPrCardClick() {
        val uuid = state.value.uuid ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenChart(uuid))
    }

    private fun processPermissionDeniedDialogDismiss() {
        updateState { it.copy(permissionDeniedDialogVisible = false) }
    }

    private fun processPermissionDeniedSettingsClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { it.copy(permissionDeniedDialogVisible = false) }
        sendEvent(Event.NavigateOpenAppSettings(context.packageName))
    }

    private fun processCameraPermissionDenied() {
        updateState { it.copy(permissionDeniedDialogVisible = true) }
    }

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val MAX_TAGS_PER_EXERCISE = 10
    }
}
