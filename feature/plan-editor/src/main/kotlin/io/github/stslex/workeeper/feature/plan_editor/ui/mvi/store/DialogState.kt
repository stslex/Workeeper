// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store

import androidx.compose.runtime.Stable

/**
 * Single source of truth for every modal dialog rendered on the Plan editor screen.
 * See Rule 4 of compose-state-discipline.md — dialog visibility lives in `State`,
 * never in a local Composable `var ... by remember`, and never as an `Event`.
 */
@Stable
sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    /**
     * Leave without saving? — the discard sheet.
     *
     * **The discard confirmation is a VARIANT of this field and must never be a `Boolean` beside
     * it.** A second modal channel makes "discard sheet and type-change sheet open at once" a
     * representable state, and the screen would draw both; one sealed field makes it
     * unrepresentable, which is what the `mvi-dialog-state` skill is for (§26).
     *
     * It carries no payload: the sheet's four strings live in the kit, one table for all three
     * editors (`core_ui_kit_discard_sheet_*`).
     */
    @Stable
    data object DiscardConfirm : DialogState

    /**
     * "Switching to weightless will clear weights on N plan rows" confirmation. The
     * pending target type lives in `State.pendingTypeChange` so the confirm handler
     * knows which value to commit; this variant only carries the pre-resolved dialog
     * payload (Rule 1 — strings hoisted out of `updateState` lambdas).
     */
    @Stable
    data class TypeChangeConfirm(
        val title: String,
        val body: String,
        val impactSummary: String,
        val confirmLabel: String,
    ) : DialogState
}
