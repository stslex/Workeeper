package io.github.stslex.workeeper.core.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.Store.Action
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State
import io.github.stslex.workeeper.core.ui.mvi.store.StoreConsumer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

open class BaseHandlerStore<S : State, A : Action, E : Event>() :
    HandlerStore<S, A, E>,
    HandlerStoreEmitter<S, A, E> {

    private var _store: StoreConsumer<S, A, E>? = null
    private val store: StoreConsumer<S, A, E>
        get() = requireNotNull(_store)

    override val state: StateFlow<S>
        get() = store.state

    override val lastAction: A?
        get() = store.lastAction

    override val logger: Logger
        get() = store.logger

    override val scope: AppCoroutineScope
        get() = store.scope

    override fun sendEvent(event: E) {
        store.sendEvent(event)
    }

    override fun consume(action: A) {
        store.consume(action)
    }

    override fun updateState(update: (S) -> S) {
        store.updateState(update)
    }

    override suspend fun updateStateImmediate(update: (S) -> S) {
        store.updateStateImmediate(update)
    }

    override suspend fun updateStateImmediate(state: S) {
        store.updateStateImmediate(state)
    }

    override fun <T> launch(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        workDispatcher: CoroutineDispatcher,
        eachDispatcher: CoroutineDispatcher,
        action: suspend CoroutineScope.() -> T
    ): Job = store.launch(
        onError = onError,
        onSuccess = onSuccess,
        workDispatcher = workDispatcher,
        eachDispatcher = eachDispatcher,
        action = action
    )

    override fun <T> Flow<T>.launch(
        onError: suspend (Throwable) -> Unit,
        workDispatcher: CoroutineDispatcher,
        eachDispatcher: CoroutineDispatcher,
        each: suspend (T) -> Unit
    ): Job = store.launch(
        flow = this,
        onError = onError,
        workDispatcher = workDispatcher,
        eachDispatcher = eachDispatcher,
        each = each
    )

    override fun setStore(store: StoreConsumer<S, A, E>) {
        _store = store
    }

    override fun clearStore() {
        _store = null
    }
}