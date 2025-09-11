package io.github.stslex.workeeper.core.ui.mvi

import io.github.stslex.workeeper.core.ui.mvi.Store.Action
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore

fun <S : State, A : Action, E : Event, HStore : HandlerStore<S, A, E>> Handler<A, HStore>.invoke(
    store: HStore,
    action: A
) {
    with(store) {
        this.invoke(action)
    }
}

inline fun <S : State, A : Action, E : Event, HStore : HandlerStore<S, A, E>> handler(
    crossinline block: HandlerStore<S, A, E>.(action: A) -> Unit
) = Handler<A, HStore> { block(it) }
