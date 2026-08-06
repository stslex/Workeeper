// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
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
        /**
         * Which edit session the current draft belongs to — bumped on every entry into
         * [Mode.Edit]. The set-removed toast carries it back with its undo action: a toast
         * outlives the draft it edited (5s, accessibility-stretched), so Save or Cancel can
         * end the draft — and Edit can start a new one — while «Отменить» is still on
         * screen. The undo handler no-ops unless the epoch still matches, so a stale undo
         * cannot put an unsaved row onto the Read screen or into a draft it never edited.
         */
        val draftEpoch: Int,
        /**
         * A save's write is in flight: the snapshot is already captured, so the draft may
         * not take an undo any more — a row restored now would reach the screen and miss
         * the database. Set when Save dispatches, cleared on every outcome (the success
         * flip to Read, or the failure that keeps the draft alive and re-arms its undos —
         * `DuplicateName` and a failed image commit both stay in Edit).
         */
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

            /**
             * One limit, two readers: `ClickHandler` enforces it and the ТЕГИ head's
             * `N из 10` counter displays it (§3.2 — the counter renders only where a limit
             * exists, which is this feature and not `feature/single-training`).
             */
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

            /**
             * Mutates the in-memory ad-hoc plan — the plan is edited **inline, in the form,
             * in both modes** (ED1): the body in `ExerciseEditScreen` emits the body action
             * wrapped in this store action, the handler delegates to `PlanDraftReducer`, and
             * nothing is persisted until Save.
             */
            @Suppress("MviActionNamingRule")
            data class OnAdhocPlanEditorAction(
                val action: PlanEditorBodyAction,
            ) : Click

            /**
             * The inline plan editor's WEIGHTED / WEIGHTLESS toggle. The form owns the type in
             * both modes, because the rows whose shape it decides are drawn on this form.
             */
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

            /**
             * [editable] states whether THIS caller can honour a replace/remove request coming
             * back from the viewer. Read mode cannot — no Save, no dirty interception — so the
             * viewer draws no verbs for it.
             */
            data class OpenImageViewer(val model: String, val editable: Boolean) : Navigation

            data class OpenChart(val exerciseUuid: String) : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class Haptic(val type: HapticFeedbackType) : Event

        data class ShowArchiveSuccess(val uuid: String, val message: String) : Event

        data class ShowTagLimitReached(val message: String) : Event

        /**
         * The DEFERRED permanent delete (ED11): nothing has been deleted when this fires.
         * [commit] is the delete itself, and the app-level snackbar host runs it when the
         * undo window closes — timeout or dismissal — and never on «Отменить»
         * (`resolveSnackbarOutcome`). It captures the interactor, not the Store, so it
         * outlives this screen's pop without outliving the process (D-OPEN-10: a process
         * death inside the window commits nothing and the row survives).
         */
        data class ShowPermanentDeleteUndo(
            val message: String,
            val commit: suspend () -> Unit,
        ) : Event

        /**
         * `− подход` in the editor is a DRAFT edit (§4's table): nothing is persisted, so
         * the undo restores the draft — [set] back at [index] — and there is no timer and
         * no deferred anything. Item-wise rather than a whole-draft snapshot, so queued
         * toasts compose: each undo restores exactly the row its toast named.
         */
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

    /**
     * Where the user is heading after confirming a discard. The form-level discard either
     * pops the screen (creation flow) or flips back to Read mode (edit flow); plan-editor
     * discard is now handled by the standalone PlanEditor route, not here.
     */
    @Stable
    enum class DiscardTarget { POP_SCREEN, FLIP_TO_READ }
}
