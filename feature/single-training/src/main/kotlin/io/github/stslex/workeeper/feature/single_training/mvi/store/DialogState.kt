// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.store

import androidx.compose.runtime.Stable

@Stable
sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    /**
     * The read screen's topbar `⋮` sheet (ED10): «В архив» · «Удалить навсегда». One sealed
     * field carries it with the confirms rather than a second `bottomSheetState`, so a menu
     * row that opens a confirm replaces this variant in one write and the double-open state
     * is unrepresentable (Rule 4 of compose-state-discipline).
     */
    @Stable
    data object DetailMenu : DialogState

    /**
     * Edit-mode discard confirmation. Strings are pulled from the screen via
     * `stringResource(R.string.*)` because the kit's `AppDialog` accepts the values
     * directly — no per-instance payload is needed beyond the variant tag.
     */
    @Stable
    data object DiscardConfirm : DialogState

    /**
     * Read-mode "permanently delete training" confirm. All display strings are
     * pre-resolved by the handler so the screen never reaches into `ResourceWrapper`.
     */
    @Stable
    data class PermanentDeleteConfirm(
        val title: String,
        val body: String,
        val impactSummary: String,
        val confirmLabel: String,
    ) : DialogState

    /**
     * Active-session conflict surfaced when the user taps Start while a different
     * training has an in-progress session. Carries the session UUID so the
     * Resume / Delete-and-start branches know which session to act on.
     */
    @Stable
    data class ActiveSessionConflict(
        val sessionUuid: String,
        val activeSessionName: String,
        val progressLabel: String,
    ) : DialogState
}
