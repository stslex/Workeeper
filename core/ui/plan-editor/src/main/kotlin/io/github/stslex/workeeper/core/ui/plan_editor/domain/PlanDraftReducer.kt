// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.domain

import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Pure transformer that applies a [PlanEditorBodyAction] to a plan draft. Shared between
 * the full-screen plan editor (`feature/plan-editor`) and the inline plan editor surfaced
 * by the exercise create-flow (`feature/exercise`) so the two paths cannot drift in their
 * set-list semantics.
 *
 * No coroutines, IO, or resource access. Lifecycle actions (`OnSave`, `OnDismiss`) and
 * actions never emitted by the body in mutation contexts return the draft unchanged.
 */
object PlanDraftReducer {

    private const val DEFAULT_NEW_REPS = 5

    @Suppress("UnusedParameter")
    fun reduce(
        draft: ImmutableList<PlanSetUiModel>,
        action: PlanEditorBodyAction,
        isWeighted: Boolean,
    ): ImmutableList<PlanSetUiModel> = when (action) {
        PlanEditorBodyAction.OnAddSet -> draft.appendDefault()
        is PlanEditorBodyAction.OnSetRemove -> draft.removeAtSafe(action.index)
        is PlanEditorBodyAction.OnSetTypeChange -> draft.updateRowSafe(action.index) {
            it.copy(type = action.value)
        }

        is PlanEditorBodyAction.OnSetWeightChange -> draft.updateRowSafe(action.index) {
            it.copy(weight = action.value)
        }

        is PlanEditorBodyAction.OnSetRepsChange -> draft.updateRowSafe(action.index) {
            it.copy(reps = action.value.coerceAtLeast(0))
        }

        PlanEditorBodyAction.OnDismiss,
        PlanEditorBodyAction.OnSave,
        -> draft
    }

    private fun ImmutableList<PlanSetUiModel>.appendDefault(): ImmutableList<PlanSetUiModel> {
        val previous = lastOrNull()
        // New set always cycles back to WORK regardless of previous type — workout pattern:
        // warmups precede work sets, so the next add is a work set by default.
        val nextSet = previous?.copy(type = SetTypeUiModel.WORK) ?: PlanSetUiModel(
            weight = null,
            reps = DEFAULT_NEW_REPS,
            type = SetTypeUiModel.WORK,
        )
        return (this + nextSet).toImmutableList()
    }

    private fun ImmutableList<PlanSetUiModel>.removeAtSafe(
        index: Int,
    ): ImmutableList<PlanSetUiModel> {
        if (index !in indices) return this
        return toMutableList().also { it.removeAt(index) }.toImmutableList()
    }

    private inline fun ImmutableList<PlanSetUiModel>.updateRowSafe(
        index: Int,
        transform: (PlanSetUiModel) -> PlanSetUiModel,
    ): ImmutableList<PlanSetUiModel> {
        if (index !in indices) return this
        return toMutableList().apply { this[index] = transform(this[index]) }.toImmutableList()
    }
}
