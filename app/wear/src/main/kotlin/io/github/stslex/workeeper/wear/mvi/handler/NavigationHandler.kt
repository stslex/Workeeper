package io.github.stslex.workeeper.wear.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.wear.di.WearHandlerStore
import io.github.stslex.workeeper.wear.di.WearScope
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Action

@SingleIn(WearScope::class)
internal class NavigationHandler @Inject constructor(
    private val store: WearHandlerStore,
) : Handler<Action.Navigation> {
    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.CloseEditor -> store.updateState { it.copy(editor = null) }
        }
    }
}
