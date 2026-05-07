// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import androidx.compose.runtime.Stable

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
     * The body string is pre-formatted by the handler — it embeds the exercise name and
     * the comma-joined list of active trainings.
     */
    @Stable
    data class ArchiveBlocked(val body: String) : DialogState

    @Stable
    data class PermanentDeleteConfirm(
        val title: String,
        val body: String,
        val impactSummary: String,
        val confirmLabel: String,
    ) : DialogState

    /**
     * "Switching to weightless will clear weights on N plan rows" confirmation. The
     * pending target type lives in `State.pendingTypeChange` so the confirm handler
     * knows which value to commit; this variant only carries the dialog payload.
     */
    @Stable
    data class TypeChangeConfirm(
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
