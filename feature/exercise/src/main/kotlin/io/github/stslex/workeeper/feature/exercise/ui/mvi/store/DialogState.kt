// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.kit.components.dialog.BlockedArchiveItem

/**
 * Single source of truth for every dialog rendered on the Exercise screen. Folds the
 * legacy `sourceDialogVisible`, `permissionDeniedDialogVisible`, `pendingConflict` State
 * fields plus the `Event.Show*` dialog events into one sealed interface so at most one
 * dialog is open at a time — enforced by the type system, not a comment. See the Rule 4
 * "Dialogs and bottom sheets are State, not Events" section of compose-state-discipline.md.
 */
@Stable
sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    /**
     * Edit-mode discard confirmation. The `target` controls whether the post-confirm
     * action pops the screen (creation flow) or flips back to Read mode (edit flow).
     */
    @Stable
    data class DiscardConfirm(val target: ExerciseStore.DiscardTarget) : DialogState

    /**
     * "Switching to weightless will clear the weights you have typed" confirmation, raised by the
     * inline plan editor's type toggle. The pending target lives in `State.pendingTypeChange` so
     * the confirm handler knows which value to commit; this variant carries only the pre-resolved
     * strings (Rule 1 — no `stringResource` inside an `updateState` lambda).
     *
     * **The wipe here is local and that is not an oversight.** A record being created has no row
     * on disk and nothing else references it, so there is no cross-plan cascade to run — the only
     * weights in existence are the ones in this draft.
     */
    @Stable
    data class TypeChangeConfirm(
        val title: String,
        val body: String,
        val impactSummary: String,
        val confirmLabel: String,
    ) : DialogState

    /**
     * Surfaced when archiving an exercise that is still referenced by an active training.
     * Rendered by the shared `AppBlockedArchiveDialog` (same component the all-exercises
     * bulk-archive path uses) so the two surfaces can't drift. [item] carries the exercise
     * name and the pre-formatted "used in …" trainings label built by the handler.
     */
    @Stable
    data class ArchiveBlocked(val item: BlockedArchiveItem) : DialogState

    @Stable
    data class PermanentDeleteConfirm(
        val title: String,
        val body: String,
        val impactSummary: String,
        val confirmLabel: String,
    ) : DialogState

    /** Camera-vs-Gallery picker shown after the user taps "Add image". */
    @Stable
    data object ImageSourcePicker : DialogState

    /** What counts as a record — opened from the history record row's PR tag. */
    @Stable
    data object PrExplainer : DialogState

    /** Camera permission denied → "Open Settings or Cancel" prompt. */
    @Stable
    data object PermissionDenied : DialogState

    /**
     * Track Now conflict — surfaced when an active session exists for a different
     * exercise. Carries the active session's UUID so Resume / Delete-and-start can
     * act without consulting another State field.
     */
    @Stable
    data class ActiveSessionConflict(
        val sessionUuid: String,
        val activeSessionName: String,
        val progressLabel: String,
    ) : DialogState
}

/**
 * The topbar `⋮` overflow, Store-homed like every other modal on this screen (Rule 4 of
 * compose-state-discipline). The v2.4 `DropdownMenu` rendered from an anchored composable
 * with no state backing; the v3 sheet survives the same way the dialogs do. Kept separate
 * from [DialogState] deliberately — the past-session rebuild established the two-field
 * shape (`dialogState` + `bottomSheetState`), and a menu item that opens a dialog closes
 * the sheet in the same state transition.
 */
@Stable
sealed interface BottomSheetState {

    @Stable
    data object Hidden : BottomSheetState

    /** `⋮` → Изменить · В архив · Удалить навсегда (the last only when deletable). */
    @Stable
    data object DetailMenu : BottomSheetState
}
