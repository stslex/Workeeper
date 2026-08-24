// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store

import androidx.compose.runtime.Stable

/**
 * Every modal rendered on the Plan editor screen, on one sealed channel.
 * See Rule 4 of compose-state-discipline.md — dialog visibility lives in `State`.
 */
@Stable
sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    /**
     * "Leave without saving?" — the discard sheet. Carries no payload: its strings live in the
     * kit, one table for all three editors (`core_ui_kit_discard_sheet_*`).
     */
    @Stable
    data object DiscardConfirm : DialogState

    /**
     * Confirms clearing weights when switching to weightless. The target type waits in
     * `State.pendingTypeChange`; this carries only the pre-resolved strings (Rule 1).
     */
    @Stable
    data class TypeChangeConfirm(
        val title: String,
        val body: String,
        val impactSummary: String,
        val confirmLabel: String,
    ) : DialogState
}
