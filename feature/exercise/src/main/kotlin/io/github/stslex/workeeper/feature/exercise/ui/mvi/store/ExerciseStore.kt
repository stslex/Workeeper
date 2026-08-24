// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.HistoryUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageErrorType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageSourceUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PersonalRecordUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

interface ExerciseStore : Store<State, Action, Event> {

    @Stable
    data class State(
        val uuid: String?,
        val mode: Mode,
        /** Which edit session the draft belongs to — bumped on each [Mode.Edit] entry. */
        val draftEpoch: Int,
        /** A save's write is in flight: the draft refuses undo and discard until it lands. */
        val isSaving: Boolean,
        val name: String,
        val nameError: Boolean,
        val nameDuplicateError: Boolean,
        val type: ExerciseTypeUiModel,
        val description: String,
        val tags: ImmutableList<AppTagItem>,
        val availableTags: ImmutableList<AppTagItem>,
        val tagSearchQuery: String,
        val recentHistory: ImmutableList<HistoryUiModel>,
        /** Total finished sessions containing this exercise — the История head's count. */
        val historyCount: Int,
        val originalSnapshot: Snapshot?,
        val isLoading: Boolean,
        val canPermanentlyDelete: Boolean,
        val adhocPlan: ImmutableList<PlanSetUiModel>?,
        /** Target of an in-flight WEIGHTED -> WEIGHTLESS switch awaiting its confirm. */
        val pendingTypeChange: ExerciseTypeUiModel?,
        val imagePath: String?,
        val imageLastModified: Long,
        val pendingImage: PendingImage,
        val dialogState: DialogState,
        val bottomSheetState: BottomSheetState,
        val personalRecord: PersonalRecordUiModel?,
    ) : Store.State {

        // GUARD: no save-enabled predicate here — gating Save on `name` makes `nameError`
        // unreachable (§26, "Save is never disabled").

        val hasChanges: Boolean
            get() = originalSnapshot?.matches(this) == false || isImageDirty || isAdhocPlanDirty

        val isImageDirty: Boolean
            get() = pendingImage != PendingImage.Unchanged

        /**
         * Dirty check for the ad-hoc plan; needed in create mode, where [originalSnapshot] is
         * null until the first save. That snapshot is the only baseline; do not add a second.
         */
        val isAdhocPlanDirty: Boolean
            get() = (adhocPlan ?: persistentListOf<PlanSetUiModel>()) !=
                (originalSnapshot?.adhocPlan ?: persistentListOf<PlanSetUiModel>())

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

        /** Back must raise the discard dialog, close an open dialog, or flip Edit → Read. */
        val interceptBack: Boolean
            get() = (mode is Mode.Edit && (hasChanges || !mode.isCreate)) ||
                dialogState !is DialogState.Hidden

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
            val adhocPlan: ImmutableList<PlanSetUiModel>?,
        ) {

            fun matches(state: State): Boolean = state.name == name &&
                state.type == type &&
                state.description == description &&
                state.tags.map { it.uuid } == tagUuids &&
                normalizePlan(state.adhocPlan) == normalizePlan(adhocPlan)

            private companion object {

                /** `null` and an empty list both mean "no plan attached". */
                fun normalizePlan(
                    plan: ImmutableList<PlanSetUiModel>?,
                ): ImmutableList<PlanSetUiModel> = plan ?: persistentListOf()
            }
        }

        companion object {

            /** Enforced by `ClickHandler`, displayed by the ТЕГИ head's `N из 10` counter. */
            const val MAX_TAGS_PER_EXERCISE: Int = 10

            fun create(uuid: String?): State = State(
                uuid = uuid,
                mode = if (uuid == null) Mode.Edit(isCreate = true) else Mode.Read,
                draftEpoch = 0,
                isSaving = false,
                name = "",
                nameError = false,
                nameDuplicateError = false,
                type = ExerciseTypeUiModel.WEIGHTED,
                description = "",
                tags = persistentListOf(),
                availableTags = persistentListOf(),
                tagSearchQuery = "",
                recentHistory = persistentListOf(),
                historyCount = 0,
                originalSnapshot = null,
                isLoading = uuid != null,
                canPermanentlyDelete = false,
                adhocPlan = null,
                pendingTypeChange = null,
                imagePath = null,
                imageLastModified = 0L,
                pendingImage = PendingImage.Unchanged,
                dialogState = DialogState.Hidden,
                bottomSheetState = BottomSheetState.Hidden,
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

            /**
             * A request popped by the image viewer: [request] names a
             * [Screen.ExerciseImageRequest]; an unrecognised name is ignored.
             */
            data class ImageRequestReceived(val request: String) : Common
        }

        sealed interface Click : Action {

            data object OnBackClick : Click

            data object OnEditClick : Click

            /** Topbar `⋮` — opens the [BottomSheetState.DetailMenu] sheet. */
            data object OnDetailMenuClick : Click

            data object OnSheetDismiss : Click

            data object OnArchiveMenuClick : Click

            data object OnTrackNowClick : Click

            data object OnTrackNowResumeConfirm : Click

            data object OnTrackNowDeleteAndStart : Click

            data object OnTrackNowConflictDismiss : Click

            data class OnHistoryRowClick(val sessionUuid: String) : Click

            /** The history record row's PR tag — opens the explainer dialog. */
            data object OnHistoryPrTagClick : Click

            data object OnPrExplainerDismiss : Click

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

            /** The plan head's `(i)` — opens the [BottomSheetState.PlanInfo] sheet (ED8). */
            data object OnPlanInfoClick : Click

            /** Mutates the in-memory ad-hoc plan; nothing is persisted until Save (ED1). */
            @Suppress("MviActionNamingRule")
            data class OnAdhocPlanEditorAction(
                val action: PlanEditorBodyAction,
            ) : Click

            /** The inline plan editor's WEIGHTED / WEIGHTLESS toggle. */
            data class OnTypeToggle(val value: ExerciseTypeUiModel) : Click

            /** Commit a switch that the weight-wipe confirm asked about. */
            data object OnTypeChangeConfirm : Click

            /** Dismiss the weight-wipe confirm, leaving the type as it was. */
            data object OnTypeChangeDismiss : Click

            /** The form's dashed «+ тег» chip — opens the [BottomSheetState.TagPicker] sheet. */
            data object OnTagAddClick : Click

            /** «Готово», the scrim or the drag — selection already applied live (ED7). */
            data object OnTagPickerDismiss : Click

            /** «Отменить» on the set-removed toast: put [set] back at [index] in the draft. */
            data class OnUndoSetRemove(
                val set: PlanSetUiModel,
                val index: Int,
                val draftEpoch: Int,
            ) : Click

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

        sealed interface Input : Action {

            data class OnNameChange(val value: String) : Input

            data class OnDescriptionChange(val value: String) : Input

            data class OnTagSearchChange(val value: String) : Input
        }

        sealed interface Navigation : Action {

            data object Back : Navigation

            data class OpenSession(val sessionUuid: String) : Navigation

            data class OpenLiveWorkout(val sessionUuid: String) : Navigation

            /** [editable] states whether this caller can honour a replace/remove request. */
            data class OpenImageViewer(val model: String, val editable: Boolean) : Navigation

            data class OpenChart(val exerciseUuid: String) : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class Haptic(val type: HapticFeedbackType) : Event

        data class ShowArchiveSuccess(val uuid: String, val message: String) : Event

        data class ShowTagLimitReached(val message: String) : Event

        /** Draft-only undo — [set] goes back at [index]; item-wise so queued toasts compose. */
        data class ShowSetRemovedUndo(
            val message: String,
            val set: PlanSetUiModel,
            val index: Int,
            /** [State.draftEpoch] at removal — the undo applies only to the same draft. */
            val draftEpoch: Int,
        ) : Event

        data class NavigateLaunchCamera(val tempUri: Uri) : Event

        data object NavigateLaunchGallery : Event

        data object NavigateRequestCameraPermission : Event

        data class NavigateOpenAppSettings(val packageName: String) : Event

        data class ShowImageError(val errorType: ImageErrorType) : Event
    }

    /** Where a confirmed discard goes: pop the screen (create) or flip back to Read. */
    @Stable
    enum class DiscardTarget { POP_SCREEN, FLIP_TO_READ }
}
