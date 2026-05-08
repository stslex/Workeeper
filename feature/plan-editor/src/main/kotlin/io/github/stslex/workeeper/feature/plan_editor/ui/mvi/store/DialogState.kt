// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store

import androidx.compose.runtime.Stable

/**
 * Single source of truth for every modal dialog rendered on the Plan editor screen.
 * See Rule 4 of compose-state-discipline.md — dialog visibility lives in `State`,
 * never in a local Composable `var ... by remember`, and never as an `Event`.
 */
@Stable
internal sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

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
