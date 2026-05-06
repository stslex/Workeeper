// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@ViewModelScoped
internal class InputHandler @Inject constructor(
    store: PlanEditorHandlerStore,
) : Handler<Action.Input>, PlanEditorHandlerStore by store {

    override fun invoke(action: Action.Input) {
        when (action) {
            is Action.Input.OnSetWeightChange -> updateRow(action.index) {
                it.copy(weight = action.value)
            }

            is Action.Input.OnSetRepsChange -> updateRow(action.index) {
                it.copy(reps = action.value.coerceAtLeast(0))
            }
        }
    }

    private inline fun updateRow(
        index: Int,
        crossinline transform: (
            PlanSetUiModel,
        ) -> PlanSetUiModel,
    ) {
        updateState { current ->
            if (index !in current.draft.indices) return@updateState current
            current.copy(
                draft = current.draft.toMutableList()
                    .apply { this[index] = transform(this[index]) }
                    .toImmutableList(),
            )
        }
    }
}
