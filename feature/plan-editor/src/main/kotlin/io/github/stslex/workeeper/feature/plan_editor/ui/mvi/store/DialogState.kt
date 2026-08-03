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
     * **This was a SECOND, INDEPENDENT CHANNEL until the editors stage:** a
     * `confirmDiscardOpen: Boolean` beside this very field, so the screen could have the discard
     * confirmation and the type-change confirmation open AT ONCE. §26 collapses the two channels
     * into this one sealed state, which is exactly what the `mvi-dialog-state` skill exists to
     * make unrepresentable — a variant cannot coexist with another variant, where two fields
     * always can.
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
