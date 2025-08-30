package io.github.stslex.workeeper.core.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.Store.Action

fun interface Handler<A : Action, TStore : HandlerStore<*, *, *>> {

    operator fun TStore.invoke(action: A)

}
