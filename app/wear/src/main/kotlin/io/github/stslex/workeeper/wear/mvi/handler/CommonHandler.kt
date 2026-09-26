package io.github.stslex.workeeper.wear.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.di.WearHandlerStore
import io.github.stslex.workeeper.wear.di.WearScope
import io.github.stslex.workeeper.wear.domain.WearInteractor
import io.github.stslex.workeeper.wear.mvi.mapper.WearPresentationMapper
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Action

@SingleIn(WearScope::class)
internal class CommonHandler @Inject constructor(
    private val store: WearHandlerStore,
    private val interactor: WearInteractor,
    private val mapper: WearPresentationMapper,
    private val dispatchers: StoreDispatchers,
) : Handler<Action.Common> {
    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> {
                refresh()
                with(store) {
                    interactor.snapshots.launch(
                        workDispatcher = dispatchers.mainImmediateDispatcher,
                        eachDispatcher = dispatchers.mainImmediateDispatcher,
                    ) { consume(Action.Common.RuntimeChanged) }
                }
            }
            Action.Common.RuntimeChanged -> refresh()
            is Action.Common.PlatformChanged -> {
                if (store.state.value.platform == action.value) return
                store.updateState { it.copy(platform = action.value) }
                refresh()
            }
            is Action.Common.PresentationChanged -> {
                val presentation = action.value
                val editor = store.state.value.editor?.takeIf { field ->
                    presentation.ambient.isAmbient || presentation.model.controlsEnabled &&
                        (field == NumericField.REPS || presentation.model.weighted)
                }
                store.updateState { it.copy(presentation = presentation, editor = editor) }
            }
        }
    }

    private fun refresh() {
        val presentation = mapper.map(interactor.current(), store.state.value.platform)
        store.consume(Action.Common.PresentationChanged(presentation))
    }
}
