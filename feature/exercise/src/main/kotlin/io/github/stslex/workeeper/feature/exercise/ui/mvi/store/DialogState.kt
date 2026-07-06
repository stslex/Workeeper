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
internal sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    /**
     * Edit-mode discard confirmation. The `target` controls whether the post-confirm
     * action pops the screen (creation flow) or flips back to Read mode (edit flow).
     */
    @Stable
    data class DiscardConfirm(val target: ExerciseStore.DiscardTarget) : DialogState

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
