// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.store

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.plan_editor.model.AppPlanEditorAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.model.HistoryUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.model.ImageDisplay
import io.github.stslex.workeeper.feature.exercise.mvi.model.ImageErrorType
import io.github.stslex.workeeper.feature.exercise.mvi.model.ImageSourceUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.mvi.model.PersonalRecordUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal interface ExerciseStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val uuid: String?,
        val mode: Mode,
        val name: String,
        val nameError: Boolean,
        val nameDuplicateError: Boolean,
        val type: ExerciseTypeUiModel,
        val description: String,
        val tags: ImmutableList<TagUiModel>,
        val availableTags: ImmutableList<TagUiModel>,
        val tagSearchQuery: String,
        val recentHistory: ImmutableList<HistoryUiModel>,
        val originalSnapshot: Snapshot?,
        val isLoading: Boolean,
        val canPermanentlyDelete: Boolean,
        val adhocPlan: ImmutableList<PlanSetUiModel>?,
        val adhocPlanSummaryLabel: String,
        val planEditorTarget: PlanEditorTarget?,
        val pendingTypeChange: ExerciseTypeUiModel?,
        val imagePath: String?,
        val imageLastModified: Long,
        val pendingImage: PendingImage,
        val sourceDialogVisible: Boolean,
        val permissionDeniedDialogVisible: Boolean,
        val pendingConflict: ConflictInfo?,
        val personalRecord: PersonalRecordUiModel?,
    ) : Store.State {

        val isSaveEnabled: Boolean
            get() = name.isNotBlank()

        /**
         * Read-mode default plan surface visibility — drives the small "Default plan" card
         * between the description block and HistorySection in [io.github.stslex.workeeper
         * .feature.exercise.ui.ExerciseDetailScreen]. Edit mode renders the inline editor
         * row instead, so the read-mode card stays hidden.
         */
        val planSummaryVisible: Boolean
            get() = mode is Mode.Read && !adhocPlan.isNullOrEmpty()

        val hasChanges: Boolean
            get() = originalSnapshot?.matches(this) == false || isImageDirty

        val isPlanEditorDirty: Boolean
            get() = planEditorTarget?.let { it.draft != it.initialPlan } == true

        val isImageDirty: Boolean
            get() = pendingImage != PendingImage.Unchanged

        /** What the UI should display right now — pending overrides committed. */
        val effectiveImageDisplay: ImageDisplay
            get() = when (val pending = pendingImage) {
                is PendingImage.NewFromUri -> ImageDisplay.FromUri(pending.uri)
                PendingImage.RemoveExisting -> ImageDisplay.None
                PendingImage.Unchanged -> when (val path = imagePath) {
                    null -> ImageDisplay.None
                    else -> ImageDisplay.FromPath(path, lastModified = imageLastModified)
                }
            }

        /**
         * True only when the system back gesture must surface the discard-changes dialog,
         * or when Edit on an existing exercise must flip back to Read instead of popping.
         * When false, BackHandler stays unsubscribed so Compose nav handles the gesture
         * natively (including the Android 13+ predictive-back preview animation).
         */
        val interceptBack: Boolean
            get() = (mode is Mode.Edit && (hasChanges || !mode.isCreate)) || isPlanEditorDirty

        @Stable
        sealed interface Mode {

            data object Read : Mode

            data class Edit(val isCreate: Boolean) : Mode
        }

        @Stable
        data class Snapshot(
            val name: String,
            val type: ExerciseTypeUiModel,
            val description: String,
            val tagUuids: List<String>,
        ) {

            fun matches(state: State): Boolean = state.name == name &&
                state.type == type &&
                state.description == description &&
                state.tags.map { it.uuid } == tagUuids
        }

        @Stable
        data class PlanEditorTarget(
            /** Snapshot of the plan when the editor opened — used for dirty detection. */
            val initialPlan: ImmutableList<PlanSetUiModel>,
            /** Live draft updated on every editor field change. */
            val draft: ImmutableList<PlanSetUiModel>,
        )

        /**
         * Snapshot of the active session that conflicts with the user's Track now request.
         * Carried in State so the modal can survive configuration changes without
         * re-fetching from the repository.
         */
        @Stable
        data class ConflictInfo(
            val sessionUuid: String,
            val activeSessionName: String,
            val progressLabel: String,
        )

        companion object {

            fun create(uuid: String?): State = State(
                uuid = uuid,
                mode = if (uuid == null) Mode.Edit(isCreate = true) else Mode.Read,
                name = "",
                nameError = false,
                nameDuplicateError = false,
                type = ExerciseTypeUiModel.WEIGHTED,
                description = "",
                tags = persistentListOf(),
                availableTags = persistentListOf(),
                tagSearchQuery = "",
                recentHistory = persistentListOf(),
                originalSnapshot = null,
                isLoading = uuid != null,
                canPermanentlyDelete = false,
                adhocPlan = null,
                adhocPlanSummaryLabel = "",
                planEditorTarget = null,
                pendingTypeChange = null,
                imagePath = null,
                imageLastModified = 0L,
                pendingImage = PendingImage.Unchanged,
                sourceDialogVisible = false,
                permissionDeniedDialogVisible = false,
                pendingConflict = null,
                personalRecord = null,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Common : Action {

            data object Init : Common

            data class ImagePicked(val uri: Uri) : Common

            data object ImagePickCancelled : Common
        }

        sealed interface Click : Action {

            data object OnBackClick : Click

            data object OnEditClick : Click

            data object OnArchiveMenuClick : Click

            data object OnTrackNowClick : Click

            data object OnTrackNowResumeConfirm : Click

            data object OnTrackNowDeleteAndStart : Click

            data object OnTrackNowConflictDismiss : Click

            data class OnHistoryRowClick(val sessionUuid: String) : Click

            data object OnSaveClick : Click

            data object OnCancelClick : Click

            data class OnConfirmDiscard(val target: DiscardTarget) : Click

            data object OnDismissDiscard : Click

            data object OnDismissArchiveBlocked : Click

            data object FlipToReadMode : Click

            data object OnPermanentDeleteMenuClick : Click

            data object OnConfirmPermanentDelete : Click

            data object OnDismissPermanentDelete : Click

            data class OnUndoArchive(val uuid: String) : Click

            data class OnTypeSelect(val type: ExerciseTypeUiModel) : Click

            data object OnTypeChangeConfirm : Click

            data object OnTypeChangeDismiss : Click

            data object OnEditPlanClick : Click

            data class OnTagToggle(val tagUuid: String) : Click

            data class OnTagRemove(val tagUuid: String) : Click

            data class OnTagCreate(val name: String) : Click

            data object OnEditImageClick : Click

            data object OnImageThumbnailClick : Click

            data class OnImageSourceSelected(val source: ImageSourceUiModel) : Click

            data object OnRemoveImageClick : Click

            data object OnPrCardClick : Click

            data object OnImageSourceDialogDismiss : Click

            data object OnPermissionDeniedDialogDismiss : Click

            data object OnPermissionDeniedSettingsClick : Click

            /** Internal: emitted by the camera-permission launcher when granted. */
            data object RequestCameraCapture : Click

            /** Internal: emitted by the camera-permission launcher when denied. */
            data object OnCameraPermissionDenied : Click
        }

        @Suppress("MviActionNamingRule")
        data class PlanEditorAction(val action: AppPlanEditorAction) : Action

        sealed interface Input : Action {

            data class OnNameChange(val value: String) : Input

            data class OnDescriptionChange(val value: String) : Input

            data class OnTagSearchChange(val value: String) : Input
        }

        sealed interface Navigation : Action {

            data object Back : Navigation

            data class OpenSession(val sessionUuid: String) : Navigation

            data class OpenLiveWorkout(val sessionUuid: String) : Navigation

            data class OpenImageViewer(val model: String) : Navigation

            data class OpenChart(val exerciseUuid: String) : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class Haptic(val type: HapticFeedbackType) : Event

        data class ShowArchiveSuccess(val uuid: String, val message: String) : Event

        data class ShowArchiveBlocked(val body: String) : Event

        data class ShowTagLimitReached(val message: String) : Event

        data class ShowActiveSessionConflict(
            val activeSessionName: String,
            val progressLabel: String,
        ) : Event

        data class ShowDiscardConfirmDialog(val target: DiscardTarget) : Event

        data class ShowPermanentDeleteConfirm(
            val title: String,
            val body: String,
            val impactSummary: String,
            val confirmLabel: String,
        ) : Event

        data class ShowPermanentDeleteSuccess(val message: String) : Event

        data class ShowTypeChangeConfirm(
            val title: String,
            val body: String,
            val impactSummary: String,
            val confirmLabel: String,
        ) : Event

        data class NavigateLaunchCamera(val tempUri: Uri) : Event

        data object NavigateLaunchGallery : Event

        data object NavigateRequestCameraPermission : Event

        data class NavigateOpenAppSettings(val packageName: String) : Event

        data class ShowImageError(val errorType: ImageErrorType) : Event
    }

    /**
     * Where the user is heading after confirming a discard. Lets the dialog reuse a single
     * surface for the form-level (POP_SCREEN/FLIP_TO_READ) and plan-editor (PLAN_EDITOR)
     * scopes — same dialog, different commit semantics.
     */
    @Stable
    enum class DiscardTarget { POP_SCREEN, FLIP_TO_READ, PLAN_EDITOR }
}
