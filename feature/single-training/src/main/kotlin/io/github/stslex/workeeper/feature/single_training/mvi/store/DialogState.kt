// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.store

import androidx.compose.runtime.Stable

@Stable
sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    /** The read screen's topbar `⋮` sheet (ED10): «В архив» · «Удалить навсегда». */
    @Stable
    data object DetailMenu : DialogState

    /** ED7: the tag picker's sheet; selection applies live, and there is no tag limit (§3.2). */
    @Stable
    data object TagPicker : DialogState

    /** Edit-mode discard confirmation; the screen resolves its strings from the kit. */
    @Stable
    data object DiscardConfirm : DialogState

    /** Read-mode "permanently delete training" confirm; the handler pre-resolves its strings. */
    @Stable
    data class PermanentDeleteConfirm(
        val title: String,
        val body: String,
        val impactSummary: String,
        val confirmLabel: String,
    ) : DialogState

    /** Start tapped while a different training has an in-progress session. */
    @Stable
    data class ActiveSessionConflict(
        val sessionUuid: String,
        val activeSessionName: String,
        val progressLabel: String,
    ) : DialogState
}
