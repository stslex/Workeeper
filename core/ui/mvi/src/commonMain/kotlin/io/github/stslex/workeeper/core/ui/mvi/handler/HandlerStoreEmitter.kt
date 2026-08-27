package io.github.stslex.workeeper.core.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.mvi.store.StoreConsumer

interface HandlerStoreEmitter<S : Store.State, A : Store.Action, E : Store.Event> {

    fun setStore(store: StoreConsumer<S, A, E>)

    fun clearStore()
}
