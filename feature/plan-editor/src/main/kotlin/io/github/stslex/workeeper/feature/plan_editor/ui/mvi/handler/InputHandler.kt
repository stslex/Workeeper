// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.domain.PlanDraftReducer
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import javax.inject.Inject

@ViewModelScoped
internal class InputHandler @Inject constructor(
    store: PlanEditorHandlerStore,
) : Handler<Action.Input>, PlanEditorHandlerStore by store {

    override fun invoke(action: Action.Input) {
        val bodyAction = when (action) {
            is Action.Input.OnSetWeightChange ->
                PlanEditorBodyAction.OnSetWeightChange(action.index, action.value)

            is Action.Input.OnSetRepsChange ->
                PlanEditorBodyAction.OnSetRepsChange(action.index, action.value)
        }
        updateState { current ->
            current.copy(
                draft = PlanDraftReducer.reduce(
                    draft = current.draft,
                    action = bodyAction,
                    isWeighted = current.isWeighted,
                ),
            )
        }
    }
}
