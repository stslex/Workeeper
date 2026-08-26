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
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_toast_undo
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.domain.PlanDraftReducer
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseChangeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.SaveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.TrackNowConflict
import io.github.stslex.workeeper.feature.exercise.ui.mvi.mapper.ExerciseUiMapper.toDomain
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageErrorType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageSourceUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.DiscardTarget
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import kotlin.uuid.Uuid
import io.github.stslex.workeeper.core.ui.plan_editor.R as CoreEditorR

@Suppress("TooManyFunctions", "LargeClass")
@SingleIn(ExerciseScope::class)
internal class ClickHandler @Inject constructor(
    private val interactor: ExerciseInteractor,
    private val resourceWrapper: ResourceWrapper,
    // Plain Context: the app graph binds it bare — one Context per graph.
    private val context: Context,
    @MainImmediateDispatcher
    private val mainDispatcher: CoroutineDispatcher,
    store: ExerciseHandlerStore,
) : Handler<Action.Click>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnBackClick -> processBackClick()
            Action.Click.OnEditClick -> processEditClick()
            Action.Click.OnDetailMenuClick -> processDetailMenuClick()
            Action.Click.OnSheetDismiss -> processSheetDismiss()
            Action.Click.OnArchiveMenuClick -> processArchiveClick()
            Action.Click.OnTrackNowClick -> processTrackNowClick()
            Action.Click.OnTrackNowResumeConfirm -> processTrackNowResumeConfirm()
            Action.Click.OnTrackNowDeleteAndStart -> processTrackNowDeleteAndStart()
            Action.Click.OnTrackNowConflictDismiss -> processCloseDialog()
            is Action.Click.OnHistoryRowClick -> processHistoryRowClick(action)
            Action.Click.OnHistoryPrTagClick -> processHistoryPrTagClick()
            Action.Click.OnPrExplainerDismiss -> processCloseDialog()
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
            Action.Click.OnPlanInfoClick -> processPlanInfoClick()
            is Action.Click.OnTypeToggle -> processTypeToggle(action.value)
            Action.Click.OnTypeChangeConfirm -> processTypeChangeConfirm()
            Action.Click.OnTypeChangeDismiss -> processTypeChangeDismiss()
            is Action.Click.OnAdhocPlanEditorAction -> processAdhocPlanEditorAction(action)
            is Action.Click.OnUndoSetRemove -> processUndoSetRemove(action)
            Action.Click.OnTagAddClick -> processTagAddClick()
            Action.Click.OnTagPickerDismiss -> processTagPickerDismiss()
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

    private fun processDetailMenuClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { it.copy(bottomSheetState = BottomSheetState.DetailMenu) }
    }

    private fun processSheetDismiss() {
        updateState { it.copy(bottomSheetState = BottomSheetState.Hidden) }
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
        // The write is in flight: no rollback now, and a create's POP_SCREEN would double-pop.
        if (current.isSaving) return
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
                // A new draft: the previous draft's undo toasts must not edit this one.
                draftEpoch = current.draftEpoch + 1,
                isSaving = false,
                bottomSheetState = BottomSheetState.Hidden,
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
        // Close the sheet before the result lands so a Blocked dialog never stacks on it.
        updateState { it.copy(bottomSheetState = BottomSheetState.Hidden) }
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
                        resolution.doneCount,
                        resolution.totalCount,
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

    private fun processHistoryPrTagClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { it.copy(dialogState = DialogState.PrExplainer) }
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

        val resolvedUuid = Uuid.parseOrRandom(current.uuid)
        // Snapshot captured: until an outcome lands an undo would miss the write.
        updateState { it.copy(isSaving = true) }
        launch(
            onError = {
                // The draft is still alive — its undos re-arm with it.
                updateStateImmediate { latest -> latest.copy(isSaving = false) }
            },
            onSuccess = { outcome ->
                when (outcome) {
                    is SaveOutcome.Success -> handleSaveSuccess(
                        resolvedUuid = outcome.resolvedUuid,
                        isCreate = isCreate,
                        current = current,
                        finalImagePath = outcome.finalImagePath,
                    )

                    SaveOutcome.DuplicateName -> updateStateImmediate {
                        it.copy(nameDuplicateError = true, isSaving = false)
                    }

                    // Error toast already emitted; the draft stays in Edit, undos re-arm.
                    SaveOutcome.ImageSaveFailed -> updateStateImmediate {
                        it.copy(isSaving = false)
                    }
                }
            },
        ) {
            // Commit the image first; a failure aborts the DB write, so no half-applied save.
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
                    // Delete the previous file only after the DB row is updated.
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
                    isSaving = false,
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
        // Same in-flight guard as processBackClick — the two entries raise one sheet.
        if (current.isSaving) return
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
        // Guarded here too: a save dispatched after the sheet rose would land on the rollback.
        if (state.value.isSaving) return
        applyDiscardTarget(target)
    }

    /** Restores every snapshot field, plan included — a plan edit is what raises the sheet. */
    private fun processFlipToReadMode() {
        // Choke point for every route back to Read that bypasses the save's own outcome.
        if (state.value.isSaving) return
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
                    adhocPlan = snapshot.adhocPlan,
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
        // Strings resolved outside `updateState` — Rule 1 of compose-state-discipline.
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
                bottomSheetState = BottomSheetState.Hidden,
            )
        }
    }

    /** The deferred delete (ED11): the commit is the snackbar's `onDismissed`, run on dismiss. */
    private fun processConfirmPermanentDelete() {
        val uuid = state.value.uuid ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        updateState { it.copy(dialogState = DialogState.Hidden) }
        val message = resourceWrapper.getString(
            R.string.feature_exercise_detail_permanent_delete_success,
        )
        // GUARD: main-immediate work dispatcher, so the coroutine starts inside this click's
        // call stack, and NonCancellable around the registration, so the pop this flow itself
        // triggers cannot separate the already-closed dialog from the queued undo window.
        // Back is consumed only after the snackbar is queued.
        launch(
            workDispatcher = mainDispatcher,
            onSuccess = { consume(Action.Navigation.Back) },
        ) {
            withContext(NonCancellable) {
                SnackbarManager.showSnackbar(
                    AppSnackbarModel(
                        message = message,
                        actionLabel = getString(Res.string.core_ui_kit_toast_undo),
                        // «Отменить» declines a delete that has not run — declining is doing nothing.
                        action = { },
                        // GUARD: the delete runs only here, never before the undo window closes; the
                        // closure captures the interactor, never the Store, whose scope dies on the pop.
                        onDismissed = { interactor.permanentlyDelete(uuid) },
                    ),
                )
            }
        }
    }

    private fun processUndoArchive(action: Action.Click.OnUndoArchive) {
        launch { interactor.restore(action.uuid) }
    }

    private fun processPlanInfoClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { it.copy(bottomSheetState = BottomSheetState.PlanInfo) }
    }

    /** Asks before a WEIGHTED -> WEIGHTLESS switch strands typed weights; the wipe is local. */
    private fun processTypeToggle(target: ExerciseTypeUiModel) {
        val current = state.value
        if (current.type == target) return
        val needsWeightWipe = target == ExerciseTypeUiModel.WEIGHTLESS &&
            current.type == ExerciseTypeUiModel.WEIGHTED &&
            current.adhocPlan?.any { it.weight != null } == true
        if (needsWeightWipe) {
            sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
            // Strings resolved outside `updateState` — Rule 1 of compose-state-discipline.
            val title = resourceWrapper.getString(
                CoreEditorR.string.core_ui_plan_editor_type_change_weightless_title,
            )
            val body = resourceWrapper.getString(
                CoreEditorR.string.core_ui_plan_editor_type_change_weightless_body,
            )
            val impact = resourceWrapper.getString(
                CoreEditorR.string.core_ui_plan_editor_type_change_weightless_impact,
            )
            val confirmLabel = resourceWrapper.getString(
                CoreEditorR.string.core_ui_plan_editor_type_change_weightless_confirm,
            )
            updateState {
                it.copy(
                    pendingTypeChange = target,
                    dialogState = DialogState.TypeChangeConfirm(
                        title = title,
                        body = body,
                        impactSummary = impact,
                        confirmLabel = confirmLabel,
                    ),
                )
            }
            return
        }
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { it.copy(type = target) }
    }

    private fun processTypeChangeConfirm() {
        val pending = state.value.pendingTypeChange ?: return
        sendEvent(Event.Haptic(HapticFeedbackType.LongPress))
        updateState { latest ->
            latest.copy(
                type = pending,
                pendingTypeChange = null,
                dialogState = DialogState.Hidden,
                adhocPlan = latest.adhocPlan?.map { it.copy(weight = null) }?.toImmutableList(),
            )
        }
    }

    private fun processTypeChangeDismiss() {
        updateState { it.copy(pendingTypeChange = null, dialogState = DialogState.Hidden) }
    }

    private fun processAdhocPlanEditorAction(action: Action.Click.OnAdhocPlanEditorAction) {
        val current = state.value
        val isWeighted = current.type == ExerciseTypeUiModel.WEIGHTED
        val draft = current.adhocPlan ?: persistentListOf()
        val nextDraft = PlanDraftReducer.reduce(
            draft = draft,
            action = action.action,
            isWeighted = isWeighted,
        )
        // An empty draft normalizes back to null — the persisted shape of `last_adhoc_sets`.
        val nextPlan = nextDraft.takeIf { it.isNotEmpty() }
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { latest -> latest.copy(adhocPlan = nextPlan) }
        // `− подход` is a draft edit: the undo re-inserts the row, nothing waits on a timer.
        val bodyAction = action.action
        if (bodyAction is PlanEditorBodyAction.OnSetRemove && nextDraft.size < draft.size) {
            sendEvent(
                Event.ShowSetRemovedUndo(
                    message = resourceWrapper.getString(
                        CoreEditorR.string.core_ui_plan_editor_toast_set_removed,
                    ),
                    set = draft[bodyAction.index],
                    index = bodyAction.index,
                    draftEpoch = current.draftEpoch,
                ),
            )
        }
    }

    /** The set-removed toast's «Отменить»: the draft takes the row back where it was. */
    private fun processUndoSetRemove(action: Action.Click.OnUndoSetRemove) {
        updateState { latest ->
            // A stale «Отменить» — wrong draft, or a save in flight — edits nothing.
            if (latest.isSaving ||
                latest.mode !is Mode.Edit ||
                action.draftEpoch != latest.draftEpoch
            ) {
                return@updateState latest
            }
            // The row was out riding its toast, so it re-enters the weightless invariant here.
            val restored = if (latest.type == ExerciseTypeUiModel.WEIGHTLESS) {
                action.set.copy(weight = null)
            } else {
                action.set
            }
            val draft = latest.adhocPlan ?: persistentListOf()
            val at = action.index.coerceIn(0, draft.size)
            latest.copy(
                adhocPlan = draft.toMutableList()
                    .apply { add(at, restored) }
                    .toImmutableList(),
            )
        }
    }

    private fun processTagAddClick() {
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        updateState { it.copy(bottomSheetState = BottomSheetState.TagPicker) }
    }

    /** The query clears with the sheet, so reopening starts from the whole dictionary. */
    private fun processTagPickerDismiss() {
        updateState {
            it.copy(
                bottomSheetState = BottomSheetState.Hidden,
                tagSearchQuery = "",
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
            if (current.tags.size >= State.MAX_TAGS_PER_EXERCISE) {
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
        if (current.tags.size >= State.MAX_TAGS_PER_EXERCISE) {
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
                    // createTag returns the EXISTING row for a known name — chip it once.
                    val alreadySelected = state.tags.any { it.uuid == tag.uuid }
                    // The cap check above ran at dispatch; re-check where the chip lands.
                    val overCap = state.tags.size >= State.MAX_TAGS_PER_EXERCISE
                    state.copy(
                        tags = if (alreadySelected || overCap) {
                            state.tags
                        } else {
                            (
                                state.tags + AppTagItem(
                                    uuid = tag.uuid,
                                    name = tag.name,
                                )
                                ).toImmutableList()
                        },
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
        // Whatever is displayed: the committed file path or the freshly-picked content URI.
        val model = when (val display = state.value.effectiveImageDisplay) {
            is ImageDisplay.FromPath -> display.path
            is ImageDisplay.FromUri -> display.uri.toString()
            ImageDisplay.None -> return
        }
        sendEvent(Event.Haptic(HapticFeedbackType.ContextClick))
        // Only Edit can honour replace/remove; the viewer hides both verbs when this is false.
        consume(
            Action.Navigation.OpenImageViewer(
                model = model,
                editable = state.value.mode is Mode.Edit,
            ),
        )
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
}
