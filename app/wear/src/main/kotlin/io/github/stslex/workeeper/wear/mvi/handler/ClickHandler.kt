package io.github.stslex.workeeper.wear.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.di.WearHandlerStore
import io.github.stslex.workeeper.wear.di.WearScope
import io.github.stslex.workeeper.wear.domain.WearInteractor
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Action
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Event
import io.github.stslex.workeeper.wear.runtime.ControllerAction

@SingleIn(WearScope::class)
internal class ClickHandler @Inject constructor(
    private val store: WearHandlerStore,
    private val interactor: WearInteractor,
) : Handler<Action.Click> {
    override fun invoke(action: Action.Click) {
        val presentation = store.state.value.presentation
        if (presentation.ambient.isAmbient) return
        when (action) {
            is Action.Click.Edit -> if (presentation.model.controlsEnabled &&
                (action.field == NumericField.REPS || presentation.model.weighted)
            ) {
                store.updateState { it.copy(editor = action.field) }
            }
            Action.Click.Complete -> if (!presentation.preview && presentation.model.completeEnabled) {
                interactor.perform(ControllerAction.CompleteSet)
            }
            Action.Click.Retry -> if (!presentation.preview && presentation.model.retryEnabled) {
                interactor.perform(ControllerAction.Retry)
            }
            Action.Click.EnableNotifications -> store.sendEvent(Event.EnableNotificationsRequested)
        }
    }
}
