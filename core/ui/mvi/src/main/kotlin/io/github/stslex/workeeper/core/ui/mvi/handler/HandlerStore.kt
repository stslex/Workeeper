package io.github.stslex.workeeper.core.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.mvi.Store.Event
import io.github.stslex.workeeper.core.ui.mvi.Store.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 *  A generic interface for managing state, actions, and events within a component or module.  It provides a central point for handling state updates, dispatching actions and events, logging, and launching coroutines.
 *
 *  @param S The type of the state managed by the store. Must implement [State].
 *  @param A The type of actions that can be dispatched to the store. Must implement [Store.Action].
 *  @param E The type of events that the store can emit. Must implement [Event].
 */
interface HandlerStore<S : State, A : Store.Action, E : Event> {

    val state: StateFlow<S>

    val lastAction: A?

    val logger: Logger

    val appDispatcher: AppDispatcher

    val scope: AppCoroutineScope

    fun sendEvent(event: E)

    fun consume(action: A)

    fun updateState(update: (S) -> S)

    suspend fun updateStateImmediate(update: (S) -> S)

    /**
     * Launches a coroutine and catches exceptions. The coroutine is launched on the default dispatcher of the AppDispatcher.
     * @param onError - error handler
     * @param onSuccess - success handler
     * @param action - action to be executed
     * @return Job
     * @see Job
     * @see AppDispatcher
     * */
    fun <T> launch(
        onError: suspend (Throwable) -> Unit = {},
        onSuccess: suspend CoroutineScope.(T) -> Unit = {},
        workDispatcher: CoroutineDispatcher = appDispatcher.default,
        eachDispatcher: CoroutineDispatcher = appDispatcher.default,
        action: suspend CoroutineScope.() -> T,
    ): Job

    /**
     * Launches a flow and collects it in the screenModelScope. The flow is collected on the default dispatcher. of the AppDispatcher.
     * @param onError - error handler
     * @param each - action for each element of the flow
     * @return Job
     * @see Flow
     * @see Job
     * @see AppDispatcher
     * */
    fun <T> Flow<T>.launch(
        onError: suspend (cause: Throwable) -> Unit = {},
        workDispatcher: CoroutineDispatcher = appDispatcher.default,
        eachDispatcher: CoroutineDispatcher = appDispatcher.default,
        each: suspend (T) -> Unit
    ): Job
}