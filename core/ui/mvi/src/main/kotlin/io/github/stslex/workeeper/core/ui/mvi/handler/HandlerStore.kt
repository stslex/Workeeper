package io.github.stslex.workeeper.core.ui.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The Store surface handlers see: read state, dispatch actions and events, launch coroutines. */
interface HandlerStore<S : State, A : Store.Action, in E : Event> {

    val state: StateFlow<S>

    val lastAction: A?

    val logger: Logger

    fun sendEvent(event: E)

    fun consume(action: A)

    suspend fun consumeOnMain(action: A)

    fun updateState(update: (S) -> S)

    suspend fun updateStateImmediate(update: suspend (S) -> S)

    suspend fun updateStateImmediate(state: S)

    /** Launches a coroutine on the Store scope, routing throwables to [onError]. */
    fun <T> launch(
        onError: suspend (Throwable) -> Unit = {},
        onSuccess: suspend CoroutineScope.(T) -> Unit = {},
        workDispatcher: CoroutineDispatcher? = null,
        eachDispatcher: CoroutineDispatcher? = null,
        action: suspend CoroutineScope.() -> T,
    ): Job

    /** Launches a coroutine on the default dispatcher, routing throwables to [onError]. */
    fun <T> launchDefault(
        onError: suspend (Throwable) -> Unit = {},
        onSuccess: suspend CoroutineScope.(T) -> Unit = {},
        action: suspend CoroutineScope.() -> T,
    ): Job

    /** Collects this flow on the Store scope, routing throwables to [onError]. */
    fun <T> Flow<T>.launch(
        onError: suspend (cause: Throwable) -> Unit = {},
        workDispatcher: CoroutineDispatcher? = null,
        eachDispatcher: CoroutineDispatcher? = null,
        each: suspend (T) -> Unit,
    ): Job
}
