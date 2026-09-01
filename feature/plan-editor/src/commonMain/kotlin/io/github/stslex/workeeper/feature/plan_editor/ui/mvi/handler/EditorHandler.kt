package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorScope
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action

@SingleIn(PlanEditorScope::class)
internal class EditorHandler @Inject constructor(
    store: PlanEditorHandlerStore,
) : Handler<Action.EditorAction>, PlanEditorHandlerStore by store {

    override fun invoke(action: Action.EditorAction) {
        consume(action.action.toStoreAction())
    }

    private fun PlanEditorBodyAction.toStoreAction(): Action = when (this) {
        PlanEditorBodyAction.OnAddSet -> Action.Click.OnAddSet
        PlanEditorBodyAction.OnDismiss -> Action.Click.OnBackClick
        PlanEditorBodyAction.OnSave -> Action.Click.OnSave
        is PlanEditorBodyAction.OnSetRemove -> Action.Click.OnSetRemove(index)
        is PlanEditorBodyAction.OnSetRepsChange -> Action.Input.OnSetRepsChange(index, value)
        is PlanEditorBodyAction.OnSetTypeChange -> Action.Click.OnSetTypeChange(index, value)
        is PlanEditorBodyAction.OnSetWeightChange -> Action.Input.OnSetWeightChange(index, value)
    }
}
