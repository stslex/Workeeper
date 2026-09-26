package io.github.stslex.workeeper.wear.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.wear.di.WearHandlerStore
import io.github.stslex.workeeper.wear.di.WearScope
import io.github.stslex.workeeper.wear.domain.WearInteractor
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Action
import io.github.stslex.workeeper.wear.runtime.ControllerAction

@SingleIn(WearScope::class)
internal class InputHandler @Inject constructor(
    private val store: WearHandlerStore,
    private val interactor: WearInteractor,
) : Handler<Action.Input> {
    override fun invoke(action: Action.Input) {
        val state = store.state.value
        val presentation = state.presentation
        if (presentation.ambient.isAmbient || presentation.preview || !presentation.model.controlsEnabled) return
        when (action) {
            is Action.Input.Draft -> if (state.editor == action.field) {
                interactor.perform(ControllerAction.AdjustDraft(action.field, action.steps))
            }
        }
    }
}
