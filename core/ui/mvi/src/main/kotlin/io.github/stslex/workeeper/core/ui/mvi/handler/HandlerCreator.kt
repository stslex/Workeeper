package io.github.stslex.workeeper.core.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.Store.Action
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State

fun interface HandlerCreator<S : State, A : Action, E : Event, HStore : HandlerStore<S, A, E>> {

    operator fun invoke(action: A): Handler<*, HStore>
}