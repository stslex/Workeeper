// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.HistoryUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageErrorType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageSourceUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PersonalRecordUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.TagUiModel
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
        val name: String,
        val nameError: Boolean,
        val nameDuplicateError: Boolean,
        val type: ExerciseTypeUiModel,
        val description: String,
        val tags: ImmutableList<TagUiModel>,
        val availableTags: ImmutableList<TagUiModel>,
        val tagSearchQuery: String,
        val recentHistory: ImmutableList<HistoryUiModel>,
        /** Total finished sessions containing this exercise — the История head's count. */
        val historyCount: Int,
        val originalSnapshot: Snapshot?,
        val isLoading: Boolean,
        val canPermanentlyDelete: Boolean,
        val adhocPlan: ImmutableList<PlanSetUiModel>?,
        val adhocPlanSummaryLabel: String,
        /** Target of an in-flight WEIGHTED -> WEIGHTLESS switch awaiting its confirm. */
        val pendingTypeChange: ExerciseTypeUiModel?,
        val imagePath: String?,
        val imageLastModified: Long,
        val pendingImage: PendingImage,
        val dialogState: DialogState,
        val bottomSheetState: BottomSheetState,
        val personalRecord: PersonalRecordUiModel?,
    ) : Store.State {

        // NO SAVE-ENABLED PREDICATE HERE, and none may be added: gating Save on `name` is
        // gating it on the exact condition that produces `nameError`, which makes that error
        // unreachable (§26, "Save is never disabled").

        val hasChanges: Boolean
            get() = originalSnapshot?.matches(this) == false || isImageDirty || isAdhocPlanDirty

        val isImageDirty: Boolean
            get() = pendingImage != PendingImage.Unchanged

        /**
         * Compares the working ad-hoc plan against the baseline. It exists for CREATE mode, where
         * [originalSnapshot] is null until the first save and the first term of [hasChanges] is
         * therefore false by construction — without it, a plan built in the create flow would be
         * silently discarded by Cancel. A null snapshot reads as "no plan yet", which is exactly
         * the comparison create mode needs.
         *
         * **The baseline is [originalSnapshot] and there must not be a second one.** Two baselines
         * for one value need every writer to keep both in step, and every writer will not: §25
         * **B39** is what that costs here. Asserted both ways in `ExerciseDirtyStateTest`.
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

        /**
         * True when the system back gesture must surface the discard-changes dialog,
         * close an open dialog before propagating, or flip an existing exercise's Edit
         * mode back to Read instead of popping. When false, BackHandler stays
         * unsubscribed so Compose nav handles the gesture natively (including the
         * Android 13+ predictive-back preview animation).
         */
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

                /**
                 * Treat `null` and an empty list as equal — both mean "no plan attached".
                 * Without this, an in-flight edit that toggles between empty list and null
                 * would falsely register as dirty.
                 */
                fun normalizePlan(
                    plan: ImmutableList<PlanSetUiModel>?,
                ): ImmutableList<PlanSetUiModel> = plan ?: persistentListOf()
            }
        }

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
                historyCount = 0,
                originalSnapshot = null,
                isLoading = uuid != null,
                canPermanentlyDelete = false,
                adhocPlan = null,
                adhocPlanSummaryLabel = "",
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
             * Dispatched after returning from a `Screen.PlanEditor.Existing` save (DB
             * round-trip). The handler does a *partial* reload — only `(type, adhocPlan)`
             * are fetched and merged into State + `originalSnapshot` — so a pending
             * unsaved name/description/tag/image edit on the parent form is preserved.
             */
            data object PlanEditorExistingReturned : Common
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

            data object OnEditPlanClick : Click

            /**
             * Mutates the in-memory ad-hoc plan during exercise create-mode. The new
             * exercise has no UUID yet, so it cannot navigate to the full-screen
             * `Screen.PlanEditor` route (which keys off `last_adhoc_sets`); instead, the
             * inline body in `ExerciseEditScreen` emits the body action wrapped in this
             * store action, and the handler delegates to `PlanDraftReducer`.
             */
            @Suppress("MviActionNamingRule")
            data class OnAdhocPlanEditorAction(
                val action: PlanEditorBodyAction,
            ) : Click

            /**
             * The inline plan editor's WEIGHTED / WEIGHTLESS toggle. Creation owns the type on
             * this form because the rows whose shape it decides are drawn on this form.
             */
            data class OnTypeToggle(val value: ExerciseTypeUiModel) : Click

            /** Commit a switch that the weight-wipe confirm asked about. */
            data object OnTypeChangeConfirm : Click

            /** Dismiss the weight-wipe confirm, leaving the type as it was. */
            data object OnTypeChangeDismiss : Click

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

            /**
             * [editable] states whether THIS caller can honour a replace/remove request coming
             * back from the viewer. Read mode cannot — no Save, no dirty interception — so the
             * viewer draws no verbs for it.
             */
            data class OpenImageViewer(val model: String, val editable: Boolean) : Navigation

            data class OpenChart(val exerciseUuid: String) : Navigation

            /**
             * Open `Screen.PlanEditor.Existing` for this exercise's default plan. Used in
             * Edit mode when the exercise already has a persisted UUID. Returns to
             * ExerciseDetail; the graph picks up `planEditorSavedAttr` and dispatches
             * [Action.Common.PlanEditorExistingReturned] for a partial reload of
             * `(type, adhocPlan)`.
             */
            data class OpenPlanEditorExisting(val exerciseUuid: String) : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class Haptic(val type: HapticFeedbackType) : Event

        data class ShowArchiveSuccess(val uuid: String, val message: String) : Event

        data class ShowTagLimitReached(val message: String) : Event

        data class ShowPermanentDeleteSuccess(val message: String) : Event

        data class NavigateLaunchCamera(val tempUri: Uri) : Event

        data object NavigateLaunchGallery : Event

        data object NavigateRequestCameraPermission : Event

        data class NavigateOpenAppSettings(val packageName: String) : Event

        data class ShowImageError(val errorType: ImageErrorType) : Event
    }

    /**
     * Where the user is heading after confirming a discard. The form-level discard either
     * pops the screen (creation flow) or flips back to Read mode (edit flow); plan-editor
     * discard is now handled by the standalone PlanEditor route, not here.
     */
    @Stable
    enum class DiscardTarget { POP_SCREEN, FLIP_TO_READ }
}
